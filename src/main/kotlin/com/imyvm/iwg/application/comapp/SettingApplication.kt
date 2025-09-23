package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.domain.EffectKey
import com.imyvm.iwg.domain.PermissionKey
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.RuleKey
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.network.ServerPlayerEntity

fun onHandleSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String,
    isPersonal: Boolean,
    targetPlayerStr: String
){
    if(!checkKeyValidity(player, keyString, valueString)) return
}

private fun checkKeyValidity(player: ServerPlayerEntity, keyString: String, valueString: String): Boolean {
    return when {
        isPermissionKey(keyString) -> checkPermissionSetting(player, keyString, valueString)
        isEffectKey(keyString) -> checkEffectSetting(player, keyString, valueString)
        isRuleKey(keyString) -> checkRuleSetting(player, keyString, valueString)
        else -> {
            player.sendMessage(Translator.tr("command.setting.error.invalid_key", keyString))
            false
        }
    }
}

private fun isPermissionKey(key: String) = runCatching { PermissionKey.valueOf(key) }.isSuccess
private fun isEffectKey(key: String) = runCatching { EffectKey.valueOf(key) }.isSuccess
private fun isRuleKey(key: String) = runCatching { RuleKey.valueOf(key) }.isSuccess

private fun checkPermissionSetting(player: ServerPlayerEntity, keyString: String, valueString: String): Boolean {
    val value = valueString.toBooleanStrictOrNull()
    if (value == null) {
        player.sendMessage(Translator.tr("command.setting.error.invalid_value_boolean", keyString, valueString))
        return false
    }

    return true
}

private fun checkEffectSetting(player: ServerPlayerEntity, keyString: String, valueString: String): Boolean {
    val value = valueString.toIntOrNull()
    if (value == null) {
        player.sendMessage(Translator.tr("command.setting.error.invalid_value_int", keyString, valueString))
        return false
    }
    return true
}

private fun checkRuleSetting(player: ServerPlayerEntity, keyString: String, valueString: String): Boolean {
    val value = valueString.toBooleanStrictOrNull()
    if (value == null) {
        player.sendMessage(Translator.tr("command.setting.error.invalid_value_boolean", keyString, valueString))
        return false
    }
    return true
}
