package com.imyvm.iwg.domain

import java.util.UUID

abstract class Setting(
    val isPersonal: Boolean = false,
    val playerUUID: UUID? = null
    ){
    abstract val key: BaseKey
    abstract val value: Any
}

class PermissionSetting(
    override val key: PermissionKey,
    override val value: Boolean,
    isPersonal: Boolean = false,
    playerUUID: UUID? = null
) : Setting(isPersonal, playerUUID)

class EffectSetting(
    override val key: EffectKey,
    override val value: Int,
    isPersonal: Boolean = false,
    playerUUID: UUID? = null
) : Setting(isPersonal, playerUUID)

class RuleSetting(
    override val key: RuleKey,
    override val value: Boolean
) : Setting(false, null)

interface BaseKey

enum class PermissionKey : BaseKey {
    BUILD_BREAK,
    FLY
}

enum class EffectKey : BaseKey {
    SPEED,
    JUMP,
    DAMAGE_RESISTANCE
}

enum class RuleKey : BaseKey {
    SPAWN_MONSTERS,
    FIRE_SPREAD,
    PVP_ALLOWED
}