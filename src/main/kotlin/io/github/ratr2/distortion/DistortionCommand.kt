package io.github.ratr2.distortion

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Entity

/**
 * `/distortion <hex> <radius max> <time in ticks> [x y z]`. Coordinates accept `~` relative to the sender
 * (player, entity or command block); without them the ripple spawns at the sender.
 */
class DistortionCommand(private val ripples: RippleManager) : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size != 3 && args.size != 6) return false

        val rgb = args[0].removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
        val radius = args[1].toDoubleOrNull()?.takeIf { it > 0 }
        val ticks = args[2].toIntOrNull()?.takeIf { it > 0 }
        if (rgb == null || radius == null || ticks == null) {
            return error(sender, "Usage: /$label <hex, e.g. #55CCFF> <radius max > 0> <time in ticks > 0> [x y z]")
        }

        val origin = when (sender) {
            is Entity -> sender.location
            is BlockCommandSender -> sender.block.location.add(0.5, 1.0, 0.5)
            else -> null
        } ?: return error(sender, "Run this as a player or from a command block.")

        val location = if (args.size == 6) {
            parse(origin, args[3], args[4], args[5]) ?: return error(sender, "Invalid coordinates: use numbers or ~ / ~offset.")
        } else {
            origin
        }

        if (!ripples.spawn(location, rgb, radius, ticks)) {
            return error(sender, "All ${RippleManager.SLOTS} ripple slots are in use.")
        }
        return true
    }

    private fun parse(origin: Location, x: String, y: String, z: String): Location? {
        fun coord(arg: String, base: Double): Double? =
            if (arg.startsWith("~")) arg.drop(1).ifEmpty { "0" }.toDoubleOrNull()?.plus(base) else arg.toDoubleOrNull()

        return Location(
            origin.world,
            coord(x, origin.x) ?: return null,
            coord(y, origin.y) ?: return null,
            coord(z, origin.z) ?: return null,
        )
    }

    private fun error(sender: CommandSender, message: String): Boolean {
        sender.sendMessage(Component.text(message, NamedTextColor.RED))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        when (args.size) {
            1 -> listOf("#55CCFF", "#FF8833", "#FFFFFF")
            2 -> listOf("4", "8", "16")
            3 -> listOf("20", "40", "60")
            in 4..6 -> listOf("~")
            else -> emptyList()
        }.filter { it.startsWith(args.last(), ignoreCase = true) }
}
