package com.imyvm.iwg.domain.component

import java.util.UUID

internal sealed interface SettingSubject {
    data object Global : SettingSubject
    data class Player(val uuid: UUID) : SettingSubject
}

internal class SettingStore(settings: Iterable<Setting> = emptyList()) {
    private class SubjectValues<T> {
        var global: T? = null
        val players = linkedMapOf<UUID, T>()
        fun isEmpty(): Boolean = global == null && players.isEmpty()
    }

    private val permissions = linkedMapOf<PermissionKeyLike, SubjectValues<Boolean>>()
    private val effects = linkedMapOf<EffectKey, SubjectValues<Int>>()
    private val rules = linkedMapOf<RuleKeyLike, Boolean>()
    private val entryExitToggles = linkedMapOf<EntryExitToggleKey, Boolean>()
    private val entryExitMessages = linkedMapOf<EntryExitMessageKey, String>()

    init {
        replaceAll(settings)
    }

    fun globalPermission(key: PermissionKeyLike): Boolean? = permissions[key]?.global
    fun playerPermission(key: PermissionKeyLike, uuid: UUID): Boolean? = permissions[key]?.players?.get(uuid)
    fun globalEffect(key: EffectKey): Int? = effects[key]?.global
    fun playerEffect(key: EffectKey, uuid: UUID): Int? = effects[key]?.players?.get(uuid)
    fun rule(key: RuleKeyLike): Boolean? = rules[key]
    fun entryExitToggle(key: EntryExitToggleKey): Boolean? = entryExitToggles[key]
    fun entryExitMessage(key: EntryExitMessageKey): String? = entryExitMessages[key]
    fun effectKeys(): Set<EffectKey> = effects.keys
    fun builtInRuleKeys(): Set<RuleKey> = rules.keys.filterIsInstanceTo(linkedSetOf())

    fun put(setting: Setting) {
        val subject = setting.playerUUID?.let(SettingSubject::Player) ?: SettingSubject.Global
        when (setting) {
            is PermissionSetting -> putSubjectValue(permissions, setting.key, subject, setting.value)
            is ExtensionPermissionSetting -> putSubjectValue(permissions, setting.key, subject, setting.value)
            is EffectSetting -> putSubjectValue(effects, setting.key, subject, setting.value)
            is RuleSetting -> rules[setting.key] = setting.value
            is ExtensionRuleSetting -> rules[setting.key] = setting.value
            is EntryExitToggleSetting -> entryExitToggles[setting.key] = setting.value
            is EntryExitMessageSetting -> entryExitMessages[setting.key] = setting.value
            else -> throw IllegalArgumentException("Unsupported setting type: ${setting.javaClass.name}")
        }
    }

    fun remove(key: SettingKey, subject: SettingSubject): Boolean = when (key) {
        is PermissionKeyLike -> removeSubjectValue(permissions, key, subject)
        is EffectKey -> removeSubjectValue(effects, key, subject)
        is RuleKeyLike -> rules.remove(key) != null
        is EntryExitToggleKey -> entryExitToggles.remove(key) != null
        is EntryExitMessageKey -> entryExitMessages.remove(key) != null
    }

    fun contains(key: SettingKey, subject: SettingSubject): Boolean = when (key) {
        is PermissionKeyLike -> containsSubjectValue(permissions[key], subject)
        is EffectKey -> containsSubjectValue(effects[key], subject)
        is RuleKeyLike -> rules.containsKey(key)
        is EntryExitToggleKey -> entryExitToggles.containsKey(key)
        is EntryExitMessageKey -> entryExitMessages.containsKey(key)
    }

    fun replaceAll(settings: Iterable<Setting>) {
        val validated = settings.toList()
        val identities = hashSetOf<Pair<SettingKey, SettingSubject>>()
        validated.forEach { setting ->
            val subject = setting.playerUUID?.let(SettingSubject::Player) ?: SettingSubject.Global
            require(identities.add(settingKey(setting) to subject)) { "duplicate setting identity" }
        }
        clear()
        validated.forEach(::put)
    }

    fun toLegacyList(): List<Setting> = buildList {
        permissions.forEach { (key, values) ->
            values.global?.let { value -> add(permissionSetting(key, value, null)) }
            values.players.forEach { (uuid, value) -> add(permissionSetting(key, value, uuid)) }
        }
        effects.forEach { (key, values) ->
            values.global?.let { value -> add(EffectSetting(key, value)) }
            values.players.forEach { (uuid, value) -> add(EffectSetting(key, value, uuid)) }
        }
        rules.forEach { (key, value) ->
            add(when (key) {
                is RuleKey -> RuleSetting(key, value)
                is ExtensionRuleKey -> ExtensionRuleSetting(key, value)
            })
        }
        entryExitToggles.forEach { (key, value) -> add(EntryExitToggleSetting(key, value)) }
        entryExitMessages.forEach { (key, value) -> add(EntryExitMessageSetting(key, value)) }
    }

    private fun clear() {
        permissions.clear()
        effects.clear()
        rules.clear()
        entryExitToggles.clear()
        entryExitMessages.clear()
    }

    private fun settingKey(setting: Setting): SettingKey = when (setting) {
        is PermissionSetting -> setting.key
        is ExtensionPermissionSetting -> setting.key
        is EffectSetting -> setting.key
        is RuleSetting -> setting.key
        is ExtensionRuleSetting -> setting.key
        is EntryExitToggleSetting -> setting.key
        is EntryExitMessageSetting -> setting.key
        else -> throw IllegalArgumentException("Unsupported setting type: ${setting.javaClass.name}")
    }

    private fun permissionSetting(key: PermissionKeyLike, value: Boolean, uuid: UUID?): Setting = when (key) {
        is PermissionKey -> PermissionSetting(key, value, uuid)
        is ExtensionPermissionKey -> ExtensionPermissionSetting(key, value, uuid)
    }

    private fun <K, V> putSubjectValue(
        map: MutableMap<K, SubjectValues<V>>,
        key: K,
        subject: SettingSubject,
        value: V
    ) {
        val values = map.getOrPut(key) { SubjectValues() }
        when (subject) {
            SettingSubject.Global -> values.global = value
            is SettingSubject.Player -> values.players[subject.uuid] = value
        }
    }

    private fun <K, V> removeSubjectValue(
        map: MutableMap<K, SubjectValues<V>>,
        key: K,
        subject: SettingSubject
    ): Boolean {
        val values = map[key] ?: return false
        val removed = when (subject) {
            SettingSubject.Global -> (values.global != null).also { values.global = null }
            is SettingSubject.Player -> values.players.remove(subject.uuid) != null
        }
        if (values.isEmpty()) map.remove(key)
        return removed
    }

    private fun <V> containsSubjectValue(values: SubjectValues<V>?, subject: SettingSubject): Boolean = when (subject) {
        SettingSubject.Global -> values?.global != null
        is SettingSubject.Player -> values?.players?.containsKey(subject.uuid) == true
    }
}
