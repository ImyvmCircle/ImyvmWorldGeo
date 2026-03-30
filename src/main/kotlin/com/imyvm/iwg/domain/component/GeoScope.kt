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
    var settings: MutableList<Setting> = mutableListOf(),
    var showOnDynmap: Boolean = true
) {
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
        return Translator.tr(key)!!?.string ?: worldId.toString()
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
        if (this.geoShape == null) return false
        return this.geoShape!!.certificateTeleportPoint(world, pointToTest)
    }

    fun getTeleportPointInvalidReasonKey(world: Level, pointToTest: BlockPos?): String? {
        if (pointToTest == null) return "teleport_point.invalid.null_point"
        if (this.geoShape == null) return "teleport_point.invalid.no_shape"
        return this.geoShape!!.getTeleportPointInvalidReasonKey(world, pointToTest)
    }

    fun findNearestValidTeleportPoint(world: Level, center: BlockPos, searchRadius: Int): BlockPos? {
        if (this.geoShape == null) return null
        return this.geoShape!!.findNearestValidTeleportPoint(world, center, searchRadius)
    }
}