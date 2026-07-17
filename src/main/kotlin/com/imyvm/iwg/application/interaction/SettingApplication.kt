package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.rule.helper.getRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getScopeRuleValue
import com.imyvm.iwg.application.region.permission.helper.resolveRegionGlobalPermission
import com.imyvm.iwg.application.region.permission.helper.resolveRegionPlayerPermission
import com.imyvm.iwg.application.region.permission.helper.resolveScopeGlobalPermission
import com.imyvm.iwg.application.region.permission.helper.resolveScopePlayerPermission
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.imyvm.iwg.inter.api.SettingAddResult
import com.imyvm.iwg.inter.api.SettingRemoveResult
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
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_RPG_NATURAL_REGEN
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_RPG_FIRE_SPREAD
import com.imyvm.iwg.infra.config.RuleConfig.RULE_DEFAULT_RPG_HUNGER
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import java.util.*

fun addRegionSetting(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) = addSetting(player, SettingMutationTarget.RegionTarget(region), keyString, valueString, targetPlayerStr)

fun addScopeSetting(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) = addSetting(player, SettingMutationTarget.ScopeTarget(region, scope), keyString, valueString, targetPlayerStr)

private fun addSetting(
    player: ServerPlayer,
    target: SettingMutationTarget,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) {
    try {
        if (valueString == null) return
        if (!validateTargetPlayer(player, keyString, targetPlayerStr)) return
        val key = parseKey(keyString)
        val targetPlayerUUID = if (targetPlayerStr != null) {
            resolveTargetPlayerUUID(player, targetPlayerStr) ?: return
        } else null
        val subject = targetPlayerUUID?.let(SettingSubject::Player) ?: SettingSubject.Global
        val displayValue: String
        val result = when (key) {
            is PermissionKey, is ExtensionPermissionKey -> {
                val value = parseBooleanSettingValue(player, keyString, valueString) ?: return
                displayValue = value.toString()
                addPermissionSetting(target, key, subject, value) { saveRegionData(player) }
            }
            is EffectKey -> {
                val amplifier = parseEffectAmplifier(player, keyString, valueString) ?: return
                displayValue = amplifier.toString()
                addEffectSetting(target, key, subject, amplifier) { saveRegionData(player) }
            }
            is RuleKey, is ExtensionRuleKey -> {
                val value = parseBooleanSettingValue(player, keyString, valueString) ?: return
                displayValue = value.toString()
                addRuleSetting(target, key, value) { saveRegionData(player) }
            }
            is EntryExitToggleKey -> {
                val value = parseBooleanSettingValue(player, keyString, valueString) ?: return
                displayValue = value.toString()
                addEntryExitToggleSetting(target, key, value) { saveRegionData(player) }
            }
            is EntryExitMessageKey -> {
                displayValue = valueString
                addEntryExitMessageSetting(target, key, valueString) { saveRegionData(player) }
            }
        }
        if (result == SettingAddResult.ALREADY_EXISTS) {
            val (msgKey, scopeName) = when (target) {
                is SettingMutationTarget.RegionTarget -> {
                    val key = if (targetPlayerStr == null) "interaction.meta.setting.error.region.duplicate_global"
                    else "interaction.meta.setting.error.region.duplicate_player"
                    key to ""
                }
                is SettingMutationTarget.ScopeTarget -> {
                    val key = if (targetPlayerStr == null) "interaction.meta.setting.error.scope.duplicate_global"
                    else "interaction.meta.setting.error.scope.duplicate_personal_player"
                    key to target.scopeName
                }
            }
            player.sendSystemMessage(Translator.tr(msgKey, keyString, targetPlayerStr ?: "", scopeName)!!)
            return
        }
        if (result == SettingAddResult.SUCCESS) {
            player.sendSystemMessage(Translator.tr("interaction.meta.setting.add.success", key.toString(), displayValue)!!)
        }
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
    }
}

fun removeRegionSetting(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    targetPlayerStr: String?
) = removeSetting(player, SettingMutationTarget.RegionTarget(region), keyString, targetPlayerStr)

fun removeScopeSetting(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    targetPlayerStr: String?
) = removeSetting(player, SettingMutationTarget.ScopeTarget(region, scope), keyString, targetPlayerStr)

private fun removeSetting(
    player: ServerPlayer,
    target: SettingMutationTarget,
    keyString: String,
    targetPlayerStr: String?
) {
    try {
        if (!validateTargetPlayer(player, keyString, targetPlayerStr)) return
        val key = parseKey(keyString)
        val targetPlayerUUID = if (targetPlayerStr != null) {
            resolveTargetPlayerUUID(player, targetPlayerStr) ?: return
        } else null
        val subject = targetPlayerUUID?.let(SettingSubject::Player) ?: SettingSubject.Global
        val result = when (key) {
            is PermissionKey, is ExtensionPermissionKey ->
                removePermissionSetting(target, key, subject) { saveRegionData(player) }
            is EffectKey -> removeEffectSetting(target, key, subject) { saveRegionData(player) }
            is RuleKey, is ExtensionRuleKey -> removeRuleSetting(target, key) { saveRegionData(player) }
            is EntryExitToggleKey -> removeEntryExitToggleSetting(target, key) { saveRegionData(player) }
            is EntryExitMessageKey -> removeEntryExitMessageSetting(target, key) { saveRegionData(player) }
        }
        if (result == SettingRemoveResult.NOT_FOUND) {
            player.sendSystemMessage(Translator.tr("interaction.meta.setting.delete.error.no_such_setting", key.toString())!!)
            return
        }
        if (result == SettingRemoveResult.SUCCESS) {
            player.sendSystemMessage(Translator.tr("interaction.meta.setting.delete.success", key.toString())!!)
        }
    } catch (e: IllegalArgumentException) {
        player.sendSystemMessage(Translator.tr(e.message)!!)
    }
}

private fun validateTargetPlayer(player: ServerPlayer, keyString: String, targetPlayerStr: String?): Boolean {
    if ((isRuleKey(keyString) || isExtensionRuleKey(keyString)) && targetPlayerStr != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.rule_no_personal")!!)
        return false
    }
    if ((isEntryExitToggleKey(keyString) || isEntryExitMessageKey(keyString)) && targetPlayerStr != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.entry_exit_no_personal")!!)
        return false
    }
    return true
}

fun onCertificatePermissionValue(
    playerExecutor: ServerPlayer,
    region: Region,
    scope: GeoScope?,
    targetPlayerNameStr: String?,
    keyString: String,
): Boolean {
    val key = parseKey(keyString)

    val uuid = if (targetPlayerNameStr != null) {
         getUUIDFromPlayerName(playerExecutor.level().server, targetPlayerNameStr)
             ?: throw IllegalArgumentException("interaction.meta.setting.error.invalid_target_player")
    } else null

    return when (key) {
        is PermissionKey -> when {
            scope != null && uuid != null -> getScopePermissionValue(region, scope, uuid, key)
            scope != null -> getScopePermissionValue(region, scope, key)
            uuid != null -> getRegionPermissionValue(region, uuid, key)
            else -> getRegionPermissionValue(region, key)
        }
        is ExtensionPermissionKey -> when {
            scope != null && uuid != null -> getScopePermissionValue(region, scope, uuid, key)
            scope != null -> getScopePermissionValue(region, scope, key)
            uuid != null -> getRegionPermissionValue(region, uuid, key)
            else -> getRegionPermissionValue(region, key)
        }
        else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
}

fun getRegionPermissionValue(region: Region, key: PermissionKey): Boolean =
    resolveRegionGlobalPermission(region, key)?.value ?: getDefaultValueForPermission(key)

fun getRegionPermissionValue(region: Region, playerUuid: UUID, key: PermissionKey): Boolean =
    resolveRegionPlayerPermission(region, playerUuid, key)?.value ?: getDefaultValueForPermission(key)

fun getScopePermissionValue(region: Region, scope: GeoScope, key: PermissionKey): Boolean =
    resolveScopeGlobalPermission(region, scope, key)?.value ?: getDefaultValueForPermission(key)

fun getScopePermissionValue(region: Region, scope: GeoScope, playerUuid: UUID, key: PermissionKey): Boolean =
    resolveScopePlayerPermission(region, scope, playerUuid, key)?.value ?: getDefaultValueForPermission(key)

fun getRegionPermissionValue(region: Region, key: ExtensionPermissionKey): Boolean {
    val default = getDefaultValueForPermission(key)
    return resolveRegionGlobalPermission(region, key)?.value ?: default
}

fun getRegionPermissionValue(region: Region, playerUuid: UUID, key: ExtensionPermissionKey): Boolean {
    val default = getDefaultValueForPermission(key)
    return resolveRegionPlayerPermission(region, playerUuid, key)?.value ?: default
}

fun getScopePermissionValue(region: Region, scope: GeoScope, key: ExtensionPermissionKey): Boolean {
    val default = getDefaultValueForPermission(key)
    return resolveScopeGlobalPermission(region, scope, key)?.value ?: default
}

fun getScopePermissionValue(region: Region, scope: GeoScope, playerUuid: UUID, key: ExtensionPermissionKey): Boolean {
    val default = getDefaultValueForPermission(key)
    return resolveScopePlayerPermission(region, scope, playerUuid, key)?.value ?: default
}

fun onCertificateExtensionPermissionValue(
    region: Region?,
    scope: GeoScope?,
    playerUuid: UUID?,
    keyString: String
): Boolean {
    if (!ExtensionSettingRegistry.isRegisteredPermissionKey(keyString)) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    val key = ExtensionSettingRegistry.permissionKey(keyString)
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return getDefaultValueForPermission(key)
    }
    return when {
        scope != null && playerUuid != null -> getScopePermissionValue(region, scope, playerUuid, key)
        scope != null -> getScopePermissionValue(region, scope, key)
        playerUuid != null -> getRegionPermissionValue(region, playerUuid, key)
        else -> getRegionPermissionValue(region, key)
    }
}

private fun parseKey(keyString: String): SettingKey = when {
    isPermissionKey(keyString) -> PermissionKey.valueOf(keyString)
    isEffectKey(keyString) -> EffectKey.valueOf(keyString)
    isRuleKey(keyString) -> RuleKey.valueOf(keyString)
    isEntryExitToggleKey(keyString) -> EntryExitToggleKey.valueOf(keyString)
    isEntryExitMessageKey(keyString) -> EntryExitMessageKey.valueOf(keyString)
    isExtensionPermissionKey(keyString) -> ExtensionSettingRegistry.permissionKey(keyString)
    isExtensionRuleKey(keyString) -> ExtensionSettingRegistry.ruleKey(keyString)
    else -> throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
}

private fun parseBooleanSettingValue(player: ServerPlayer, key: String, value: String): Boolean? =
    value.toBooleanStrictOrNull().also {
        if (it == null) {
            player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.invalid_value_boolean", key, value)!!)
        }
    }

private fun parseEffectAmplifier(player: ServerPlayer, key: String, value: String): Int? {
    val amplifier = value.toIntOrNull()?.takeIf { it in 0..255 }
    if (amplifier == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.invalid_value_int", key, value)!!)
    }
    return amplifier
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

internal fun getDefaultValueForPermission(key: PermissionKey): Boolean {
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

internal fun getDefaultValueForPermission(key: ExtensionPermissionKey): Boolean {
    return ExtensionSettingRegistry.getPermissionDefaultValue(key.id)
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
        RuleKey.RPG_NATURAL_REGEN -> RULE_DEFAULT_RPG_NATURAL_REGEN.value
        RuleKey.RPG_FIRE_SPREAD -> RULE_DEFAULT_RPG_FIRE_SPREAD.value
        RuleKey.RPG_HUNGER -> RULE_DEFAULT_RPG_HUNGER.value
    }
}

private fun getDefaultValueForRule(key: ExtensionRuleKey): Boolean {
    return ExtensionSettingRegistry.getRuleDefaultValue(key.id)
}

fun getEffectiveExtensionRuleValue(
    region: Region?,
    scope: GeoScope?,
    keyString: String
): Boolean {
    if (!ExtensionSettingRegistry.isRegisteredRuleKey(keyString)) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    val key = ExtensionSettingRegistry.ruleKey(keyString)
    if (region == null) {
        require(scope == null) { "scope requires region" }
    } else {
        val value = if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)
        value?.let { return it }
    }
    return getDefaultValueForRule(key)
}

private fun isPermissionKey(key: String) = runCatching { PermissionKey.valueOf(key) }.isSuccess
private fun isEffectKey(key: String) = runCatching { EffectKey.valueOf(key) }.isSuccess
private fun isRuleKey(key: String) = runCatching { RuleKey.valueOf(key) }.isSuccess
private fun isEntryExitToggleKey(key: String) = runCatching { EntryExitToggleKey.valueOf(key) }.isSuccess
private fun isEntryExitMessageKey(key: String) = runCatching { EntryExitMessageKey.valueOf(key) }.isSuccess
private fun isExtensionPermissionKey(key: String) = ExtensionSettingRegistry.isRegisteredPermissionKey(key)
private fun isExtensionRuleKey(key: String) = ExtensionSettingRegistry.isRegisteredRuleKey(key)

fun onCertificateRuleValue(
    region: Region?,
    scope: GeoScope?,
    keyString: String,
): Boolean? {
    val key = parseKey(keyString)
    if (key !is RuleKey && key !is ExtensionRuleKey) throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return null
    }
    return when (key) {
        is RuleKey -> if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)
        is ExtensionRuleKey -> if (scope == null) getRegionRuleValue(region, key) else getScopeRuleValue(region, scope, key)
    }
}

fun onQuerySettingValue(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope?,
    keyString: String,
    targetPlayerStr: String?
) {
    try {
        val key = parseKey(keyString)
        val scopeName = scope?.scopeName
        val displayTarget = if (scopeName != null) "Scope &b${scopeName}&r of Region &b${region.name}&r" else "Region &b${region.name}&r"
        when (key) {
            is RuleKey, is ExtensionRuleKey -> {
                val value = onCertificateRuleValue(region, scope, keyString)
                if (value == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
                }
            }
            is PermissionKey, is ExtensionPermissionKey -> {
                val value = onCertificatePermissionValue(player, region, scope, targetPlayerStr, keyString)
                player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
            }
            is EntryExitToggleKey -> {
                val value = (scope?.settingStore ?: region.settingStore).entryExitToggle(key)
                if (value == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
                }
            }
            is EntryExitMessageKey -> {
                val value = (scope?.settingStore ?: region.settingStore).entryExitMessage(key)
                if (value == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
                }
            }
            is EffectKey -> {
                val store = scope?.settingStore ?: region.settingStore
                val subject = if (targetPlayerStr == null) SettingSubject.Global else {
                    val uuid = resolveTargetPlayerUUID(player, targetPlayerStr) ?: return
                    SettingSubject.Player(uuid)
                }
                val value = when (subject) {
                    SettingSubject.Global -> store.globalEffect(key)
                    is SettingSubject.Player -> store.playerEffect(key, subject.uuid)
                }
                if (value == null) {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)!!)
                } else {
                    player.sendSystemMessage(Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)!!)
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        val scopeName = scope?.scopeName
        val message = when (e.message) {
            "interaction.meta.setting.error.invalid_target_player" -> Translator.tr(e.message, targetPlayerStr)
            "region.error.no_scope" -> Translator.tr(e.message, scopeName, region.name)
            else -> Translator.tr(e.message)
        }
        player.sendSystemMessage(message!!)
    }
}
