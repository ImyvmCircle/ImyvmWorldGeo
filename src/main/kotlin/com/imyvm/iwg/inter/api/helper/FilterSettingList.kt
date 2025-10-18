package com.imyvm.iwg.inter.api.helper

import com.imyvm.iwg.domain.*
import java.util.*

fun filterSettingsByType(
    settings: List<Setting>,
    settingTypes: SettingTypes,
    isPersonal: Boolean,
    playerUUID: UUID? = null
): List<Setting> {
    val settingsSet = settings.toSet()
    return when (settingTypes) {
        SettingTypes.PERMISSION -> settingsSet.filterIsInstance<PermissionSetting>()
        SettingTypes.EFFECT -> settingsSet.filterIsInstance<EffectSetting>()
        SettingTypes.RULE -> settingsSet.filterIsInstance<RuleSetting>()
    }.filter { setting ->
        setting.isPersonal == isPersonal && (playerUUID == null || setting.playerUUID == playerUUID)
    }
}