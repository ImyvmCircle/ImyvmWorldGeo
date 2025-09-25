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
    valueString: String?,
    isPersonal: Boolean?,
    targetPlayerStr: String?
){
    // Whether the operation is addition or deletion is analyzed by the presence of valueString.
    if( valueString != null && !checkKeyValueValidity(player, keyString, valueString)) return
    if( isPersonal == true && !checkPlayerWhenPersonal(player, region, scopeName, keyString, valueString, isPersonal, targetPlayerStr)) return
}

private fun checkKeyValueValidity(player: ServerPlayerEntity, keyString: String, valueString: String): Boolean {
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

private fun checkPlayerWhenPersonal(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String?,
    isPersonal: Boolean?,
    targetPlayerStr: String?
): Boolean{
    if (targetPlayerStr.isNullOrBlank()) {
        player.sendMessage(Translator.tr("command.setting.error.missing_target_player"))
        return false
    }

    if (valueString != null) {
        if (scopeName == null) {
            if ( region.settings.filter { (it.key.toString() == keyString) && it.isPersonal == isPersonal }
                    .any { it.playerUUID.toString() == targetPlayerStr } ) {
                player.sendMessage(Translator.tr("command.setting.error.region.duplicate_personal_setting", keyString, targetPlayerStr))
                return false
            }
        } else {
            val scope = region.geometryScope.find { it.scopeName.equals(scopeName, ignoreCase = true) }
            if (scope == null) {
                player.sendMessage(Translator.tr("command.setting.error.region.no_scope", scopeName))
                return false
            }
            if ( scope.settings.filter { (it.key.toString() == keyString) && it.isPersonal == isPersonal }
                    .any { it.playerUUID.toString() == targetPlayerStr } ) {
                player.sendMessage(Translator.tr("command.setting.error.scope.duplicate_personal_setting", keyString, targetPlayerStr, scopeName))
                return false
            }
        }
    }
    return true
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
