package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.block.CarpetBlock
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World

class GeoScope(
    var scopeName: String,
    var worldId: Identifier,
    var teleportPoint: BlockPos?,
    var geoShape: GeoShape?,
    var settings: MutableList<Setting> = mutableListOf()
) {
    fun getScopeInfo(index: Int): Text? {
        val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
        return Translator.tr("scope.info", index, scopeName, shapeInfoString)
    }

    fun getWorld(server: MinecraftServer): ServerWorld? {
        val registryKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        return server.getWorld(registryKey)
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return Region.formatSettings(server, settings, "scope.setting", scopeName)
    }

    companion object {
        fun certificateTeleportPoint(world: World, teleportPoint: BlockPos?): Boolean {
            if (teleportPoint == null) return false
            return isValidTeleportPoint(world, teleportPoint)
        }

        fun updateTeleportPoint(world: World, geoShape: GeoShape): BlockPos? {
            val par = geoShape.shapeParameter
            return when (geoShape.geoShapeType) {
                GeoShapeType.CIRCLE -> updateTeleportPointByShape(world, par, GeoShapeType.CIRCLE)
                GeoShapeType.RECTANGLE -> updateTeleportPointByShape(world, par, GeoShapeType.RECTANGLE)
                GeoShapeType.POLYGON -> updateTeleportPointByShape(world, par, GeoShapeType.POLYGON)
                GeoShapeType.UNKNOWN -> null
            }
        }

        private fun updateTeleportPointByShape(
            world: World,
            shapeParameters: MutableList<Int>,
            geoShapeType: GeoShapeType
        ): BlockPos? {
            val points = when (geoShapeType) {
                GeoShapeType.CIRCLE -> iterateCirclePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2])
                GeoShapeType.RECTANGLE -> iterateRectanglePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2], shapeParameters[3])
                GeoShapeType.POLYGON -> iteratePolygonPoint(shapeParameters)
                GeoShapeType.UNKNOWN -> return null
            }

            for (point in points) {
                val blockPos = generateSurfacePoint(world, point)
                if (blockPos != null) return blockPos
            }
            return null
        }

        private fun generateSurfacePoint(world: World, point: Pair<Int, Int>): BlockPos? {
            val x = point.first
            val z = point.second
            val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val candidatePos = BlockPos(x, topY, z)
            if (isValidTeleportPoint(world, candidatePos)) {
                return candidatePos
            }
            return null
        }

        private fun isValidTeleportPoint(world: World, pos: BlockPos): Boolean {
            val feetState = world.getBlockState(pos)
            val headState = world.getBlockState(pos.up())
            val groundState = world.getBlockState(pos.down())

            if (!feetState.isAir || !headState.isAir) return false

            val isSolid = groundState.hasSolidTopSurface(world, pos.down(), null)
            val isCarpet = groundState.block is CarpetBlock

            return isSolid || isCarpet
        }
    }
}