package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

class GeoScope(
    var scopeName: String,
    var worldId: Identifier,
    var teleportPoint: BlockPos?,
    var isTeleportPointPublic: Boolean = false,
    var geoShape: GeoShape?,
    settings: MutableList<Setting> = mutableListOf(),
    var showOnDynmap: Boolean = true,
    scopeId: ScopeId = ScopeId(ScopeId.UNASSIGNED_RAW)
) {
    private var identity: ScopeIdentity = scopeId.toIdentity()
    internal val settingStore = SettingStore(settings)

    /**
     * Binary-compatible identity facade for existing addons.
     *
     * A scope may move from unassigned to assigned once. Replacing an assigned identity is rejected.
     */
    var scopeId: ScopeId
        get() = when (val current = identity) {
            ScopeIdentity.Unassigned -> ScopeId(ScopeId.UNASSIGNED_RAW)
            is ScopeIdentity.Assigned -> current.id.toLegacyScopeId()
        }
        set(value) {
            when (val current = identity) {
                ScopeIdentity.Unassigned -> identity = value.toIdentity()
                is ScopeIdentity.Assigned -> require(current.id.toLegacyScopeId() == value) {
                    "assigned scope id cannot be changed"
                }
            }
        }

    internal val assignedScopeIdOrNull: AssignedScopeId?
        get() = (identity as? ScopeIdentity.Assigned)?.id

    internal fun requireAssignedScopeId(): AssignedScopeId =
        assignedScopeIdOrNull ?: throw IllegalStateException("scope id is not assigned")

    internal fun assignScopeId(id: AssignedScopeId) {
        require(identity === ScopeIdentity.Unassigned) { "scope id is already assigned" }
        identity = ScopeIdentity.Assigned(id)
    }

    /**
     * Binary-compatible view for existing addons.
     *
     * The getter returns a detached snapshot. Mutating that list does not change this GeoScope.
     * Use `RegionDataApi` for reads and `PlayerInteractionApi` setting operations for writes.
     * The JVM getter, setter, and constructor parameter are retained with no scheduled removal.
     */
    var settings: MutableList<Setting>
        get() = settingStore.toLegacyList().toMutableList()
        set(value) = settingStore.replaceAll(value)

    fun getScopeInfo(index: Int): Component? {
        val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
        val dimensionDisplay = getDimensionDisplayName()
        return if (teleportPoint == null) {
            Translator.tr("geo.scope.info",
                index,
                scopeName,
                shapeInfoString,
                dimensionDisplay,
                showOnDynmap)!!
        } else {
            Translator.tr("geo.scope.info.with_teleport_point",
                index,
                scopeName,
                shapeInfoString,
                isTeleportPointPublic,
                teleportPoint!!.x,
                teleportPoint!!.y,
                teleportPoint!!.z,
                dimensionDisplay,
                showOnDynmap)!!
        }
    }

    private fun getDimensionDisplayName(): String {
        val key = "geo.dimension.${worldId.namespace}.${worldId.path}"
        return Translator.tr(key)?.string ?: worldId.toString()
    }

    fun getWorld(server: MinecraftServer): ServerLevel? {
        val registryKey = ResourceKey.create(Registries.DIMENSION, worldId)
        return server.getLevel(registryKey)
    }

    fun getSettingInfos(server: MinecraftServer): List<Component> {
        return Region.formatSettings(server, settings, "geo.scope.setting", scopeName)
    }

    fun certificateTeleportPoint(world: Level, pointToTest: BlockPos?): Boolean {
        if (pointToTest == null) return false
        val shape = geoShape ?: return false
        return shape.certificateTeleportPoint(world, pointToTest)
    }

    fun getTeleportPointInvalidReasonKey(world: Level, pointToTest: BlockPos?): String? {
        if (pointToTest == null) return "teleport_point.invalid.null_point"
        val shape = geoShape ?: return "teleport_point.invalid.no_shape"
        return shape.getTeleportPointInvalidReasonKey(world, pointToTest)
    }

    fun findNearestValidTeleportPoint(world: Level, center: BlockPos, searchRadius: Int): BlockPos? {
        return geoShape?.findNearestValidTeleportPoint(world, center, searchRadius)
    }
}

private fun ScopeId.toIdentity(): ScopeIdentity = when (raw) {
    ScopeId.UNASSIGNED_RAW -> ScopeIdentity.Unassigned
    else -> ScopeIdentity.Assigned(AssignedScopeId.require(this))
}
