package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKeyLike
import com.imyvm.iwg.domain.component.RuleKeyLike
import com.imyvm.iwg.domain.component.SettingStore
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.api.SettingAddResult
import com.imyvm.iwg.inter.api.SettingRemoveResult

internal sealed interface SettingMutationTarget {
    val store: SettingStore

    class RegionTarget(region: Region) : SettingMutationTarget {
        init {
            RegionDatabase.requireCanonicalRegion(region)
        }

        override val store: SettingStore = region.settingStore
    }

    class ScopeTarget(region: Region, scope: GeoScope) : SettingMutationTarget {
        init {
            RegionDatabase.requireCanonicalScope(region, scope)
        }

        override val store: SettingStore = scope.settingStore
        val scopeName: String = scope.scopeName
    }
}

internal fun addPermissionSetting(
    target: SettingMutationTarget,
    key: PermissionKeyLike,
    subject: SettingSubject,
    value: Boolean,
    save: () -> Boolean
): SettingAddResult {
    validatePermissionKey(key)
    return addSettingValue(
        put = { target.store.putPermissionIfAbsent(key, subject, value) },
        rollback = { check(target.store.removePermission(key, subject) != null) },
        save = save
    )
}

internal fun removePermissionSetting(
    target: SettingMutationTarget,
    key: PermissionKeyLike,
    subject: SettingSubject,
    save: () -> Boolean
): SettingRemoveResult {
    validatePermissionKey(key)
    return removeSettingValue(
        remove = { target.store.removePermission(key, subject) },
        restore = { target.store.restorePermission(key, subject, it) },
        save = save
    )
}

internal fun addEffectSetting(
    target: SettingMutationTarget,
    key: EffectKey,
    subject: SettingSubject,
    amplifier: Int,
    save: () -> Boolean
): SettingAddResult {
    require(amplifier in 0..255) { "effect amplifier must be between 0 and 255" }
    return addSettingValue(
        put = { target.store.putEffectIfAbsent(key, subject, amplifier) },
        rollback = { check(target.store.removeEffect(key, subject) != null) },
        save = save
    )
}

internal fun removeEffectSetting(
    target: SettingMutationTarget,
    key: EffectKey,
    subject: SettingSubject,
    save: () -> Boolean
): SettingRemoveResult = removeSettingValue(
    remove = { target.store.removeEffect(key, subject) },
    restore = { target.store.restoreEffect(key, subject, it) },
    save = save
)

internal fun addRuleSetting(
    target: SettingMutationTarget,
    key: RuleKeyLike,
    value: Boolean,
    save: () -> Boolean
): SettingAddResult {
    validateRuleKey(key)
    return addSettingValue(
        put = { target.store.putRuleIfAbsent(key, value) },
        rollback = { check(target.store.removeRule(key) != null) },
        save = save
    )
}

internal fun removeRuleSetting(
    target: SettingMutationTarget,
    key: RuleKeyLike,
    save: () -> Boolean
): SettingRemoveResult {
    validateRuleKey(key)
    return removeSettingValue(
        remove = { target.store.removeRule(key) },
        restore = { target.store.restoreRule(key, it) },
        save = save
    )
}

internal fun addEntryExitToggleSetting(
    target: SettingMutationTarget,
    key: EntryExitToggleKey,
    value: Boolean,
    save: () -> Boolean
): SettingAddResult = addSettingValue(
    put = { target.store.putEntryExitToggleIfAbsent(key, value) },
    rollback = { check(target.store.removeEntryExitToggle(key) != null) },
    save = save
)

internal fun removeEntryExitToggleSetting(
    target: SettingMutationTarget,
    key: EntryExitToggleKey,
    save: () -> Boolean
): SettingRemoveResult = removeSettingValue(
    remove = { target.store.removeEntryExitToggle(key) },
    restore = { target.store.restoreEntryExitToggle(key, it) },
    save = save
)

internal fun addEntryExitMessageSetting(
    target: SettingMutationTarget,
    key: EntryExitMessageKey,
    message: String,
    save: () -> Boolean
): SettingAddResult = addSettingValue(
    put = { target.store.putEntryExitMessageIfAbsent(key, message) },
    rollback = { check(target.store.removeEntryExitMessage(key) != null) },
    save = save
)

internal fun removeEntryExitMessageSetting(
    target: SettingMutationTarget,
    key: EntryExitMessageKey,
    save: () -> Boolean
): SettingRemoveResult = removeSettingValue(
    remove = { target.store.removeEntryExitMessage(key) },
    restore = { target.store.restoreEntryExitMessage(key, it) },
    save = save
)

private inline fun addSettingValue(
    put: () -> Boolean,
    rollback: () -> Unit,
    save: () -> Boolean
): SettingAddResult {
    if (!put()) return SettingAddResult.ALREADY_EXISTS
    if (save()) return SettingAddResult.SUCCESS
    rollback()
    return SettingAddResult.PERSISTENCE_FAILED
}

private inline fun <V : Any> removeSettingValue(
    remove: () -> V?,
    restore: (V) -> Unit,
    save: () -> Boolean
): SettingRemoveResult {
    val previous = remove() ?: return SettingRemoveResult.NOT_FOUND
    if (save()) return SettingRemoveResult.SUCCESS
    restore(previous)
    return SettingRemoveResult.PERSISTENCE_FAILED
}

private fun validatePermissionKey(key: PermissionKeyLike) {
    if (key is ExtensionPermissionKey) {
        require(ExtensionSettingRegistry.isRegisteredPermissionKey(key.id)) {
            "interaction.meta.setting.error.invalid_key"
        }
    }
}

private fun validateRuleKey(key: RuleKeyLike) {
    if (key is ExtensionRuleKey) {
        require(ExtensionSettingRegistry.isRegisteredRuleKey(key.id)) {
            "interaction.meta.setting.error.invalid_key"
        }
    }
}
