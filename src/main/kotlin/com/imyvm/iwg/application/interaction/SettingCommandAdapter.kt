package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.permission.helper.getEffectiveRegionGlobalPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveRegionPlayerPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveScopeGlobalPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveScopePlayerPermissionValue
import com.imyvm.iwg.application.region.rule.helper.getRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getScopeRuleValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionKeyLike
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleKeyLike
import com.imyvm.iwg.domain.component.SettingKey
import com.imyvm.iwg.domain.component.SettingStore
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.inter.api.SettingAddResult
import com.imyvm.iwg.inter.api.SettingRemoveResult
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

internal fun addRegionSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    valueString: String,
    targetPlayerName: String?
) = addSettingFromCommand(
    player,
    SettingMutationTarget.RegionTarget(region),
    keyString,
    valueString,
    targetPlayerName
)

internal fun addScopeSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    valueString: String,
    targetPlayerName: String?
) = addSettingFromCommand(
    player,
    SettingMutationTarget.ScopeTarget(region, scope),
    keyString,
    valueString,
    targetPlayerName
)

private fun addSettingFromCommand(
    player: ServerPlayer,
    target: SettingMutationTarget,
    keyString: String,
    valueString: String,
    targetPlayerName: String?
) {
    val key = parseRegisteredSettingKeyOrNotify(player, keyString) ?: return
    if (!validatePersonalSubject(player, key, targetPlayerName)) return
    val subject = resolveSettingSubjectOrNotify(player, targetPlayerName) ?: return
    val displayValue: String
    val result = when (key) {
        is PermissionKeyLike -> {
            val value = parseBooleanSettingValueOrNotify(player, keyString, valueString) ?: return
            displayValue = value.toString()
            addPermissionSetting(target, key, subject, value) { saveRegionData(player) }
        }
        is EffectKey -> {
            val amplifier = parseEffectAmplifierOrNotify(player, keyString, valueString) ?: return
            displayValue = amplifier.toString()
            addEffectSetting(target, key, subject, amplifier) { saveRegionData(player) }
        }
        is RuleKeyLike -> {
            val value = parseBooleanSettingValueOrNotify(player, keyString, valueString) ?: return
            displayValue = value.toString()
            addRuleSetting(target, key, value) { saveRegionData(player) }
        }
        is EntryExitToggleKey -> {
            val value = parseBooleanSettingValueOrNotify(player, keyString, valueString) ?: return
            displayValue = value.toString()
            addEntryExitToggleSetting(target, key, value) { saveRegionData(player) }
        }
        is EntryExitMessageKey -> {
            displayValue = valueString
            addEntryExitMessageSetting(target, key, valueString) { saveRegionData(player) }
        }
    }
    if (result == SettingAddResult.ALREADY_EXISTS) {
        val (messageKey, scopeName) = when (target) {
            is SettingMutationTarget.RegionTarget -> {
                val duplicateKey = if (targetPlayerName == null) {
                    "interaction.meta.setting.error.region.duplicate_global"
                } else {
                    "interaction.meta.setting.error.region.duplicate_player"
                }
                duplicateKey to ""
            }
            is SettingMutationTarget.ScopeTarget -> {
                val duplicateKey = if (targetPlayerName == null) {
                    "interaction.meta.setting.error.scope.duplicate_global"
                } else {
                    "interaction.meta.setting.error.scope.duplicate_personal_player"
                }
                duplicateKey to target.scopeName
            }
        }
        player.sendSystemMessage(
            Translator.tr(messageKey, keyString, targetPlayerName ?: "", scopeName)
        )
        return
    }
    if (result == SettingAddResult.SUCCESS) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.setting.add.success", key.toString(), displayValue)
        )
    }
}

internal fun removeRegionSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    targetPlayerName: String?
) = removeSettingFromCommand(
    player,
    SettingMutationTarget.RegionTarget(region),
    keyString,
    targetPlayerName
)

internal fun removeScopeSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    targetPlayerName: String?
) = removeSettingFromCommand(
    player,
    SettingMutationTarget.ScopeTarget(region, scope),
    keyString,
    targetPlayerName
)

private fun removeSettingFromCommand(
    player: ServerPlayer,
    target: SettingMutationTarget,
    keyString: String,
    targetPlayerName: String?
) {
    val key = parseRegisteredSettingKeyOrNotify(player, keyString) ?: return
    if (!validatePersonalSubject(player, key, targetPlayerName)) return
    val subject = resolveSettingSubjectOrNotify(player, targetPlayerName) ?: return
    val result = when (key) {
        is PermissionKeyLike -> removePermissionSetting(target, key, subject) { saveRegionData(player) }
        is EffectKey -> removeEffectSetting(target, key, subject) { saveRegionData(player) }
        is RuleKeyLike -> removeRuleSetting(target, key) { saveRegionData(player) }
        is EntryExitToggleKey -> removeEntryExitToggleSetting(target, key) { saveRegionData(player) }
        is EntryExitMessageKey -> removeEntryExitMessageSetting(target, key) { saveRegionData(player) }
    }
    if (result == SettingRemoveResult.NOT_FOUND) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.setting.delete.error.no_such_setting", key.toString())
        )
        return
    }
    if (result == SettingRemoveResult.SUCCESS) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.setting.delete.success", key.toString())
        )
    }
}

internal fun queryRegionSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    targetPlayerName: String?
) = querySettingFromCommand(
    player,
    CommandSettingQueryTarget.RegionTarget(region),
    keyString,
    targetPlayerName
)

internal fun queryScopeSettingFromCommand(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    targetPlayerName: String?
) = querySettingFromCommand(
    player,
    CommandSettingQueryTarget.ScopeTarget(region, scope),
    keyString,
    targetPlayerName
)

private fun querySettingFromCommand(
    player: ServerPlayer,
    target: CommandSettingQueryTarget,
    keyString: String,
    targetPlayerName: String?
) {
    val key = parseRegisteredSettingKeyOrNotify(player, keyString) ?: return
    when (key) {
        is RuleKeyLike -> sendQueryResult(player, keyString, queryRuleValue(target, key), target.displayName)
        is PermissionKeyLike -> {
            val subject = resolveSettingSubjectOrNotify(player, targetPlayerName) ?: return
            sendQueryResult(
                player,
                keyString,
                queryPermissionValue(target, subject, key),
                target.displayName
            )
        }
        is EntryExitToggleKey ->
            sendQueryResult(player, keyString, target.store.entryExitToggle(key), target.displayName)
        is EntryExitMessageKey ->
            sendQueryResult(player, keyString, target.store.entryExitMessage(key), target.displayName)
        is EffectKey -> {
            val subject = resolveSettingSubjectOrNotify(player, targetPlayerName) ?: return
            val value = when (subject) {
                SettingSubject.Global -> target.store.globalEffect(key)
                is SettingSubject.Player -> target.store.playerEffect(key, subject.uuid)
            }
            sendQueryResult(player, keyString, value, target.displayName)
        }
    }
}

private fun queryPermissionValue(
    target: CommandSettingQueryTarget,
    subject: SettingSubject,
    key: PermissionKeyLike
): Boolean = when (target) {
    is CommandSettingQueryTarget.RegionTarget -> when (subject) {
        SettingSubject.Global -> getEffectiveRegionGlobalPermissionValue(target.region, key)
        is SettingSubject.Player -> getEffectiveRegionPlayerPermissionValue(target.region, subject.uuid, key)
    }
    is CommandSettingQueryTarget.ScopeTarget -> when (subject) {
        SettingSubject.Global -> getEffectiveScopeGlobalPermissionValue(target.region, target.scope, key)
        is SettingSubject.Player ->
            getEffectiveScopePlayerPermissionValue(target.region, target.scope, subject.uuid, key)
    }
}

private fun queryRuleValue(target: CommandSettingQueryTarget, key: RuleKeyLike): Boolean? =
    when (key) {
        is RuleKey -> when (target) {
            is CommandSettingQueryTarget.RegionTarget -> getRegionRuleValue(target.region, key)
            is CommandSettingQueryTarget.ScopeTarget -> getScopeRuleValue(target.region, target.scope, key)
        }
        is ExtensionRuleKey -> when (target) {
            is CommandSettingQueryTarget.RegionTarget -> getRegionRuleValue(target.region, key)
            is CommandSettingQueryTarget.ScopeTarget -> getScopeRuleValue(target.region, target.scope, key)
        }
    }

private fun <T> sendQueryResult(
    player: ServerPlayer,
    keyString: String,
    value: T?,
    displayTarget: String
) {
    val message = if (value == null) {
        Translator.tr("interaction.meta.setting.query.rule.not_set", keyString, displayTarget)
    } else {
        Translator.tr("interaction.meta.setting.query.result", keyString, value, displayTarget)
    }
    player.sendSystemMessage(message)
}

private fun validatePersonalSubject(
    player: ServerPlayer,
    key: SettingKey,
    targetPlayerName: String?
): Boolean {
    if (targetPlayerName == null) return true
    if (key is RuleKeyLike) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.rule_no_personal"))
        return false
    }
    if (key is EntryExitToggleKey || key is EntryExitMessageKey) {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.entry_exit_no_personal"))
        return false
    }
    return true
}

private fun resolveSettingSubjectOrNotify(
    player: ServerPlayer,
    targetPlayerName: String?
): SettingSubject? {
    if (targetPlayerName == null) return SettingSubject.Global
    val uuid = getUUIDFromPlayerName(player.level().server, targetPlayerName)
    if (uuid != null) return SettingSubject.Player(uuid)
    player.sendSystemMessage(
        Translator.tr("interaction.meta.setting.error.invalid_target_player", targetPlayerName)
    )
    return null
}

internal fun requireTargetPlayerUUID(player: ServerPlayer, targetPlayerName: String): UUID =
    getUUIDFromPlayerName(player.level().server, targetPlayerName)
        ?: throw IllegalArgumentException("interaction.meta.setting.error.invalid_target_player")

internal fun parseRegisteredSettingKeyOrNull(keyString: String): SettingKey? =
    PermissionKey.entries.firstOrNull { it.name == keyString }
        ?: EffectKey.entries.firstOrNull { it.name == keyString }
        ?: RuleKey.entries.firstOrNull { it.name == keyString }
        ?: EntryExitToggleKey.entries.firstOrNull { it.name == keyString }
        ?: EntryExitMessageKey.entries.firstOrNull { it.name == keyString }
        ?: if (ExtensionSettingRegistry.isRegisteredPermissionKey(keyString)) {
            ExtensionSettingRegistry.permissionKey(keyString)
        } else if (ExtensionSettingRegistry.isRegisteredRuleKey(keyString)) {
            ExtensionSettingRegistry.ruleKey(keyString)
        } else {
            null
        }

internal fun requireRegisteredSettingKey(keyString: String): SettingKey =
    requireNotNull(parseRegisteredSettingKeyOrNull(keyString)) {
        "interaction.meta.setting.error.invalid_key"
    }

internal fun requireRegisteredPermissionKey(keyString: String): PermissionKeyLike =
    requireRegisteredSettingKey(keyString) as? PermissionKeyLike
        ?: throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")

internal fun requireRegisteredExtensionPermissionKey(keyString: String): ExtensionPermissionKey {
    if (!ExtensionSettingRegistry.isRegisteredPermissionKey(keyString)) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    return ExtensionSettingRegistry.permissionKey(keyString)
}

internal fun requireRegisteredExtensionRuleKey(keyString: String): ExtensionRuleKey {
    if (!ExtensionSettingRegistry.isRegisteredRuleKey(keyString)) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    return ExtensionSettingRegistry.ruleKey(keyString)
}

internal fun parseBooleanSettingValue(value: String): Boolean? = value.toBooleanStrictOrNull()

internal fun parseEffectAmplifier(value: String): Int? =
    value.toIntOrNull()?.takeIf { it in 0..255 }

private fun parseRegisteredSettingKeyOrNotify(player: ServerPlayer, keyString: String): SettingKey? =
    parseRegisteredSettingKeyOrNull(keyString) ?: run {
        player.sendSystemMessage(Translator.tr("interaction.meta.setting.error.invalid_key"))
        null
    }

private fun parseBooleanSettingValueOrNotify(
    player: ServerPlayer,
    keyString: String,
    valueString: String
): Boolean? = parseBooleanSettingValue(valueString).also {
    if (it == null) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.setting.error.invalid_value_boolean", keyString, valueString)
        )
    }
}

private fun parseEffectAmplifierOrNotify(
    player: ServerPlayer,
    keyString: String,
    valueString: String
): Int? = parseEffectAmplifier(valueString).also {
    if (it == null) {
        player.sendSystemMessage(
            Translator.tr("interaction.meta.setting.error.invalid_value_int", keyString, valueString)
        )
    }
}

private sealed interface CommandSettingQueryTarget {
    val region: Region
    val store: SettingStore
    val displayName: String

    class RegionTarget(override val region: Region) : CommandSettingQueryTarget {
        override val store: SettingStore = region.settingStore
        override val displayName: String = "Region &b${region.name}&r"
    }

    class ScopeTarget(
        override val region: Region,
        val scope: GeoScope
    ) : CommandSettingQueryTarget {
        init {
            require(region.containsScope(scope)) { "scope does not belong to region" }
        }

        override val store: SettingStore = scope.settingStore
        override val displayName: String = "Scope &b${scope.scopeName}&r of Region &b${region.name}&r"
    }
}
