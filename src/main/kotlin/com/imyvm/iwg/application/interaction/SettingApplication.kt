package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.permission.helper.getEffectiveRegionGlobalPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveRegionPlayerPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveScopeGlobalPermissionValue
import com.imyvm.iwg.application.region.permission.helper.getEffectiveScopePlayerPermissionValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getEffectiveScopeRuleValue
import com.imyvm.iwg.application.region.rule.helper.getRegionRuleValue
import com.imyvm.iwg.application.region.rule.helper.getScopeRuleValue
import com.imyvm.iwg.application.region.setting.defaultPermissionValue
import com.imyvm.iwg.application.region.setting.defaultRuleValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleKeyLike
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Compatibility entry point retained at its historical JVM owner.
 *
 * Commands use the explicit Region/Scope command adapters.
 */
fun addRegionSetting(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) {
    if (valueString == null) return
    addRegionSettingFromCommand(player, region, keyString, valueString, targetPlayerStr)
}

/**
 * Compatibility entry point retained at its historical JVM owner.
 *
 * Commands use the explicit Region/Scope command adapters.
 */
fun addScopeSetting(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    valueString: String?,
    targetPlayerStr: String?
) {
    if (valueString == null) return
    addScopeSettingFromCommand(player, region, scope, keyString, valueString, targetPlayerStr)
}

/** Compatibility entry point retained at its historical JVM owner. */
fun removeRegionSetting(
    player: ServerPlayer,
    region: Region,
    keyString: String,
    targetPlayerStr: String?
) = removeRegionSettingFromCommand(player, region, keyString, targetPlayerStr)

/** Compatibility entry point retained at its historical JVM owner. */
fun removeScopeSetting(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope,
    keyString: String,
    targetPlayerStr: String?
) = removeScopeSettingFromCommand(player, region, scope, keyString, targetPlayerStr)

/** Compatibility dispatcher retained for existing string-based callers. */
fun onCertificatePermissionValue(
    playerExecutor: ServerPlayer,
    region: Region,
    scope: GeoScope?,
    targetPlayerNameStr: String?,
    keyString: String,
): Boolean {
    val key = requireRegisteredPermissionKey(keyString)
    val playerUUID = targetPlayerNameStr?.let { requireTargetPlayerUUID(playerExecutor, it) }
    return when {
        scope != null && playerUUID != null ->
            getEffectiveScopePlayerPermissionValue(region, scope, playerUUID, key)
        scope != null -> getEffectiveScopeGlobalPermissionValue(region, scope, key)
        playerUUID != null -> getEffectiveRegionPlayerPermissionValue(region, playerUUID, key)
        else -> getEffectiveRegionGlobalPermissionValue(region, key)
    }
}

/** Compatibility wrapper. Prefer `RegionDataApi.getRegionGlobalPermissionValue`. */
fun getRegionPermissionValue(region: Region, key: PermissionKey): Boolean =
    getEffectiveRegionGlobalPermissionValue(region, key)

/** Compatibility wrapper. Prefer `RegionDataApi.getRegionPlayerPermissionValue`. */
fun getRegionPermissionValue(region: Region, playerUuid: UUID, key: PermissionKey): Boolean =
    getEffectiveRegionPlayerPermissionValue(region, playerUuid, key)

/** Compatibility wrapper. Prefer `RegionDataApi.getScopeGlobalPermissionValue`. */
fun getScopePermissionValue(region: Region, scope: GeoScope, key: PermissionKey): Boolean =
    getEffectiveScopeGlobalPermissionValue(region, scope, key)

/** Compatibility wrapper. Prefer `RegionDataApi.getScopePlayerPermissionValue`. */
fun getScopePermissionValue(region: Region, scope: GeoScope, playerUuid: UUID, key: PermissionKey): Boolean =
    getEffectiveScopePlayerPermissionValue(region, scope, playerUuid, key)

/** Compatibility wrapper. Prefer the explicit extension permission methods on `RegionDataApi`. */
fun getRegionPermissionValue(region: Region, key: ExtensionPermissionKey): Boolean =
    getEffectiveRegionGlobalPermissionValue(region, key)

/** Compatibility wrapper. Prefer the explicit extension permission methods on `RegionDataApi`. */
fun getRegionPermissionValue(region: Region, playerUuid: UUID, key: ExtensionPermissionKey): Boolean =
    getEffectiveRegionPlayerPermissionValue(region, playerUuid, key)

/** Compatibility wrapper. Prefer the explicit extension permission methods on `RegionDataApi`. */
fun getScopePermissionValue(region: Region, scope: GeoScope, key: ExtensionPermissionKey): Boolean =
    getEffectiveScopeGlobalPermissionValue(region, scope, key)

/** Compatibility wrapper. Prefer the explicit extension permission methods on `RegionDataApi`. */
fun getScopePermissionValue(
    region: Region,
    scope: GeoScope,
    playerUuid: UUID,
    key: ExtensionPermissionKey
): Boolean = getEffectiveScopePlayerPermissionValue(region, scope, playerUuid, key)

/** Compatibility dispatcher retained for existing nullable extension permission callers. */
fun onCertificateExtensionPermissionValue(
    region: Region?,
    scope: GeoScope?,
    playerUuid: UUID?,
    keyString: String
): Boolean {
    val key = requireRegisteredExtensionPermissionKey(keyString)
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return defaultPermissionValue(key)
    }
    return when {
        scope != null && playerUuid != null ->
            getEffectiveScopePlayerPermissionValue(region, scope, playerUuid, key)
        scope != null -> getEffectiveScopeGlobalPermissionValue(region, scope, key)
        playerUuid != null -> getEffectiveRegionPlayerPermissionValue(region, playerUuid, key)
        else -> getEffectiveRegionGlobalPermissionValue(region, key)
    }
}

/** Compatibility wrapper retained at its historical JVM owner. */
internal fun getDefaultValueForPermission(key: PermissionKey): Boolean =
    defaultPermissionValue(key)

/** Compatibility wrapper retained at its historical JVM owner. */
internal fun getDefaultValueForPermission(key: ExtensionPermissionKey): Boolean =
    defaultPermissionValue(key)

/** Compatibility wrapper retained at its historical JVM owner. */
fun getDefaultValueForRule(key: RuleKey): Boolean =
    defaultRuleValue(key)

/** Compatibility dispatcher retained for existing nullable extension rule callers. */
fun getEffectiveExtensionRuleValue(
    region: Region?,
    scope: GeoScope?,
    keyString: String
): Boolean {
    val key = requireRegisteredExtensionRuleKey(keyString)
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return defaultRuleValue(key)
    }
    return if (scope == null) {
        getEffectiveRegionRuleValue(region, key)
    } else {
        getEffectiveScopeRuleValue(region, scope, key)
    }
}

/** Compatibility dispatcher retained for existing nullable string rule callers. */
fun onCertificateRuleValue(
    region: Region?,
    scope: GeoScope?,
    keyString: String,
): Boolean? {
    val key = requireRegisteredSettingKey(keyString)
    if (key !is RuleKeyLike) {
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    if (region == null) {
        require(scope == null) { "scope requires region" }
        return null
    }
    return when (key) {
        is RuleKey -> if (scope == null) {
            getRegionRuleValue(region, key)
        } else {
            getScopeRuleValue(region, scope, key)
        }
        is ExtensionRuleKey -> if (scope == null) {
            getRegionRuleValue(region, key)
        } else {
            getScopeRuleValue(region, scope, key)
        }
    }
}

/** Compatibility dispatcher retained for existing nullable Scope query callers. */
fun onQuerySettingValue(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope?,
    keyString: String,
    targetPlayerStr: String?
) {
    if (scope == null) {
        queryRegionSettingFromCommand(player, region, keyString, targetPlayerStr)
    } else {
        queryScopeSettingFromCommand(player, region, scope, keyString, targetPlayerStr)
    }
}
