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
    INTERACTION,
    CONTAINER(INTERACTION),
    BUILD(BUILD_BREAK),
    BREAK(BUILD_BREAK),
    REDSTONE(INTERACTION),
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
    FARMING,
    IGNITE,
    ARMOR_STAND,
    ITEM_FRAME
}

enum class EffectKey(val effectId: String) : BaseKey {
    SPEED("speed"),
    JUMP("jump_boost"),
    DAMAGE_RESISTANCE("resistance"),
    SLOWNESS("slowness"),
    HASTE("haste"),
    MINING_FATIGUE("mining_fatigue"),
    STRENGTH("strength"),
    INSTANT_HEALTH("instant_health"),
    INSTANT_DAMAGE("instant_damage"),
    NAUSEA("nausea"),
    REGENERATION("regeneration"),
    FIRE_RESISTANCE("fire_resistance"),
    WATER_BREATHING("water_breathing"),
    INVISIBILITY("invisibility"),
    BLINDNESS("blindness"),
    NIGHT_VISION("night_vision"),
    HUNGER("hunger"),
    WEAKNESS("weakness"),
    POISON("poison"),
    WITHER("wither"),
    HEALTH_BOOST("health_boost"),
    ABSORPTION("absorption"),
    SATURATION("saturation"),
    GLOWING("glowing"),
    LEVITATION("levitation"),
    LUCK("luck"),
    UNLUCK("unluck"),
    SLOW_FALLING("slow_falling"),
    CONDUIT_POWER("conduit_power"),
    DOLPHINS_GRACE("dolphins_grace"),
    BAD_OMEN("bad_omen"),
    HERO_OF_THE_VILLAGE("hero_of_the_village"),
    DARKNESS("darkness"),
    TRIAL_OMEN("trial_omen"),
    RAID_OMEN("raid_omen"),
    WIND_CHARGED("wind_charged"),
    WEAVING("weaving"),
    OOZING("oozing"),
    INFESTED("infested")
}

enum class RuleKey : BaseKey {
    SPAWN_MONSTERS,
    SPAWN_PHANTOMS,
    TNT_BLOCK_PROTECTION
}