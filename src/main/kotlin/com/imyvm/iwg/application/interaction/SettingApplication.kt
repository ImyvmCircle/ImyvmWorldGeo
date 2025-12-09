package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

fun onHandleSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) {
    if (!checkPlayer(player, region, scopeName, keyString, valueString, targetPlayerStr)) return

    try {
        val key = parseKey(keyString)
        if (valueString != null) {
            val value = parseValue(player, key, valueString) ?: return
            handleAddSetting(player, region, scopeName, key, value, targetPlayerStr)
        } else {
            handleRemoveSetting(player, region, scopeName, key, targetPlayerStr)
        }
    } catch (e: IllegalArgumentException) {
        player.sendMessage(Translator.tr(e.message))
    }
}

fun onCertificatePermissionValue(
    region: Region,
    scopeName: String?,
    targetPlayerStr: String?,
    keyString: String,
): Boolean {
    TODO()
}

private fun parseKey(keyString: String): Any = when {
    isPermissionKey(keyString) -> PermissionKey.valueOf(keyString)
    isEffectKey(keyString) -> EffectKey.valueOf(keyString)
    isRuleKey(keyString) -> RuleKey.valueOf(keyString)
    else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
}

private fun parseValue(player: ServerPlayerEntity, key: Any, valueString: String): Any? {
    return try {
        when (key) {
            is PermissionKey -> valueString.toBooleanStrict()
            is EffectKey -> valueString.toInt()
            is RuleKey -> valueString.toBooleanStrict()
            else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
        }
    } catch (e: Exception) {
        val errorMsg = when (key) {
            is PermissionKey, is RuleKey -> "interaction.meta.setting.error.invalid_value_boolean"
            is EffectKey -> "interaction.meta.setting.error.invalid_value_int"
            else -> "interaction.meta.setting.error.invalid_key"
        }
        player.sendMessage(Translator.tr(errorMsg, key.toString(), valueString))
        null
    }
}

private fun handleAddSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    key: Any,
    value: Any,
    targetPlayerStr: String?
) {
    val targetPlayerUUID = if (targetPlayerStr != null) {
        resolveTargetPlayerUUID(player, targetPlayerStr) ?: return
    } else {
        null
    }
    val setting = buildSetting(key, value, targetPlayerUUID)

    val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
    settingsContainer.add(setting)

    player.sendMessage(Translator.tr("interaction.meta.setting.add.success", key.toString(), value.toString()))
}

private fun handleRemoveSetting(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    key: Any,
    targetPlayerStr: String?
) {
    val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
    val removed = settingsContainer.removeIf { matchesSetting(it, key, targetPlayerStr, player.server) }

    if (!removed) {
        player.sendMessage(Translator.tr("interaction.meta.setting.delete.error.no_such_setting", key.toString()))
        return
    }
    player.sendMessage(Translator.tr("interaction.meta.setting.delete.success", key.toString()))
}

private fun buildSetting(
    key: Any,
    value: Any,
    targetPlayerUUID: UUID?
): Setting = when (key) {
    is PermissionKey -> PermissionSetting(key, value as Boolean, targetPlayerUUID)
    is EffectKey -> EffectSetting(key, value as Int, targetPlayerUUID)
    is RuleKey -> RuleSetting(key, value as Boolean)
    else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
}

private fun resolveTargetPlayerUUID(
    player: ServerPlayerEntity,
    targetPlayerStr: String?
): UUID? {
    if (targetPlayerStr == null) return null

    val uuid = getUUIDFromPlayerName(player.server, targetPlayerStr)
    return if (uuid != null) {
        uuid
    } else {
        player.sendMessage(Translator.tr("interaction.meta.setting.error.invalid_target_player", targetPlayerStr))
        null
    }
}

private fun matchesSetting(
    setting: Setting,
    key: Any,
    targetPlayerStr: String?,
    server: MinecraftServer
): Boolean {
    if (setting.key != key) return false
    if (targetPlayerStr == null) {
        return setting.playerUUID == null
    }

    val name = resolvePlayerName(server, setting.playerUUID)
    return name.equals(targetPlayerStr, ignoreCase = true)
}

private fun checkPlayer(
    player: ServerPlayerEntity,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
): Boolean {
    val server = player.server

    if (valueString != null) {
        val container = try {
            scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
        } catch (e: IllegalArgumentException) {
            player.sendMessage(Translator.tr(e.message))
            return false
        }

        if (isDuplicateSetting(server, container, keyString, targetPlayerStr)) {
            val msgKey = if (scopeName == null) {
                if (targetPlayerStr == null) {
                    "interaction.meta.setting.error.region.duplicate_global"
                } else {
                    "interaction.meta.setting.error.region.duplicate_player"
                }
            } else {
                if (targetPlayerStr == null) {
                    "interaction.meta.setting.error.scope.duplicate_global"
                } else {
                    "interaction.meta.setting.error.scope.duplicate_player"
                }
            }
            player.sendMessage(Translator.tr(msgKey, keyString, targetPlayerStr ?: "", scopeName ?: ""))
            return false
        }
    }
    return true
}

private fun isDuplicateSetting(
    server: MinecraftServer,
    settings: List<Setting>,
    keyString: String,
    targetPlayerStr: String?
): Boolean {
    return settings.any { setting ->
        if (setting.key.toString() != keyString) return@any false

        if (targetPlayerStr == null) {
            return@any setting.playerUUID == null
        } else {
            val name = resolvePlayerName(server, setting.playerUUID)
            return@any name.equals(targetPlayerStr, ignoreCase = true)
        }
    }
}

private fun getDefaultValueForPermission(key: PermissionKey): Boolean {
    return when (key) {
        PermissionKey.BUILD_BREAK -> true
        PermissionKey.CONTAINER -> true
        PermissionKey.FLY -> false
    }
}

private fun isPermissionKey(key: String) = runCatching { PermissionKey.valueOf(key) }.isSuccess
private fun isEffectKey(key: String) = runCatching { EffectKey.valueOf(key) }.isSuccess
private fun isRuleKey(key: String) = runCatching { RuleKey.valueOf(key) }.isSuccess
