package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShape.Companion.isPhysicalSafe
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateNewScopeIdRaw
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.geo.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import kotlin.math.abs

object RegionFactory {

    fun createRegion(
        name: String,
        numberID: Int,
        playerExecutor: ServerPlayer? = null,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<Region, CreationError> {

        val mainScopeResult = createScopeForPlayer(
            scopeName = "main_scope",
            playerExecutor = requireNotNull(playerExecutor) { "playerExecutor is required to create a region" },
            selectedPositions = selectedPositions,
            shapeType = shapeType
        )

        if (mainScopeResult is Result.Err) return mainScopeResult

        val mainScope = (mainScopeResult as Result.Ok).value
        mainScope.assignScopeId(
            AssignedScopeId.require(ScopeId(generateNewScopeIdRaw(numberID, parseMarkFromRegionId(numberID))))
        )
        val geometryScope = mutableListOf<GeoScope>()
        geometryScope.add(mainScope)

        val newRegion = Region(name, numberID, geometryScope)

        return Result.Ok(newRegion)
    }

    fun createScopeForPlayer(
        scopeName: String,
        playerExecutor: ServerPlayer,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoScope, CreationError> {
        val worldId = playerExecutor.level().dimension().identifier()
        val geoShapeResult = createGeoShape(selectedPositions, shapeType, worldId)
        if (geoShapeResult is Result.Err) return geoShapeResult
        val geoShape = (geoShapeResult as Result.Ok).value
        return Result.Ok(GeoScope(scopeName, worldId, getTeleportPoint(playerExecutor, geoShape), false, geoShape))
    }

    fun recreateScope(
        scopeName: String,
        worldId: Identifier,
        teleportPoint: BlockPos?,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoScope, CreationError> = createScopeFromData(
        scopeName,
        worldId,
        teleportPoint,
        selectedPositions,
        shapeType
    )

    internal fun recreateScopeShape(
        region: Region,
        existingScope: GeoScope,
        selectedPositions: List<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoShape, CreationError> {
        require(region.containsScope(existingScope)) { "scope does not belong to region" }
        return createGeoShape(selectedPositions, shapeType, existingScope.worldId, existingScope)
    }

    @Deprecated("Use createScopeForPlayer or recreateScope")
    fun createScope(
        scopeName: String,
        playerExecutor: ServerPlayer? = null,
        existingWorld: Identifier? = null,
        existingTeleportPoint: BlockPos? = null,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoScope, CreationError> {
        return if (existingWorld == null) {
            createScopeForPlayer(scopeName, requireNotNull(playerExecutor) { "playerExecutor or existingWorld is required" }, selectedPositions, shapeType)
        } else {
            createScopeFromData(scopeName, existingWorld, existingTeleportPoint, selectedPositions, shapeType)
        }
    }

    private fun createScopeFromData(
        scopeName: String,
        worldId: Identifier,
        teleportPoint: BlockPos?,
        selectedPositions: MutableList<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoScope, CreationError> {
        val geoShapeResult = createGeoShape(selectedPositions, shapeType, worldId)
        if (geoShapeResult is Result.Err) return geoShapeResult
        return Result.Ok(GeoScope(scopeName, worldId, teleportPoint, false, (geoShapeResult as Result.Ok).value))
    }

    private fun getTeleportPoint(
        playerExecutor: ServerPlayer?,
        geoShape: GeoShape
    ): BlockPos? {
        if (playerExecutor == null) return null

        val playerWorld = playerExecutor.level()
        val playerPosition = playerExecutor.blockPosition()
        return if (isPhysicalSafe(playerWorld, playerPosition)) playerPosition
        else geoShape.generateTeleportPoint(playerWorld)
    }

    private fun createGeoShape(
        positions: List<BlockPos>,
        shapeType: GeoShapeType,
        worldId: Identifier,
        excludedScope: GeoScope? = null
    ): Result<GeoShape, CreationError> {
        val requiredPoints = requiredPoints(shapeType)
        if (positions.size < requiredPoints) {
            return Result.Err(CreationError.InsufficientPoints)
        }

        val geoShapeResult = when (shapeType) {
            GeoShapeType.RECTANGLE -> createRectangle(positions)
            GeoShapeType.CIRCLE -> createCircle(positions)
            GeoShapeType.POLYGON -> createPolygon(positions)
            else -> Result.Err(CreationError.InsufficientPoints)
        }

        if (geoShapeResult is Result.Err) return geoShapeResult

        val geoShape = (geoShapeResult as Result.Ok).value

        val existingScopes = RegionDatabase.getRegionList()
            .flatMap { region ->
                region.scopes
                    .filter { it !== excludedScope && it.worldId == worldId }
                    .map { Pair(it, region.name) }
            }
        val intersections = checkIntersection(geoShape, existingScopes)
        if (intersections.isNotEmpty()) {
            return Result.Err(CreationError.IntersectionBetweenScopes(intersections))
        }

        return Result.Ok(geoShape)
    }

    private fun requiredPoints(shapeType: GeoShapeType): Int =
        when (shapeType) {
            GeoShapeType.CIRCLE,
            GeoShapeType.RECTANGLE -> 2
            GeoShapeType.POLYGON -> 3
            else -> 0
        }

    private fun createRectangle(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val pos1 = positions[0]
        val pos2 = positions[1]

        if (pos1 == pos2) return Result.Err(CreationError.DuplicatedPoints)
        if (pos1.x == pos2.x || pos1.z == pos2.z) return Result.Err(CreationError.CoincidentPoints)

        val width = abs(pos1.x.toLong() - pos2.x)
        val length = abs(pos1.z.toLong() - pos2.z)
        val error = checkRectangleSize(width, length)
        if (error != null) return Result.Err(error)

        val west = minOf(pos1.x, pos2.x)
        val east = maxOf(pos1.x, pos2.x)
        val north = minOf(pos1.z, pos2.z)
        val south = maxOf(pos1.z, pos2.z)

        return Result.Ok(
            GeoShape(GeoShapeType.RECTANGLE, mutableListOf(west, north, east, south))
        )
    }

    internal fun createCircle(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val center = positions[0]
        val circumference = positions[1]

        if (center == circumference) return Result.Err(CreationError.DuplicatedPoints)

        val radius = circleRadius(center, circumference)
        if (!checkCircleSize(radius)) return Result.Err(CreationError.UnderSizeLimit)
        if (radius > Int.MAX_VALUE) return Result.Err(CreationError.CoordinateRangeExceeded)
        val intRadius = radius.toInt()
        if (!circleExtentsFit(center.x, center.z, intRadius)) {
            return Result.Err(CreationError.CoordinateRangeExceeded)
        }

        return Result.Ok(
            GeoShape(GeoShapeType.CIRCLE, mutableListOf(center.x, center.z, intRadius))
        )
    }

    private fun circleExtentsFit(centerX: Int, centerZ: Int, radius: Int): Boolean {
        val min = Int.MIN_VALUE.toLong()
        val max = Int.MAX_VALUE.toLong()
        return centerX.toLong() - radius in min..max && centerX.toLong() + radius in min..max &&
            centerZ.toLong() - radius in min..max && centerZ.toLong() + radius in min..max
    }

    private fun circleRadius(center: BlockPos, circumference: BlockPos): Double =
        kotlin.math.hypot(
            circumference.x.toDouble() - center.x,
            circumference.z.toDouble() - center.z
        )

    private fun createPolygon(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        if (!isPolygonVertexCountSupported(positions.size)) {
            return Result.Err(CreationError.PolygonVertexLimitExceeded)
        }
        val distinct = positions.distinctBy { it.x to it.z }
        if (distinct.size != positions.size) return Result.Err(CreationError.DuplicatedPoints)
        if (!isConvex(positions)) return Result.Err(CreationError.NotConvex)
        val error = checkPolygonSize(positions)
        if (error != null) return Result.Err(error)

        return Result.Ok(
            GeoShape(
                geoShapeType = GeoShapeType.POLYGON,
                positions.flatMap { listOf(it.x, it.z) }.toMutableList()
            )
        )
    }
}

sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}
