package io.github.ratr2.distortion

import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
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

/** Zips the pack embedded under `pack/` in the plugin jar, hosts it over HTTP and sends it to every player. */
class ResourcePack(private val plugin: Distortion) : Listener {

    private val id: UUID = UUID.nameUUIDFromBytes("distortion-pack".toByteArray())
    private var zip: ByteArray = ByteArray(0)
    private var sha1: String = ""
    private var http: HttpServer? = null
    var url: URI = URI("http://localhost")
        private set

    private val loaded = HashSet<UUID>()
    var onLoaded: (Player) -> Unit = {}

    init {
        start()
    }

    /** Rebuilds the pack, restarts the HTTP server from the current config and resends the pack to everyone online. */
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
        zip = buildZip()
        sha1 = MessageDigest.getInstance("SHA-1").digest(zip).joinToString("") { "%02x".format(it) }

        val config = plugin.config
        val port = config.getInt("resource-pack.port", 8085)
        val host = config.getString("resource-pack.public-host").orEmpty()
            .ifBlank { plugin.server.ip }
            .ifBlank { "localhost" }

        val pack = zip
        http = HttpServer.create(InetSocketAddress(config.getString("resource-pack.bind", "0.0.0.0"), port), 0).apply {
            createContext("/") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/zip")
                exchange.sendResponseHeaders(200, pack.size.toLong())
                exchange.responseBody.use { it.write(pack) }
            }
            start()
        }
        url = URI("http://$host:$port/distortion-$sha1.zip")
        plugin.logger.info("Serving resource pack at $url")
    }

    fun hasLoaded(player: Player) = player.uniqueId in loaded

    fun loadedPlayers(): List<Player> = loaded.mapNotNull(plugin.server::getPlayer)

    private fun buildZip(): ByteArray {
        val out = ByteArrayOutputStream()
        JarFile(plugin.jar()).use { jar ->
            ZipOutputStream(out).use { zip ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("pack/") && !it.isDirectory }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        zip.putNextEntry(ZipEntry(entry.name.removePrefix("pack/")).apply { time = 0 })
                        jar.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
        }
        return out.toByteArray()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) = send(event.player)

    private fun send(player: Player) {
        val config = plugin.config
        player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(id, url, sha1))
                .required(config.getBoolean("resource-pack.required", true))
                .prompt(Component.text(config.getString("resource-pack.prompt").orEmpty()))
                .build()
        )
    }

    @EventHandler
    fun onStatus(event: PlayerResourcePackStatusEvent) {
        if (event.getID() != id) return
        val player = event.player
        when (event.status) {
            Status.SUCCESSFULLY_LOADED -> if (loaded.add(player.uniqueId)) onLoaded(player)
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
