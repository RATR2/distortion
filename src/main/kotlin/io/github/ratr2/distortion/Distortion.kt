package io.github.ratr2.distortion

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Distortion : JavaPlugin() {

    private lateinit var pack: ResourcePack
    private lateinit var ripples: RippleManager

    override fun onEnable() {
        saveDefaultConfig()
        pack = ResourcePack(this)
        ripples = RippleManager(this, pack)
        pack.onLoaded = ripples::onPackLoaded

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
