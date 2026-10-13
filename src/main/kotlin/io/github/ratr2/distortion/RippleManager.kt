package io.github.ratr2.distortion

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Spawns the marker text displays the resource pack's text shader turns into ripples.
 * Must match assets/minecraft/shaders/core/text.vsh / text.fsh in the pack.
 */
class RippleManager(private val plugin: Distortion, private val pack: ResourcePack) : Listener {

    companion object {
        const val SLOTS = 16
        private const val GLYPH = "󰀐"
        private const val OPACITY: Byte = 123
        private const val SCALE_UNIT = 0.01f
        private const val POST_EFFECT = "distortion:ripple"
    }

    private val slots = arrayOfNulls<TextDisplay>(SLOTS)

    val activeCount get() = slots.count { it != null }

    /** Spawns a ripple growing from 0 to [radius] blocks while fading out over [ticks]; false if all slots are busy. */
    fun spawn(location: Location, rgb: Int, radius: Double, ticks: Int): Boolean {
        require(radius > 0 && ticks > 0) { "radius and ticks must be positive" }
        val world = location.world ?: return false
        val slot = slots.indexOfFirst { it == null }
        if (slot < 0) return false

        val display = world.spawn(location, TextDisplay::class.java) {
            it.isPersistent = false
            it.isVisibleByDefault = false
            it.text(Component.text(GLYPH, TextColor.color(rgb and 0xFFFFFF)))
            it.textOpacity = OPACITY
            it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            it.isShadowed = false
            it.isSeeThrough = false
            it.billboard = Display.Billboard.CENTER
            it.brightness = Display.Brightness(slot, 15)
            it.transformation = scale(strength = 1f, radius = 0f)
        }
        slots[slot] = display
        // Only pack users get the marker; without the pack it would show as a stray glyph.
        pack.loadedPlayers().forEach { it.showEntity(plugin, display) }
        if (activeCount == 1) pack.loadedPlayers().forEach(::addEffect)

        // A tick later so clients have the start state, then let them interpolate to the end state.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!display.isValid) return@Runnable
            display.interpolationDelay = 0
            display.interpolationDuration = ticks
            display.transformation = scale(strength = 0f, radius = radius.toFloat())
        }, 1L)
        plugin.server.scheduler.runTaskLater(plugin, Runnable { remove(slot, display) }, ticks + 2L)
        return true
    }

    fun clear() {
        for (slot in slots.indices) slots[slot]?.let { remove(slot, it) }
    }

    fun onPackLoaded(player: Player) {
        slots.filterNotNull().forEach { player.showEntity(plugin, it) }
        if (activeCount > 0) addEffect(player)
    }

    private fun remove(slot: Int, display: TextDisplay) {
        if (slots[slot] !== display) return
        slots[slot] = null
        display.remove()
        if (activeCount == 0) pack.loadedPlayers().forEach(::removeEffect)
    }

    private fun scale(strength: Float, radius: Float) = Transformation(
        Vector3f(),
        Quaternionf(),
        Vector3f(strength * SCALE_UNIT, radius * SCALE_UNIT, SCALE_UNIT),
        Quaternionf(),
    )

    private fun addEffect(player: Player) = postEffect("add", player)

    private fun removeEffect(player: Player) = postEffect("remove", player)

    // By name: /posteffect rejects UUIDs since they could match non-player entities.
    private fun postEffect(action: String, player: Player) {
        plugin.server.dispatchCommand(plugin.server.consoleSender, "posteffect $action ${player.name} $POST_EFFECT")
    }

    // The client may drop post effects on respawn or dimension change, so re-apply while ripples are live.
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) = reapply(event.player)

    @EventHandler
    fun onChangedWorld(event: PlayerChangedWorldEvent) = reapply(event.player)

    private fun reapply(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline && pack.hasLoaded(player) && activeCount > 0) addEffect(player)
        }, 1L)
    }
}
