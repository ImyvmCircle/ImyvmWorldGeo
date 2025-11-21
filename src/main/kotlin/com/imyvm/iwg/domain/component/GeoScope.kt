package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.geo.*
import com.imyvm.iwg.util.text.Translator
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
        fun updateTeleportPoint(world: World, geoShape: GeoShape): BlockPos? {
            val par = geoShape.shapeParameter
            return when (geoShape.geoShapeType) {
                GeoShapeType.CIRCLE -> updateTeleportPoint(world, par, GeoShapeType.CIRCLE)
                GeoShapeType.RECTANGLE -> updateTeleportPoint(world, par, GeoShapeType.RECTANGLE)
                GeoShapeType.POLYGON -> updateTeleportPoint(world, par, GeoShapeType.POLYGON)
                GeoShapeType.UNKNOWN -> null
            }
        }

        fun certificateTeleportPoint(teleportPoint: BlockPos?): Boolean {

            return true
        }

        private fun updateTeleportPoint(
            world: World,
            shapeParameters: MutableList<Int>,
            geoShapeType: GeoShapeType
        ): BlockPos? {
            val points = when (geoShapeType) {
                GeoShapeType.CIRCLE -> iterateCirclePoint(shapeParameters[0], shapeParameters[1], shapeParameters[2])
                GeoShapeType.RECTANGLE -> iterateRectanglePoint(
                    shapeParameters[0],
                    shapeParameters[1],
                    shapeParameters[2],
                    shapeParameters[3]
                )

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

            val surfaceBlockPos = BlockPos(x, topY - 1, z)
            val surfaceBlockStatus = world.getBlockState(surfaceBlockPos)

            if (
                !surfaceBlockStatus.fluidState.isEmpty
                || !surfaceBlockStatus.hasSolidTopSurface(world, surfaceBlockPos, null)
            ) {
                return null
            }

            return surfaceBlockPos.up()
        }
    }
}