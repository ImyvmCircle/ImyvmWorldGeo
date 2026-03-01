package com.imyvm.iwg.domain.component

import java.util.UUID

abstract class Setting(
    val playerUUID: UUID? = null
) {
    abstract val key: BaseKey
    abstract val value: Any

    val isPersonal: Boolean
        get() = playerUUID != null
}

enum class SettingTypes{
    PERMISSION,
    EFFECT,
    RULE
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

enum class PermissionKey(val parent: PermissionKey? = null) : BaseKey {
    BUILD_BREAK,
    FLY,
    CONTAINER,
    BUILD(BUILD_BREAK),
    BREAK(BUILD_BREAK),
    TOGGLE,
    REDSTONE(TOGGLE),
    TRADE,
    PVP,
    BUCKET_BUILD(BUILD),
    BUCKET_SCOOP(BREAK),
    ANIMAL_KILLING,
    VILLAGER_KILLING,
    THROWABLE,
    EGG_USE(THROWABLE),
    SNOWBALL_USE(THROWABLE),
    POTION_USE(THROWABLE),
    FARMING
}

enum class EffectKey : BaseKey {
    SPEED,
    JUMP,
    DAMAGE_RESISTANCE
}

enum class RuleKey : BaseKey {
    SPAWN_MONSTERS,
    SPAWN_PHANTOMS
}