package io.github.ratr2.distortion

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zips the packs embedded under `packs/<era>/` in the plugin jar, hosts them over HTTP and sends each player the one
 * that matches the client version they connected with.
 */
class ResourcePack(private val plugin: Distortion, private val versions: ClientVersions) : Listener {

    private class Built(val era: PackEra, val zip: ByteArray) {
        val id: UUID = UUID.nameUUIDFromBytes("distortion-pack-${era.id}".toByteArray())
        val sha1: String = MessageDigest.getInstance("SHA-1").digest(zip).joinToString("") { "%02x".format(it) }
        val fileName get() = "distortion-${era.id}-$sha1.zip"
    }

    @Volatile
    private var built = emptyMap<PackEra, Built>()

    @Volatile
    private var byFile = emptyMap<String, Built>()

    /** The pack server, shared with other plugins that use the same scheme (Repack): whichever starts first hosts it. */
    private val host = SharedHost(plugin, "distortion", 8085) { name -> byFile[name]?.zip }

    val baseUrl: String get() = host.baseUrl

    /** Says who serves the packs and where, for logs and commands. */
    val serverDescription: String get() = host.description

    private val loaded = HashMap<UUID, PackEra>()
    var onLoaded: (Player) -> Unit = {}
    var onLost: (Player, Boolean) -> Unit = { _, _ -> }

    init {
        start()
        host.offer(::joinOffer)
    }

    /** What the host sends a joining player for Distortion, together with the other plugins' packs in one request. */
    private fun joinOffer(playerId: UUID): Map<String, String>? {
        val player = plugin.server.getPlayer(playerId) ?: return null
        val pack = eraOf(player)?.let(built::get) ?: return null
        val config = plugin.config
        return mapOf(
            "id" to pack.id.toString(),
            "url" to host.urlOf(pack.fileName).toString(),
            "sha1" to pack.sha1,
            "required" to config.getBoolean("resource-pack.required", true).toString(),
            "prompt" to config.getString("resource-pack.prompt").orEmpty(),
        )
    }

    /**
     * Rebuilds the packs, applies a changed port or sharing setting without a restart, and resends the pack to everyone online.
     * Returns an error message when the pack server could not be moved, otherwise null.
     */
    fun restart(): String? {
        build()
        val error = host.restart()
        plugin.server.onlinePlayers.forEach(::send)
        return error
    }

    fun stop() {
        host.stop()
    }

    private fun start() {
        build()
        host.start()
        plugin.logger.info("Resource packs ${built.keys.joinToString { it.id }}: pack server ${host.description}")
    }

    private fun build() {
        built = PackEra.entries.associateWith { Built(it, buildZip(it)) }
        byFile = built.values.associateBy { it.fileName }
    }

    fun hasLoaded(player: Player) = player.uniqueId in loaded

    /** Whether the pack this player loaded is driven by the server post effect API (26.3 and later clients). */
    fun usesPostEffects(player: Player) = loaded[player.uniqueId]?.usesPostEffects == true

    /** Whether this player's client version takes the post effect API at all, pack loaded or not. */
    fun supportsPostEffects(player: Player) = eraOf(player)?.usesPostEffects == true

    fun loadedPlayers(): List<Player> = loaded.keys.mapNotNull(plugin.server::getPlayer)

    private fun eraOf(player: Player) = PackEra.forProtocol(versions.protocolOf(player))

    private fun buildZip(era: PackEra): ByteArray {
        val prefix = "packs/${era.id}/"
        val out = ByteArrayOutputStream()
        var count = 0
        JarFile(plugin.jar()).use { jar ->
            ZipOutputStream(out).use { zip ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith(prefix) && !it.isDirectory }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        zip.putNextEntry(ZipEntry(entry.name.removePrefix(prefix)).apply { time = 0 })
                        jar.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
            }
        }
        check(count > 0) { "No pack files under $prefix in the plugin jar" }
        return out.toByteArray()
    }

    /** Tells the shared host a player joined: it sends this pack and the other plugins' together, after the join delay. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (eraOf(player)?.let(built::get) == null) {
            notifyUnsupported(player)
            return
        }
        host.joined(player.uniqueId)
    }

    /** Sends the pack for the player's client version when it is their turn, spaced out from packs other plugins send them. */
    private fun send(player: Player) {
        val pack = eraOf(player)?.let(built::get)
        if (pack == null) {
            notifyUnsupported(player)
            return
        }
        host.enqueue(player.uniqueId) { if (player.isOnline) sendNow(player, pack) }
    }

    private fun sendNow(player: Player, pack: Built) {
        val config = plugin.config
        player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(pack.id, host.urlOf(pack.fileName), pack.sha1))
                .required(config.getBoolean("resource-pack.required", true))
                .prompt(Component.text(config.getString("resource-pack.prompt").orEmpty()))
                .build()
        )
    }

    private fun notifyUnsupported(player: Player) {
        val protocol = versions.protocolOf(player)
        val reason = if (protocol < PackEra.oldest.minProtocol) {
            "it needs ${PackEra.oldest.label.substringBefore(" to ")} or newer"
        } else {
            "it is newer than this plugin supports (up to ${PackEra.newest.label})"
        }
        notice(player, "Distortion effects are not available on your client version: $reason.")
    }

    private fun notice(player: Player, message: String) {
        if (plugin.config.getBoolean("client-notices", true)) player.sendMessage(Component.text(message, NamedTextColor.YELLOW))
    }

    @EventHandler
    fun onStatus(event: PlayerResourcePackStatusEvent) {
        val pack = built.values.firstOrNull { it.id == event.getID() } ?: return
        val player = event.player
        when (event.status) {
            Status.SUCCESSFULLY_LOADED -> if (loaded.put(player.uniqueId, pack.era) == null) {
                onLoaded(player)
                if (!pack.era.usesPostEffects) {
                    notice(player, "On your client version, distortion effects only show with Fabulous! graphics (Video Settings > Graphics).")
                }
            }
            Status.DECLINED, Status.FAILED_DOWNLOAD, Status.FAILED_RELOAD, Status.INVALID_URL, Status.DISCARDED ->
                loaded.remove(player.uniqueId)?.let { onLost(player, it.usesPostEffects) }
            else -> {}
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        loaded.remove(event.player.uniqueId)
    }
}
