package com.imyvm.iwg.inter.commands

import com.imyvm.iwg.util.ui.Translator
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

fun runHelp(context: CommandContext<ServerCommandSource>): Int {
    val player = context.source.player ?: return 0
    player.sendMessage(Translator.tr("command.help"))
    return 1
}