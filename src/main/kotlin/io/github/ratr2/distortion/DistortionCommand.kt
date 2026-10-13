package io.github.ratr2.distortion

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * ```
 * /distortion spawn <hex> <radius> <ticks> [x y z]
 * /distortion reload <config|host|both>
 * /distortion debug [on|off]
 * ```
 */
class DistortionCommand(
    private val plugin: Distortion,
    private val ripples: RippleManager,
    private val pack: ResourcePack,
) {

    fun node(): LiteralCommandNode<CommandSourceStack> = Commands.literal("distortion")
        .requires { source -> listOf(SPAWN, RELOAD, DEBUG).any(source.sender::hasPermission) }
        .then(
            Commands.literal("spawn")
                .requires { it.sender.hasPermission(SPAWN) }
                .then(
                    Commands.argument("hex", StringArgumentType.string())
                        .then(
                            Commands.argument("radius", DoubleArgumentType.doubleArg(0.01))
                                .then(
                                    Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes { spawn(it, it.source.location) }
                                        .then(
                                            Commands.argument("pos", ArgumentTypes.finePosition(false))
                                                .executes {
                                                    val pos = it.getArgument("pos", FinePositionResolver::class.java).resolve(it.source)
                                                    spawn(it, pos.toLocation(it.source.location.world))
                                                }
                                        )
                                )
                        )
                )
        )
        .then(
            Commands.literal("reload")
                .requires { it.sender.hasPermission(RELOAD) }
                .then(Commands.literal("config").executes { reload(it, config = true, host = false) })
                .then(Commands.literal("host").executes { reload(it, config = false, host = true) })
                .then(Commands.literal("both").executes { reload(it, config = true, host = true) })
        )
        .then(
            Commands.literal("debug")
                .requires { it.sender.hasPermission(DEBUG) && it.sender is Player }
                .executes { debug(it, null) }
                .then(Commands.literal("on").executes { debug(it, true) })
                .then(Commands.literal("off").executes { debug(it, false) })
        )
        .build()

    private fun debug(context: CommandContext<CommandSourceStack>, enabled: Boolean?): Int {
        val player = context.source.sender as Player
        val on = enabled ?: !ripples.isDebugging(player)
        ripples.setDebug(player, on)
        val state = if (on) "on: outlines + center crosses per ripple, raw data strip in the corner" else "off"
        player.sendMessage(Component.text("Distortion debug $state.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun spawn(context: CommandContext<CommandSourceStack>, location: Location): Int {
        val sender = context.source.sender
        val rgb = StringArgumentType.getString(context, "hex").removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
        if (rgb == null) {
            sender.sendMessage(Component.text("Color must be 6 hex digits, e.g. 55CCFF.", NamedTextColor.RED))
            return 0
        }
        val radius = DoubleArgumentType.getDouble(context, "radius")
        val ticks = IntegerArgumentType.getInteger(context, "ticks")
        if (!ripples.spawn(location, rgb, radius, ticks)) {
            sender.sendMessage(Component.text("All ${RippleManager.SLOTS} ripple slots are in use.", NamedTextColor.RED))
            return 0
        }
        return Command.SINGLE_SUCCESS
    }

    private fun reload(context: CommandContext<CommandSourceStack>, config: Boolean, host: Boolean): Int {
        if (config) plugin.reloadConfig()
        if (host) pack.restart()
        val what = listOfNotNull("config".takeIf { config }, "pack host (${pack.url})".takeIf { host }).joinToString(" and ")
        context.source.sender.sendMessage(Component.text("Reloaded $what.", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    companion object {
        const val SPAWN = "distortion.spawn"
        const val RELOAD = "distortion.reload"
        const val DEBUG = "distortion.debug"
    }
}
