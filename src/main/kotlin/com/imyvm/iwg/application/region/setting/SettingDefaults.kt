package com.imyvm.iwg.application.region.setting

import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionKeyLike
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleKeyLike
import com.imyvm.iwg.infra.config.PermissionConfig
import com.imyvm.iwg.infra.config.RuleConfig

internal fun defaultPermissionValue(key: PermissionKeyLike): Boolean = when (key) {
    is PermissionKey -> when (key) {
        PermissionKey.BUILD_BREAK -> PermissionConfig.PERMISSION_DEFAULT_BUILD_BREAK.value
        PermissionKey.INTERACTION -> PermissionConfig.PERMISSION_DEFAULT_INTERACTION.value
        PermissionKey.CONTAINER -> PermissionConfig.PERMISSION_DEFAULT_CONTAINER.value
        PermissionKey.FLY -> PermissionConfig.PERMISSION_DEFAULT_FLY.value
        PermissionKey.BUILD -> PermissionConfig.PERMISSION_DEFAULT_BUILD.value
        PermissionKey.BREAK -> PermissionConfig.PERMISSION_DEFAULT_BREAK.value
        PermissionKey.REDSTONE -> PermissionConfig.PERMISSION_DEFAULT_REDSTONE.value
        PermissionKey.TRADE -> PermissionConfig.PERMISSION_DEFAULT_TRADE.value
        PermissionKey.PVP -> PermissionConfig.PERMISSION_DEFAULT_PVP.value
        PermissionKey.BUCKET_BUILD -> PermissionConfig.PERMISSION_DEFAULT_BUCKET_BUILD.value
        PermissionKey.BUCKET_SCOOP -> PermissionConfig.PERMISSION_DEFAULT_BUCKET_SCOOP.value
        PermissionKey.ANIMAL_KILLING -> PermissionConfig.PERMISSION_DEFAULT_ANIMAL_KILLING.value
        PermissionKey.VILLAGER_KILLING -> PermissionConfig.PERMISSION_DEFAULT_VILLAGER_KILLING.value
        PermissionKey.EGG_USE -> PermissionConfig.PERMISSION_DEFAULT_EGG_USE.value
        PermissionKey.THROWABLE -> PermissionConfig.PERMISSION_DEFAULT_THROWABLE.value
        PermissionKey.SNOWBALL_USE -> PermissionConfig.PERMISSION_DEFAULT_SNOWBALL_USE.value
        PermissionKey.POTION_USE -> PermissionConfig.PERMISSION_DEFAULT_POTION_USE.value
        PermissionKey.FARMING -> PermissionConfig.PERMISSION_DEFAULT_FARMING.value
        PermissionKey.IGNITE -> PermissionConfig.PERMISSION_DEFAULT_IGNITE.value
        PermissionKey.ARMOR_STAND -> PermissionConfig.PERMISSION_DEFAULT_ARMOR_STAND.value
        PermissionKey.ITEM_FRAME -> PermissionConfig.PERMISSION_DEFAULT_ITEM_FRAME.value
        PermissionKey.WIND_CHARGE_USE -> PermissionConfig.PERMISSION_DEFAULT_WIND_CHARGE_USE.value
        PermissionKey.RPG_ITEM_PICKUP -> PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP.value
        PermissionKey.RPG_BOW_SHOOT -> PermissionConfig.PERMISSION_DEFAULT_RPG_BOW_SHOOT.value
        PermissionKey.RPG_VEHICLE_USE -> PermissionConfig.PERMISSION_DEFAULT_RPG_VEHICLE_USE.value
        PermissionKey.RPG_EATING -> PermissionConfig.PERMISSION_DEFAULT_RPG_EATING.value
        PermissionKey.RPG_FISHING -> PermissionConfig.PERMISSION_DEFAULT_RPG_FISHING.value
    }
    is ExtensionPermissionKey -> ExtensionSettingRegistry.getPermissionDefaultValue(key.id)
}

internal fun defaultRuleValue(key: RuleKeyLike): Boolean = when (key) {
    is RuleKey -> when (key) {
        RuleKey.SPAWN_MONSTERS -> RuleConfig.RULE_DEFAULT_SPAWN_MONSTERS.value
        RuleKey.SPAWN_PHANTOMS -> RuleConfig.RULE_DEFAULT_SPAWN_PHANTOMS.value
        RuleKey.TNT_BLOCK_PROTECTION -> RuleConfig.RULE_DEFAULT_TNT_BLOCK_PROTECTION.value
        RuleKey.ENDERMAN_BLOCK_PICKUP -> RuleConfig.RULE_DEFAULT_ENDERMAN_BLOCK_PICKUP.value
        RuleKey.SCULK_SPREAD -> RuleConfig.RULE_DEFAULT_SCULK_SPREAD.value
        RuleKey.SNOW_GOLEM_TRAIL -> RuleConfig.RULE_DEFAULT_SNOW_GOLEM_TRAIL.value
        RuleKey.DISPENSER -> RuleConfig.RULE_DEFAULT_DISPENSER.value
        RuleKey.PRESSURE_PLATE -> RuleConfig.RULE_DEFAULT_PRESSURE_PLATE.value
        RuleKey.PISTON -> RuleConfig.RULE_DEFAULT_PISTON.value
        RuleKey.RPG_NATURAL_REGEN -> RuleConfig.RULE_DEFAULT_RPG_NATURAL_REGEN.value
        RuleKey.RPG_FIRE_SPREAD -> RuleConfig.RULE_DEFAULT_RPG_FIRE_SPREAD.value
        RuleKey.RPG_HUNGER -> RuleConfig.RULE_DEFAULT_RPG_HUNGER.value
    }
    is ExtensionRuleKey -> ExtensionSettingRegistry.getRuleDefaultValue(key.id)
}
