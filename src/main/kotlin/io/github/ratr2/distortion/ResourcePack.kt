package io.github.ratr2.distortion

import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress
import java.net.URI
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

    private var built = emptyMap<PackEra, Built>()
    private var http: HttpServer? = null
    var baseUrl: String = "http://localhost"
        private set

    private val loaded = HashMap<UUID, PackEra>()
    var onLoaded: (Player) -> Unit = {}

    init {
        start()
    }

    /** Rebuilds the packs, restarts the HTTP server from the current config and resends the pack to everyone online. */
    fun restart() {
        stop()
        start()
        plugin.server.onlinePlayers.forEach(::send)
    }

    fun stop() {
        http?.stop(0)
        http = null
    }

    private fun start() {
        built = PackEra.entries.associateWith { Built(it, buildZip(it)) }

        val config = plugin.config
        val port = config.getInt("resource-pack.port", 8085)
        val host = config.getString("resource-pack.public-host").orEmpty()
            .ifBlank { plugin.server.ip }
            .ifBlank { "localhost" }

        val byFile = built.values.associateBy { it.fileName }
        http = HttpServer.create(InetSocketAddress(config.getString("resource-pack.bind", "0.0.0.0"), port), 0).apply {
            createContext("/") { exchange ->
                val pack = byFile[exchange.requestURI.path.removePrefix("/")]
                if (pack == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.responseHeaders.add("Content-Type", "application/zip")
                    exchange.sendResponseHeaders(200, pack.zip.size.toLong())
                    exchange.responseBody.use { it.write(pack.zip) }
                }
                exchange.close()
            }
            start()
        }
        baseUrl = "http://$host:$port"
        plugin.logger.info("Serving ${built.size} resource packs (${built.keys.joinToString { it.id }}) at $baseUrl")
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

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) = send(event.player)

    private fun send(player: Player) {
        val pack = eraOf(player)?.let(built::get)
        if (pack == null) {
            notifyUnsupported(player)
            return
        }
        val config = plugin.config
        player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(pack.id, URI("$baseUrl/${pack.fileName}"), pack.sha1))
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
                loaded.remove(player.uniqueId)
            else -> {}
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        loaded.remove(event.player.uniqueId)
    }
}
