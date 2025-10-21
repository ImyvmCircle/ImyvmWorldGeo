package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity

object NameValidationMessages {
    fun sendAutoFilled(player: ServerPlayerEntity, type: NameType, name: String) {
        val key = when (type) {
            NameType.REGION -> "interaction.meta.name_auto_filled"
            NameType.SCOPE -> "interaction.meta.add.name_auto_filled"
        }
        player.sendMessage(Translator.tr(key, name))
    }

    fun sendNameRequired(player: ServerPlayerEntity, type: NameType) {
        val key = when (type) {
            NameType.REGION -> "interaction.meta.create.name_invalid"
            NameType.SCOPE -> "interaction.meta.scope.add.name_invalid"
        }
        player.sendMessage(Translator.tr(key))
    }

    fun sendDuplicateScope(player: ServerPlayerEntity) {
        player.sendMessage(Translator.tr("interaction.meta.scope.add.duplicate_scope_name"))
    }
}

enum class NameType { REGION, SCOPE }