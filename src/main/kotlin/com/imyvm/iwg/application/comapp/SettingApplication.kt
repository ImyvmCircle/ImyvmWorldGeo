package com.imyvm.iwg.application.comapp

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.util.ui.Translator
import net.minecraft.server.MinecraftServer
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

    try {
        val key = when {
            isPermissionKey(keyString) -> PermissionKey.valueOf(keyString)
            isEffectKey(keyString) -> EffectKey.valueOf(keyString)
            isRuleKey(keyString) -> RuleKey.valueOf(keyString)
            else -> throw IllegalArgumentException("command.setting.error.invalid_key")
        }
        if (valueString != null) {
            val value = when (key) {
                is PermissionKey -> valueString.toBooleanStrict()
                is EffectKey -> valueString.toInt()
                is RuleKey -> valueString.toBooleanStrict()
                else -> throw IllegalArgumentException("command.setting.error.invalid_key")
            }
            val targetPlayerUUID = if (isPersonal == true) {
                try {
                    val server = player.server
                    val profile = server.userCache
                        ?.findByName(targetPlayerStr)
                        ?.orElse(null)
                        ?: throw IllegalArgumentException(
                            Translator.tr("command.setting.error.invalid_target_player", targetPlayerStr).toString()
                        )
                    profile.id
                } catch (e: IllegalArgumentException) {
                    player.sendMessage(Translator.tr("command.setting.error.invalid_target_player", targetPlayerStr))
                    return
                }
            } else null

            val setting: Setting = when (key) {
                is PermissionKey -> PermissionSetting(key, value as Boolean, isPersonal == true, targetPlayerUUID)
                is EffectKey -> EffectSetting(key, value as Int, isPersonal == true, targetPlayerUUID)
                is RuleKey -> RuleSetting(key, value as Boolean)
                else -> throw IllegalArgumentException("command.setting.error.invalid_key")
            }
            if (scopeName == null) {
                region.settings.add(setting)
            } else {
                val scope = region.getScopeByName(scopeName)
                scope.settings.add(setting)
            }
            player.sendMessage(Translator.tr("command.setting.add.success", keyString, valueString))
        } else {
            val removed = if (scopeName == null) {
                region.settings.removeIf { it.key == key && it.isPersonal == isPersonal && (targetPlayerStr == null || player.server.userCache?.getByUuid(it.playerUUID)?.get()?.name.equals(targetPlayerStr, ignoreCase = true)) }
            } else {
                val scope = region.getScopeByName(scopeName)
                scope.settings.removeIf { it.key == key && it.isPersonal == isPersonal && (targetPlayerStr == null || player.server.userCache?.getByUuid(it.playerUUID)?.get()?.name.equals(targetPlayerStr, ignoreCase = true)) }
            }
            if (!removed) {
                player.sendMessage(Translator.tr("command.setting.delete.error.no_such_setting", keyString))
                return
            }
            player.sendMessage(Translator.tr("command.setting.delete.success", keyString))
        }
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr(e.message))
        return
    }
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
): Boolean {
    if (targetPlayerStr.isNullOrBlank()) {
        player.sendMessage(Translator.tr("command.setting.error.missing_target_player"))
        return false
    }

    val server = player.server

    if (valueString != null) {
        if (scopeName == null) {
            if (isDuplicateSetting(server, region.settings, keyString, isPersonal, targetPlayerStr)) {
                player.sendMessage(
                    Translator.tr(
                        "command.setting.error.region.duplicate_personal_setting",
                        keyString,
                        targetPlayerStr
                    )
                )
                return false
            }
        } else {
            try {
                val scope = region.getScopeByName(scopeName)
                if (isDuplicateSetting(server, scope.settings, keyString, isPersonal, targetPlayerStr)) {
                    player.sendMessage(
                        Translator.tr(
                            "command.setting.error.scope.duplicate_personal_setting",
                            keyString,
                            targetPlayerStr,
                            scopeName
                        )
                    )
                    return false
                }
            } catch (e: IllegalArgumentException) {
                player.sendMessage(Translator.tr(e.message))
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

private fun isDuplicateSetting(server: MinecraftServer, settings: List<Setting>, keyString: String, isPersonal: Boolean?, targetPlayerStr: String?): Boolean {
    return settings
        .filter { it.key.toString() == keyString && it.isPersonal == isPersonal }
        .any { setting ->
            val profile = server.userCache?.getByUuid(setting.playerUUID)
            profile?.get()?.name.equals(targetPlayerStr, ignoreCase = true)
        }
}
