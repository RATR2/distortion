package io.github.ratr2.distortion

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Distortion : JavaPlugin() {

    private lateinit var pack: ResourcePack
    private lateinit var ripples: RippleManager

    override fun onEnable() {
        saveDefaultConfig()
        val versions = ClientVersions(this)
        if (versions.hasVia) logger.info("ViaVersion found: each player gets the pack for the client version they connect with.")
        if (versions.hasViaBackwards) {
            logger.warning(
                "ViaBackwards found: clients older than ${PackEra.oldest.label.substringBefore(" to ")} will not be able to see distortion effects."
            )
        }
        pack = ResourcePack(this, versions)
        ripples = RippleManager(this, pack)
        pack.onLoaded = ripples::onPackLoaded
        pack.onLost = ripples::onPackLost

        server.pluginManager.registerEvents(pack, this)
        server.pluginManager.registerEvents(ripples, this)
        val command = DistortionCommand(this, ripples, pack)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(command.node(), "Spawn and debug distortion ripples")
        }
        DistortionAPI.manager = ripples
    }

    override fun onDisable() {
        DistortionAPI.manager = null
        if (::ripples.isInitialized) ripples.clear()
        if (::pack.isInitialized) pack.stop()
    }

    internal fun jar(): File = file
}
