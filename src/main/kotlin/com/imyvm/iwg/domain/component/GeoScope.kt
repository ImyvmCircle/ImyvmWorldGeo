package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class GeoScope(
    var scopeName: String,
    var worldId: Identifier,
    var teleportPoint: BlockPos?,
    var isTeleportPointPublic: Boolean = false,
    var geoShape: GeoShape?,
    var settings: MutableList<Setting> = mutableListOf()
) {
    fun getScopeInfo(index: Int): Text? {
        val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
        return if (teleportPoint == null) {
            Translator.tr("geo.scope.info",
                index,
                scopeName,
                shapeInfoString)
        } else {
            Translator.tr("geo.scope.info.with_teleport_point",
                index,
                scopeName,
                shapeInfoString,
                isTeleportPointPublic,
                teleportPoint!!.x,
                teleportPoint!!.y,
                teleportPoint!!.z)
        }
    }

    fun getWorld(server: MinecraftServer): ServerWorld? {
        val registryKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        return server.getWorld(registryKey)
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return Region.formatSettings(server, settings, "geo.scope.setting", scopeName)
    }

    fun certificateTeleportPoint(world: World, pointToTest: BlockPos?): Boolean {
        if (pointToTest == null) return false
        if (this.geoShape == null) return false
        return this.geoShape!!.certificateTeleportPoint(world, pointToTest)
    }
}