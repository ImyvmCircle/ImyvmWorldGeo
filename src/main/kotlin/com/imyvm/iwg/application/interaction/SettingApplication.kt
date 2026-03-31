package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.rule.helper.getRuleValue
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUILD_BREAK
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_CONTAINER
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FLY
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUILD
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BREAK
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_INTERACTION
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_REDSTONE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_TRADE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_PVP
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUCKET_BUILD
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_BUCKET_SCOOP
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ANIMAL_KILLING
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_VILLAGER_KILLING
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_EGG_USE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_THROWABLE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_SNOWBALL_USE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_POTION_USE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_FARMING
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_IGNITE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ARMOR_STAND
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_ITEM_FRAME
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_WIND_CHARGE_USE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_BOW_SHOOT
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_VEHICLE_USE
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_EATING
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_FISHING
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_SPAWN_MONSTERS
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_SPAWN_PHANTOMS
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_TNT_BLOCK_PROTECTION
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_ENDERMAN_BLOCK_PICKUP
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_SCULK_SPREAD
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_SNOW_GOLEM_TRAIL
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_DISPENSER
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_PRESSURE_PLATE
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_PISTON
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import com.imyvm.iwg.util.translator.resolvePlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

fun onHandleSetting(
    player: ServerPlayer,
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
        player.sendSystemMessage(Translator.tr(e.message)!!)
    }
}

fun onCertificatePermissionValue(
    playerExecutor: ServerPlayer,
    region: Region?,
    scopeName: String?,
    targetPlayerNameStr: String?,
    keyString: String,
): Boolean {
    val key = parseKey(keyString)
    if (key !is PermissionKey) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }

    val scope = if (scopeName != null) {
        try {
            region?.getScopeByName(scopeName)
        } catch (e: IllegalArgumentException) {
            return getDefaultValueForPermission(key)
        }
    } else null

    val uuid = if (targetPlayerNameStr != null) {
         getUUIDFromPlayerName(playerExecutor.level().server, targetPlayerNameStr) ?: return getDefaultValueForPermission(key)
    } else null
    return onCertificatePermissionValue(region, scope, uuid, key)
}

fun onCertificatePermissionValue(
    region: Region?,
    scope: GeoScope?,
    playerUuid: UUID?,
    key: PermissionKey
): Boolean {
    if (region == null) {
        return getDefaultValueForPermission(key)
    }

    val settingsContainer = scope?.settings ?: region.settings

    val setting = settingsContainer.firstOrNull { setting ->
        if (setting.key != key) return@firstOrNull false
        if (playerUuid == null) {
            return@firstOrNull !setting.isPersonal
        } else {
            return@firstOrNull setting.playerUUID == playerUuid
        }
    }

    return if (setting is PermissionSetting) {
        setting.value
    } else {
        getDefaultValueForPermission(key)
    }
}

private fun parseKey(keyString: String): Any = when {
    isPermissionKey(keyString) -> PermissionKey.valueOf(keyString)
    isEffectKey(keyString) -> EffectKey.valueOf(keyString)
    isRuleKey(keyString) -> RuleKey.valueOf(keyString)
    isEntryExitToggleKey(keyString) -> EntryExitToggleKey.valueOf(keyString)
    isEntryExitMessageKey(keyString) -> EntryExitMessageKey.valueOf(keyString)
    else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
}

private fun parseValue(player: ServerPlayer, key: Any, valueString: String): Any? {
    return try {
        when (key) {
            is PermissionKey -> valueString.toBooleanStrict()
            is EffectKey -> valueString.toInt()
            is RuleKey -> valueString.toBooleanStrict()
            is EntryExitToggleKey -> valueString.toBooleanStrict()
            is EntryExitMessageKey -> valueString
            else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
        }
    } catch (e: Exception) {
        val errorMsg = when (key) {
            is PermissionKey, is RuleKey, is EntryExitToggleKey -> "interaction.meta.setting.error.invalid_value_boolean"
            is EffectKey -> "interaction.meta.setting.error.invalid_value_int"
            else -> "interaction.meta.setting.error.invalid_key"
        }
        player.sendSystemMessage(Translator.tr(errorMsg, key.toString(), valueString)!!)
        null
    }
}

private fun handleAddSetting(
    player: ServerPlayer,
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

    player.sendSystemMessage(Translator.tr("interaction.meta.setting.add.success", key.toString(), value.toString())!!)
}

private fun handleRemoveSetting(
    player: ServerPlayer,
    region: Region,
    scopeName: String?,
    key: Any,
    targetPlayerStr: String?
) {
    val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
    val removed = settingsContainer.removeIf { matchesSetting(it, key, targetPlayerStr, player.level().server) }

    if (!removed) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.delete.error.no_such_setting", key.toString())!!)
        return
    }
    player.sendSystemMessage(Translator.tr("interaction.meta.setting.delete.success", key.toString())!!)
}

private fun buildSetting(
    key: Any,
    value: Any,
    targetPlayerUUID: UUID?
): Setting = when (key) {
    is PermissionKey -> PermissionSetting(key, value as Boolean, targetPlayerUUID)
    is EffectKey -> EffectSetting(key, value as Int, targetPlayerUUID)
    is RuleKey -> RuleSetting(key, value as Boolean)
    is EntryExitToggleKey -> EntryExitToggleSetting(key, value as Boolean)
    is EntryExitMessageKey -> EntryExitMessageSetting(key, value as String)
    else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
}

private fun resolveTargetPlayerUUID(
    player: ServerPlayer,
    targetPlayerStr: String?
): UUID? {
    if (targetPlayerStr == null) return null

    val uuid = getUUIDFromPlayerName(player.level().server, targetPlayerStr)
    return if (uuid != null) {
        uuid
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.invalid_target_player", targetPlayerStr)!!)
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
    player: ServerPlayer,
    region: Region,
    scopeName: String?,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
): Boolean {
    val server = player.level().server

    if (isEntryExitToggleKey(keyString) || isEntryExitMessageKey(keyString)) {
        if (targetPlayerStr != null) {
            player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.entry_exit_no_personal")!!)
            return false
        }
    }

    if (valueString != null) {
        val container = try {
            scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
        } catch (e: IllegalArgumentException) {
            player.sendSystemMessage(Translator.tr(e.message)!!)
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
            player.sendSystemMessage(Translator.tr(msgKey, keyString, targetPlayerStr ?: "", scopeName ?: "")!!)
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
        PermissionKey.BUILD_BREAK -> PERMISSION_DEFAULT_BUILD_BREAK.value
        PermissionKey.INTERACTION -> PERMISSION_DEFAULT_INTERACTION.value
        PermissionKey.CONTAINER -> PERMISSION_DEFAULT_CONTAINER.value
        PermissionKey.FLY -> PERMISSION_DEFAULT_FLY.value
        PermissionKey.BUILD -> PERMISSION_DEFAULT_BUILD.value
        PermissionKey.BREAK -> PERMISSION_DEFAULT_BREAK.value
        PermissionKey.REDSTONE -> PERMISSION_DEFAULT_REDSTONE.value
        PermissionKey.TRADE -> PERMISSION_DEFAULT_TRADE.value
        PermissionKey.PVP -> PERMISSION_DEFAULT_PVP.value
        PermissionKey.BUCKET_BUILD -> PERMISSION_DEFAULT_BUCKET_BUILD.value
        PermissionKey.BUCKET_SCOOP -> PERMISSION_DEFAULT_BUCKET_SCOOP.value
        PermissionKey.ANIMAL_KILLING -> PERMISSION_DEFAULT_ANIMAL_KILLING.value
        PermissionKey.VILLAGER_KILLING -> PERMISSION_DEFAULT_VILLAGER_KILLING.value
        PermissionKey.EGG_USE -> PERMISSION_DEFAULT_EGG_USE.value
        PermissionKey.THROWABLE -> PERMISSION_DEFAULT_THROWABLE.value
        PermissionKey.SNOWBALL_USE -> PERMISSION_DEFAULT_SNOWBALL_USE.value
        PermissionKey.POTION_USE -> PERMISSION_DEFAULT_POTION_USE.value
        PermissionKey.FARMING -> PERMISSION_DEFAULT_FARMING.value
        PermissionKey.IGNITE -> PERMISSION_DEFAULT_IGNITE.value
        PermissionKey.ARMOR_STAND -> PERMISSION_DEFAULT_ARMOR_STAND.value
        PermissionKey.ITEM_FRAME -> PERMISSION_DEFAULT_ITEM_FRAME.value
        PermissionKey.WIND_CHARGE_USE -> PERMISSION_DEFAULT_WIND_CHARGE_USE.value
        PermissionKey.RPG_ITEM_PICKUP -> PERMISSION_DEFAULT_RPG_ITEM_PICKUP.value
        PermissionKey.RPG_BOW_SHOOT -> PERMISSION_DEFAULT_RPG_BOW_SHOOT.value
        PermissionKey.RPG_VEHICLE_USE -> PERMISSION_DEFAULT_RPG_VEHICLE_USE.value
        PermissionKey.RPG_EATING -> PERMISSION_DEFAULT_RPG_EATING.value
        PermissionKey.RPG_FISHING -> PERMISSION_DEFAULT_RPG_FISHING.value
    }
}

fun getDefaultValueForRule(key: RuleKey): Boolean {
    return when (key) {
        RuleKey.SPAWN_MONSTERS -> RULE_DEFAULT_SPAWN_MONSTERS.value
        RuleKey.SPAWN_PHANTOMS -> RULE_DEFAULT_SPAWN_PHANTOMS.value
        RuleKey.TNT_BLOCK_PROTECTION -> RULE_DEFAULT_TNT_BLOCK_PROTECTION.value
        RuleKey.ENDERMAN_BLOCK_PICKUP -> RULE_DEFAULT_ENDERMAN_BLOCK_PICKUP.value
        RuleKey.SCULK_SPREAD -> RULE_DEFAULT_SCULK_SPREAD.value
        RuleKey.SNOW_GOLEM_TRAIL -> RULE_DEFAULT_SNOW_GOLEM_TRAIL.value
        RuleKey.DISPENSER -> RULE_DEFAULT_DISPENSER.value
        RuleKey.PRESSURE_PLATE -> RULE_DEFAULT_PRESSURE_PLATE.value
        RuleKey.PISTON -> RULE_DEFAULT_PISTON.value
    }
}

private fun isPermissionKey(key: String) = runCatching { PermissionKey.valueOf(key) }.isSuccess
private fun isEffectKey(key: String) = runCatching { EffectKey.valueOf(key) }.isSuccess
private fun isRuleKey(key: String) = runCatching { RuleKey.valueOf(key) }.isSuccess
private fun isEntryExitToggleKey(key: String) = runCatching { EntryExitToggleKey.valueOf(key) }.isSuccess
private fun isEntryExitMessageKey(key: String) = runCatching { EntryExitMessageKey.valueOf(key) }.isSuccess

fun onCertificateRuleValue(
    region: Region?,
    scopeName: String?,
    keyString: String,
): Boolean? {
    val key = parseKey(keyString)
    if (key !is RuleKey) throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    if (region == null) return null
    val scope = scopeName?.let {
        try { region.getScopeByName(it) } catch (e: IllegalArgumentException) { null }
    }
    return getRuleValue(region, key, scope)
}

fun onQuerySettingValue(
    player: ServerPlayer,
    region: Region,
    scopeName: String?,
    keyString: String,
    targetPlayerStr: String?
) {
    try {
        val key = parseKey(keyString)
        val displayTarget = if (scopeName != null) "Scope &b${scopeName}&r of Region &b${region.name}&r" else "Region &b${region.name}&r"
        when (key) {
            is RuleKey -> {
                val value = onCertificateRuleValue(region, scopeName, keyString)
                if (value == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
                }
            }
            is PermissionKey -> {
                val value = onCertificatePermissionValue(player, region, scopeName, targetPlayerStr, keyString)
                player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
            }
            is EntryExitToggleKey -> {
                val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
                val setting = settingsContainer.filterIsInstance<EntryExitToggleSetting>().firstOrNull { it.key == key }
                if (setting == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, setting.value, displayTarget)!!)
                }
            }
            is EntryExitMessageKey -> {
                val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
                val setting = settingsContainer.filterIsInstance<EntryExitMessageSetting>().firstOrNull { it.key == key }
                if (setting == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, setting.value, displayTarget)!!)
                }
            }
            is EffectKey -> {
                val settingsContainer = scopeName?.let { region.getScopeByName(it).settings } ?: region.settings
                val setting = settingsContainer.filterIsInstance<EffectSetting>().firstOrNull { it.key == key }
                if (setting == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, setting.value, displayTarget)!!)
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
    }
}