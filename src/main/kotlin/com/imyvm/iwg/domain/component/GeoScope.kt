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
    scopeName: String,
    worldId: Identifier,
    teleportPoint: BlockPos?,
    isTeleportPointPublic: Boolean = false,
    geoShape: GeoShape?,
    settings: MutableList<Setting> = mutableListOf(),
    showOnDynmap: Boolean = true,
    scopeId: ScopeId = ScopeId(ScopeId.UNASSIGNED_RAW)
) {
    init {
        require(isValidGeoName(scopeName)) { "invalid scope name" }
    }

    private var currentScopeName: String = scopeName
    private val currentWorldId: Identifier = worldId
    private var currentTeleportPoint: BlockPos? = teleportPoint
    private var currentTeleportPointPublic: Boolean = isTeleportPointPublic
    private var currentGeoShape: GeoShape? = geoShape
    private var currentShowOnDynmap: Boolean = showOnDynmap
    private var identity: ScopeIdentity = scopeId.toIdentity()
    internal val settingStore = SettingStore(settings)

    /**
     * Binary-compatible property for existing addons. Renaming is controlled by the owning Region.
     */
    var scopeName: String
        get() = currentScopeName
        set(value) {
            require(value == currentScopeName) { "scope name must be changed through its owning region" }
        }

    /** Binary-compatible property. A Scope cannot change dimension after construction. */
    var worldId: Identifier
        get() = currentWorldId
        set(value) {
            require(value == currentWorldId) { "scope world cannot be changed" }
        }

    /** Binary-compatible property. Use supported interaction operations to update the teleport point. */
    var teleportPoint: BlockPos?
        get() = currentTeleportPoint
        set(value) {
            require(value == currentTeleportPoint) { "teleport point must be changed through the application boundary" }
        }

    /** Binary-compatible property. Use supported interaction operations to change accessibility. */
    var isTeleportPointPublic: Boolean
        get() = currentTeleportPointPublic
        set(value) {
            require(value == currentTeleportPointPublic) {
                "teleport point accessibility must be changed through the application boundary"
            }
        }

    /** Binary-compatible property. Use supported interaction operations to replace geometry. */
    var geoShape: GeoShape?
        get() = currentGeoShape
        set(value) {
            require(value === currentGeoShape) { "scope geometry must be changed through the application boundary" }
        }

    /** Binary-compatible property. Use supported interaction operations to change Dynmap visibility. */
    var showOnDynmap: Boolean
        get() = currentShowOnDynmap
        set(value) {
            require(value == currentShowOnDynmap) { "Dynmap visibility must be changed through the application boundary" }
        }

    internal fun renameTo(name: String) {
        require(isValidGeoName(name)) { "invalid scope name" }
        currentScopeName = name
    }

    internal fun replaceGeometry(shape: GeoShape?) {
        currentGeoShape = shape
    }

    internal fun updateTeleportPoint(point: BlockPos?) {
        currentTeleportPoint = point
    }

    internal fun setTeleportPointPublic(value: Boolean) {
        currentTeleportPointPublic = value
    }

    internal fun setDynmapVisibility(value: Boolean) {
        currentShowOnDynmap = value
    }

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
        val point = teleportPoint
        return if (point == null) {
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
                point.x,
                point.y,
                point.z,
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
