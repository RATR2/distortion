package io.github.ratr2.distortion

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Entity

/** `/distortion <hex> <radius max> <time in ticks>` at the sender's position (player, entity or command block). */
class DistortionCommand(private val ripples: RippleManager) : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size != 3) return false

        val rgb = args[0].removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
        val radius = args[1].toDoubleOrNull()?.takeIf { it > 0 }
        val ticks = args[2].toIntOrNull()?.takeIf { it > 0 }
        if (rgb == null || radius == null || ticks == null) {
            sender.sendMessage(Component.text("Usage: /$label <hex, e.g. #55CCFF> <radius max > 0> <time in ticks > 0>", NamedTextColor.RED))
            return true
        }

        val location = when (sender) {
            is Entity -> sender.location
            is BlockCommandSender -> sender.block.location.add(0.5, 1.0, 0.5)
            else -> null
        }
        if (location == null) {
            sender.sendMessage(Component.text("Run this as a player or from a command block.", NamedTextColor.RED))
            return true
        }

        if (!ripples.spawn(location, rgb, radius, ticks)) {
            sender.sendMessage(Component.text("All ${RippleManager.SLOTS} ripple slots are in use.", NamedTextColor.RED))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        when (args.size) {
            1 -> listOf("#55CCFF", "#FF8833", "#FFFFFF")
            2 -> listOf("4", "8", "16")
            3 -> listOf("20", "40", "60")
            else -> emptyList()
        }.filter { it.startsWith(args.last(), ignoreCase = true) }
}
