package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.typesafe.config.Config

object PermissionConfig {
    @JvmField
    val PERMISSION_DEFAULT_BUILD_BREAK = Option("core.permission.build_break.default", true,
        "the default build/break permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_BUILD = Option("core.permission.build.default", true,
        "the default build (block placement) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_BREAK = Option("core.permission.break.default", true,
        "the default break (block breaking) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_BUCKET_BUILD = Option("core.permission.bucket_build.default", true,
        "the default bucket build (placing fluid from a non-empty bucket) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_BUCKET_SCOOP = Option("core.permission.bucket_scoop.default", true,
        "the default bucket scoop (collecting fluid or creatures with an empty bucket) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_INTERACTION = Option("core.permission.interaction.default", true,
        "the default interaction (state-changing block interactions: doors, trapdoors, fence gates, cauldrons, composters, lecterns, chiseled bookshelves, cakes, flower pots, jukeboxes, respawn anchors, beehives, vaults, and dragon eggs) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_CONTAINER = Option("core.permission.container.default", true,
        "the default container interaction permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_REDSTONE = Option("core.permission.redstone.default", true,
        "the default redstone device interaction permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_TRADE = Option("core.permission.trade.default", true,
        "the default trade (villager/merchant interaction) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_PVP = Option("core.permission.pvp.default", true,
        "the default PVP (player vs player damage) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_FLY = Option("core.permission.fly.default", false,
        "the default fly permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS = Option("core.permission.fly.disable_countdown_seconds", 5,
        "the countdown time in seconds before disabling fly when the player leaves a region that allows flying.") { obj: Config, path: String? -> obj.getInt(path) }

    @JvmField
    val PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS = Option("core.permission.fly.disable_fall_immunity_seconds", 5,
        "the duration in seconds of fall damage immunity after fly is disabled.") { obj: Config, path: String? -> obj.getInt(path) }

    @JvmField
    val PERMISSION_DEFAULT_ANIMAL_KILLING = Option("core.permission.animal_killing.default", true,
        "the default animal killing (damage to passive animals) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_VILLAGER_KILLING = Option("core.permission.villager_killing.default", true,
        "the default villager killing (damage to villagers) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_THROWABLE = Option("core.permission.throwable.default", true,
        "the default throwable (throwing projectile items such as eggs and snowballs) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_EGG_USE = Option("core.permission.egg_use.default", true,
        "the default egg use (throwing eggs) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_SNOWBALL_USE = Option("core.permission.snowball_use.default", true,
        "the default snowball use (throwing snowballs) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_POTION_USE = Option("core.permission.potion_use.default", true,
        "the default potion use (throwing splash and lingering potions) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_FARMING = Option("core.permission.farming.default", true,
        "the default farming (breaking and planting crops on farmland) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_IGNITE = Option("core.permission.ignite.default", true,
        "the default ignite (using flint and steel or fire charges to ignite blocks and entities, including TNT and creepers) permission in regions. Independent of BUILD and BUILD_BREAK.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_ARMOR_STAND = Option("core.permission.armor_stand.default", true,
        "the default armor stand permission (placing armor stand items, breaking armor stand entities, and interacting with their equipment slots) in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_ITEM_FRAME = Option("core.permission.item_frame.default", true,
        "the default item frame permission (placing item frame and glow item frame entities, breaking them, and interacting with their held items) in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }

    @JvmField
    val PERMISSION_DEFAULT_WIND_CHARGE_USE = Option("core.permission.wind_charge_use.default", true,
        "the default wind charge use (throwing wind charges) permission in regions.") { obj: Config, path: String? -> obj.getBoolean(path) }
}
