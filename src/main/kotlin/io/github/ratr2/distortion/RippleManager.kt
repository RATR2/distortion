package io.github.ratr2.distortion

import net.kyori.adventure.key.Key
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
import java.util.UUID

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
        private val POST_EFFECT = Key.key("distortion", "ripple")
        private val DEBUG_EFFECT = Key.key("distortion", "ripple_debug")
    }

    private val slots = arrayOfNulls<TextDisplay>(SLOTS)
    private val debugging = HashSet<UUID>()

    val activeCount get() = slots.count { it != null }

    /** Spawns a ripple growing from 0 to [radius] blocks while fading out over [ticks]; false if all slots are busy. */
    fun spawn(location: Location, rgb: Int, radius: Double, ticks: Int): Boolean {
        require(radius > 0 && ticks > 0) { "radius and ticks must be positive" }
        val world = location.world ?: return false
        val slot = slots.indexOfFirst { it == null }
        if (slot < 0) return false

        // Culled as a box from radius below the center to radius above it, so the ring stays while any of it is on
        // screen; the glyph is lifted back to the center by the transformation's translation.
        val base = Location(world, location.x, location.y - radius, location.z, 0f, 0f)
        val display = world.spawn(base, TextDisplay::class.java) {
            it.isPersistent = false
            it.isVisibleByDefault = false
            it.text(Component.text(GLYPH, TextColor.color(rgb and 0xFFFFFF)))
            it.textOpacity = OPACITY
            it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            it.isShadowed = false
            it.isSeeThrough = false
            it.billboard = Display.Billboard.FIXED
            it.displayWidth = (radius * 2).toFloat()
            it.displayHeight = (radius * 2).toFloat()
            it.brightness = Display.Brightness(slot, 15)
            it.transformation = transformation(strength = 1f, radius = 0f, lift = radius.toFloat())
        }
        slots[slot] = display
        // Only pack users get the marker; without the pack it would show as a stray glyph.
        // Re-assert the effect too: add() is a no-op if present, and covers it being dropped after the pack loaded.
        pack.loadedPlayers().forEach {
            it.showEntity(plugin, display)
            it.postEffects().add(effectFor(it))
        }

        // A tick later so clients have the start state, then let them interpolate to the end state.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!display.isValid) return@Runnable
            display.interpolationDelay = 0
            display.interpolationDuration = ticks
            display.transformation = transformation(strength = 0f, radius = radius.toFloat(), lift = radius.toFloat())
        }, 1L)
        plugin.server.scheduler.runTaskLater(plugin, Runnable { remove(slot, display) }, ticks + 2L)
        return true
    }

    fun clear() {
        for (slot in slots.indices) slots[slot]?.let { remove(slot, it) }
        pack.loadedPlayers().forEach { it.postEffects().remove(effectFor(it)) }
    }

    // The effect stays on for pack users: with no live markers it just copies the screen, and toggling it per
    // ripple left a tick where a marker's data pixels showed before the effect synced.
    fun onPackLoaded(player: Player) {
        slots.filterNotNull().forEach { player.showEntity(plugin, it) }
        player.postEffects().add(effectFor(player))
    }

    fun isDebugging(player: Player) = player.uniqueId in debugging

    /** Swaps [player] between the normal and debug ripple effect; they must never run stacked. */
    fun setDebug(player: Player, enabled: Boolean) {
        val effects = player.postEffects()
        effects.remove(effectFor(player))
        if (enabled) debugging.add(player.uniqueId) else debugging.remove(player.uniqueId)
        if (pack.hasLoaded(player)) effects.add(effectFor(player))
    }

    private fun effectFor(player: Player) = if (isDebugging(player)) DEBUG_EFFECT else POST_EFFECT

    private fun remove(slot: Int, display: TextDisplay) {
        if (slots[slot] !== display) return
        slots[slot] = null
        display.remove()
    }

    private fun transformation(strength: Float, radius: Float, lift: Float) = Transformation(
        Vector3f(0f, lift, 0f),
        Quaternionf(),
        Vector3f(strength * SCALE_UNIT, radius * SCALE_UNIT, SCALE_UNIT),
        Quaternionf(),
    )

    // The client may drop post effects on respawn or dimension change, so re-apply.
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) = reapply(event.player)

    @EventHandler
    fun onChangedWorld(event: PlayerChangedWorldEvent) = reapply(event.player)

    private fun reapply(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline && pack.hasLoaded(player)) player.postEffects().add(effectFor(player))
        }, 1L)
    }
}
