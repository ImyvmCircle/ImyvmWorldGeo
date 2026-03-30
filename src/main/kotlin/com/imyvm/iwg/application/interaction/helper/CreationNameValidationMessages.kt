package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

object NameValidationMessages {
    fun sendAutoFilled(player: ServerPlayer, type: NameType, name: String) {
        val key = when (type) {
            NameType.REGION -> "interaction.meta.name_auto_filled"
            NameType.SCOPE -> "interaction.meta.add.name_auto_filled"
        }
        player.sendSystemMessage(Translator.tr(key, name)!!)
    }

    fun sendNameRequired(player: ServerPlayer, type: NameType) {
        val key = when (type) {
            NameType.REGION -> "interaction.meta.create.name_invalid"
            NameType.SCOPE -> "interaction.meta.scope.add.name_invalid"
        }
        player.sendSystemMessage(Translator.tr(key)!!)
    }

    fun sendDuplicateScope(player: ServerPlayer) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.add.duplicate_scope_name")!!)
    }
}

enum class NameType { REGION, SCOPE }