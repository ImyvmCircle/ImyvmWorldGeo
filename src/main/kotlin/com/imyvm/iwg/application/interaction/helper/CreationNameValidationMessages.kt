package com.imyvm.iwg.application.interaction.helper

import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

object NameValidationMessages {
    fun sendRegionAutoFilled(player: ServerPlayer, name: String) {
        player.sendSystemMessage(Translator.tr("interaction.meta.name_auto_filled", name))
    }

    fun sendScopeAutoFilled(player: ServerPlayer, name: String) {
        player.sendSystemMessage(Translator.tr("interaction.meta.add.name_auto_filled", name))
    }

    fun sendRegionNameRequired(player: ServerPlayer) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.name_invalid"))
    }

    fun sendScopeNameRequired(player: ServerPlayer) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.add.name_invalid"))
    }

    @Deprecated("Use sendRegionAutoFilled or sendScopeAutoFilled")
    fun sendAutoFilled(player: ServerPlayer, type: NameType, name: String) {
        when (type) {
            NameType.REGION -> sendRegionAutoFilled(player, name)
            NameType.SCOPE -> sendScopeAutoFilled(player, name)
        }
    }

    @Deprecated("Use sendRegionNameRequired or sendScopeNameRequired")
    fun sendNameRequired(player: ServerPlayer, type: NameType) {
        when (type) {
            NameType.REGION -> sendRegionNameRequired(player)
            NameType.SCOPE -> sendScopeNameRequired(player)
        }
    }

    fun sendDuplicateScope(player: ServerPlayer) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.add.duplicate_scope_name"))
    }
}

enum class NameType { REGION, SCOPE }
