package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.TeleportConfig
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.phys.Vec3

fun onAddingTeleportPoint(
    playerExecutor: ServerPlayer,
    targetRegion: Region,
    geoScope: GeoScope,
    x: Int,
    y: Int,
    z: Int
): Int {
    RegionDatabase.requireCanonicalScope(targetRegion, geoScope)
    val targetWorld = resolveScopeWorldOrReport(playerExecutor, targetRegion, geoScope) ?: return 0
    val teleportPoint = BlockPos(x, y, z)
    val reasonKey = geoScope.getTeleportPointInvalidReasonKey(targetWorld, teleportPoint)

    return if (reasonKey == null) {
        val oldPoint = geoScope.teleportPoint
        geoScope.updateTeleportPoint(teleportPoint)
        if (!saveRegionData(playerExecutor)) {
            geoScope.updateTeleportPoint(oldPoint)
            return 0
        }
        playerExecutor.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.added", x, y, z, geoScope.scopeName, targetRegion.name)!!)
        1
    } else {
        playerExecutor.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.invalid", x, y, z, geoScope.scopeName, targetRegion.name)!!)
        playerExecutor.sendSystemMessage(Translator.tr(reasonKey, x, y, z)!!)
        0
    }
}

fun onResettingTeleportPoint(
    playerExecutor: ServerPlayer,
    region: Region,
    scope: GeoScope
): Int {
    RegionDatabase.requireCanonicalScope(region, scope)
    val oldPoint = scope.teleportPoint
    scope.updateTeleportPoint(null)
    if (!saveRegionData(playerExecutor)) {
        scope.updateTeleportPoint(oldPoint)
        return 0
    }
    playerExecutor.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.reset", scope.scopeName, region.name)!!)
    return 1
}

fun onGettingTeleportPoint(
    geoScope: GeoScope
): BlockPos? {
    return geoScope.teleportPoint
}

fun onTeleportingPlayer(
    playerExecutor: ServerPlayer,
    targetRegion: Region,
    geoScope: GeoScope
): Int {
    RegionDatabase.requireCanonicalScope(targetRegion, geoScope)
    if (!isTeleportPointPubliclyAccessible(geoScope)) {
        playerExecutor.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.private",
            geoScope.scopeName,
            targetRegion.name
        )!!)
        return 0
    }
    return teleportPlayerToCanonicalScope(playerExecutor, targetRegion, geoScope)
}

fun onTeleportingPlayerAsAdministrator(
    playerExecutor: ServerPlayer,
    targetRegion: Region,
    geoScope: GeoScope
): Int {
    RegionDatabase.requireCanonicalScope(targetRegion, geoScope)
    return teleportPlayerToCanonicalScope(playerExecutor, targetRegion, geoScope)
}

private fun teleportPlayerToCanonicalScope(
    playerExecutor: ServerPlayer,
    targetRegion: Region,
    geoScope: GeoScope
): Int {
    val targetWorld = resolveScopeWorldOrReport(playerExecutor, targetRegion, geoScope) ?: return 0
    val teleportPoint = onGettingTeleportPoint(geoScope)

    if (teleportPoint == null) {
        playerExecutor.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.null",
            geoScope.scopeName,
            targetRegion.name)!!)
        return 0
    }

    val reasonKey = geoScope.getTeleportPointInvalidReasonKey(targetWorld, teleportPoint)
    if (reasonKey == null) {
        playerExecutor.teleport(TeleportTransition(
            targetWorld,
            Vec3(teleportPoint.x.toDouble() + 0.5, teleportPoint.y.toDouble(), teleportPoint.z.toDouble() + 0.5),
            Vec3.ZERO, playerExecutor.yRot, playerExecutor.xRot, TeleportTransition.DO_NOTHING
        ))
        playerExecutor.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.teleported",
            geoScope.scopeName,
            targetRegion.name)!!)
        return 1
    }

    val searchRadius = TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value
    val fallback = geoScope.findNearestValidTeleportPoint(targetWorld, teleportPoint, searchRadius)

    return if (fallback != null) {
        geoScope.updateTeleportPoint(fallback)
        if (!saveRegionData(playerExecutor)) {
            geoScope.updateTeleportPoint(teleportPoint)
            return 0
        }
        playerExecutor.teleport(TeleportTransition(
            targetWorld,
            Vec3(fallback.x.toDouble() + 0.5, fallback.y.toDouble(), fallback.z.toDouble() + 0.5),
            Vec3.ZERO, playerExecutor.yRot, playerExecutor.xRot, TeleportTransition.DO_NOTHING
        ))
        playerExecutor.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.unsafe_updated",
            geoScope.scopeName,
            targetRegion.name,
            teleportPoint.x, teleportPoint.y, teleportPoint.z,
            fallback.x, fallback.y, fallback.z
        )!!)
        playerExecutor.sendSystemMessage(Translator.tr(reasonKey, teleportPoint.x, teleportPoint.y, teleportPoint.z)!!)
        1
    } else {
        playerExecutor.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.unsafe_no_fallback",
            geoScope.scopeName,
            targetRegion.name,
            teleportPoint.x, teleportPoint.y, teleportPoint.z
        )!!)
        playerExecutor.sendSystemMessage(Translator.tr(reasonKey, teleportPoint.x, teleportPoint.y, teleportPoint.z)!!)
        0
    }
}

internal fun isTeleportPointPubliclyAccessible(scope: GeoScope): Boolean =
    scope.isTeleportPointPublic

internal fun findPublicTeleportScope(region: Region): GeoScope? =
    region.scopes.firstOrNull { isTeleportPointPubliclyAccessible(it) && it.teleportPoint != null }

private fun resolveScopeWorldOrReport(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope
) = scope.getWorld(player.level().server).also { world ->
    if (world == null) {
        player.sendSystemMessage(Translator.tr(
            "interaction.meta.scope.teleport_point.dimension_unavailable",
            scope.worldId,
            scope.scopeName,
            region.name
        )!!)
    }
}

fun onTogglingTeleportPointAccessibility(
    player: ServerPlayer,
    region: Region,
    scope: GeoScope
): Int = toggleTeleportPointAccessibility(region, scope) { saveRegionData(player) }

/**
 * Compatibility entry point for the former Scope-only API.
 *
 * The Scope must be the canonical object currently owned by RegionDatabase. A detached copy,
 * orphan, or unassigned Scope is rejected because its owner cannot be safely inferred.
 */
@Deprecated("Use onTogglingTeleportPointAccessibility(player, region, scope)")
@JvmOverloads
fun onTogglingTeleportPointAccessibility(
    scope: GeoScope,
    player: ServerPlayer? = null
): Int {
    val region = resolveTeleportAccessibilityOwner(scope) ?: return 0
    return toggleTeleportPointAccessibility(region, scope) { saveRegionData(player) }
}

internal fun toggleTeleportPointAccessibility(
    region: Region,
    scope: GeoScope,
    findScope: (AssignedScopeId) -> Pair<Region, GeoScope>? = RegionDatabase::getScopeByAssignedId,
    save: () -> Boolean
): Int {
    require(resolveTeleportAccessibilityOwner(scope, findScope) === region) {
        "region and scope must be canonical database objects"
    }
    val oldValue = scope.isTeleportPointPublic
    scope.setTeleportPointPublic(!oldValue)
    if (!save()) {
        scope.setTeleportPointPublic(oldValue)
        return 0
    }
    return 1
}

internal fun resolveTeleportAccessibilityOwner(
    scope: GeoScope,
    findScope: (AssignedScopeId) -> Pair<Region, GeoScope>? = RegionDatabase::getScopeByAssignedId
): Region? {
    val scopeId = scope.assignedScopeIdOrNull ?: return null
    val (region, canonicalScope) = findScope(scopeId) ?: return null
    return region.takeIf { canonicalScope === scope }
}

fun onGettingTeleportPointAccessibility(
    scope: GeoScope
): Boolean {
    return scope.isTeleportPointPublic
}
