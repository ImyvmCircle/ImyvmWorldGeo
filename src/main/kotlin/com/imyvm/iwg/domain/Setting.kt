package com.imyvm.iwg.domain

import java.util.UUID

abstract class Setting(
    val playerUUID: UUID? = null
) {
    abstract val key: BaseKey
    abstract val value: Any

    val isPersonal: Boolean
        get() = playerUUID != null
}

class PermissionSetting(
    override val key: PermissionKey,
    override val value: Boolean,
    playerUUID: UUID? = null
) : Setting(playerUUID)

class EffectSetting(
    override val key: EffectKey,
    override val value: Int,
    playerUUID: UUID? = null
) : Setting(playerUUID)

class RuleSetting(
    override val key: RuleKey,
    override val value: Boolean
) : Setting(null)

interface BaseKey

enum class PermissionKey : BaseKey {
    BUILD_BREAK,
    FLY,
    CONTAINER
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