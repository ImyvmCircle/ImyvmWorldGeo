package com.imyvm.iwg.domain.component

enum class PermissionCategory {
    GENERAL,
    RPG
}

sealed interface PermissionEntryNotification {
    data object None : PermissionEntryNotification

    data class Restricted(val translationKey: String) : PermissionEntryNotification {
        init {
            require(translationKey.isNotBlank()) { "permission entry notification key must not be blank" }
        }
    }
}

enum class PermissionKey(
    val category: PermissionCategory,
    val parent: PermissionKey?,
    val displayTranslationKey: String,
    val entryNotification: PermissionEntryNotification
) : PermissionKeyLike {
    BUILD_BREAK(
        PermissionCategory.GENERAL, null, "permission.name.build_break", PermissionEntryNotification.None
    ),
    FLY(
        PermissionCategory.GENERAL, null, "permission.name.fly", PermissionEntryNotification.None
    ),
    INTERACTION(
        PermissionCategory.GENERAL, null, "permission.name.interaction", PermissionEntryNotification.None
    ),
    CONTAINER(
        PermissionCategory.GENERAL, INTERACTION, "permission.name.container", PermissionEntryNotification.None
    ),
    BUILD(
        PermissionCategory.GENERAL, BUILD_BREAK, "permission.name.build", PermissionEntryNotification.None
    ),
    BREAK(
        PermissionCategory.GENERAL, BUILD_BREAK, "permission.name.break", PermissionEntryNotification.None
    ),
    REDSTONE(
        PermissionCategory.GENERAL, INTERACTION, "permission.name.redstone", PermissionEntryNotification.None
    ),
    TRADE(
        PermissionCategory.GENERAL, null, "permission.name.trade", PermissionEntryNotification.None
    ),
    PVP(
        PermissionCategory.GENERAL, null, "permission.name.pvp", PermissionEntryNotification.None
    ),
    BUCKET_BUILD(
        PermissionCategory.GENERAL, BUILD, "permission.name.bucket_build", PermissionEntryNotification.None
    ),
    BUCKET_SCOOP(
        PermissionCategory.GENERAL, BREAK, "permission.name.bucket_scoop", PermissionEntryNotification.None
    ),
    ANIMAL_KILLING(
        PermissionCategory.GENERAL, null, "permission.name.animal_killing", PermissionEntryNotification.None
    ),
    VILLAGER_KILLING(
        PermissionCategory.GENERAL, null, "permission.name.villager_killing", PermissionEntryNotification.None
    ),
    THROWABLE(
        PermissionCategory.GENERAL, null, "permission.name.throwable", PermissionEntryNotification.None
    ),
    EGG_USE(
        PermissionCategory.GENERAL, THROWABLE, "permission.name.egg_use", PermissionEntryNotification.None
    ),
    SNOWBALL_USE(
        PermissionCategory.GENERAL, THROWABLE, "permission.name.snowball_use", PermissionEntryNotification.None
    ),
    POTION_USE(
        PermissionCategory.GENERAL, THROWABLE, "permission.name.potion_use", PermissionEntryNotification.None
    ),
    FARMING(
        PermissionCategory.GENERAL, null, "permission.name.farming", PermissionEntryNotification.None
    ),
    IGNITE(
        PermissionCategory.GENERAL, null, "permission.name.ignite", PermissionEntryNotification.None
    ),
    ARMOR_STAND(
        PermissionCategory.GENERAL, null, "permission.name.armor_stand", PermissionEntryNotification.None
    ),
    ITEM_FRAME(
        PermissionCategory.GENERAL, null, "permission.name.item_frame", PermissionEntryNotification.None
    ),
    WIND_CHARGE_USE(
        PermissionCategory.GENERAL, THROWABLE, "permission.name.wind_charge_use", PermissionEntryNotification.None
    ),
    RPG_ITEM_PICKUP(
        PermissionCategory.RPG,
        null,
        "permission.name.rpg_item_pickup",
        PermissionEntryNotification.Restricted("notification.rpg.item_pickup_restricted")
    ),
    RPG_BOW_SHOOT(
        PermissionCategory.RPG,
        null,
        "permission.name.rpg_bow_shoot",
        PermissionEntryNotification.Restricted("notification.rpg.bow_shoot_restricted")
    ),
    RPG_VEHICLE_USE(
        PermissionCategory.RPG,
        null,
        "permission.name.rpg_vehicle_use",
        PermissionEntryNotification.Restricted("notification.rpg.vehicle_use_restricted")
    ),
    RPG_EATING(
        PermissionCategory.RPG,
        null,
        "permission.name.rpg_eating",
        PermissionEntryNotification.Restricted("notification.rpg.eating_restricted")
    ),
    RPG_FISHING(
        PermissionCategory.RPG,
        null,
        "permission.name.rpg_fishing",
        PermissionEntryNotification.Restricted("notification.rpg.fishing_restricted")
    );

    init {
        require(displayTranslationKey.isNotBlank()) { "permission display translation key must not be blank" }
    }
}
