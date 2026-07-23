package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateNewScopeIdRaw
import com.imyvm.iwg.domain.component.isPolygonVertexCountSupported
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.TeleportConfig
import com.imyvm.iwg.util.geo.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos

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

    fun createRegionFromShape(
        name: String,
        numberID: Int,
        player: ServerPlayer,
        shape: GeoShape
    ): Result<Region, CreationError> {
        val worldId = player.level().dimension().identifier()
        validateGeoShapeSize(shape)?.let { return Result.Err(it) }
        validateGeoShapePlacement(shape, worldId, null)?.let { return Result.Err(it) }

        val mainScope = GeoScope("main_scope", worldId, getTeleportPoint(player, shape), false, shape)
        mainScope.assignScopeId(
            AssignedScopeId.require(ScopeId(generateNewScopeIdRaw(numberID, parseMarkFromRegionId(numberID))))
        )
        val newRegion = Region(name, numberID, mutableListOf(mainScope))
        return Result.Ok(newRegion)
    }

    fun createScopeFromShape(
        scopeName: String,
        player: ServerPlayer,
        shape: GeoShape
    ): Result<GeoScope, CreationError> {
        val worldId = player.level().dimension().identifier()
        validateGeoShapeSize(shape)?.let { return Result.Err(it) }
        validateGeoShapePlacement(shape, worldId, null)?.let { return Result.Err(it) }
        return Result.Ok(GeoScope(scopeName, worldId, getTeleportPoint(player, shape), false, shape))
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
        playerExecutor: ServerPlayer,
        geoShape: GeoShape
    ): BlockPos? {
        val playerWorld = playerExecutor.level()
        val playerPosition = playerExecutor.blockPosition()
        return if (geoShape.certificateTeleportPoint(playerWorld, playerPosition)) {
            playerPosition
        } else {
            geoShape.findNearestValidTeleportPoint(
                playerWorld,
                playerPosition,
                TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value
            )
        }
    }

    internal fun createSubSpaceShape(
        positions: List<BlockPos>,
        shapeType: GeoShapeType,
        region: Region,
        parentScope: GeoScope,
        excludedSubSpace: SubSpace? = null
    ): Result<GeoShape, CreationError> {
        require(region.containsScope(parentScope)) { "scope does not belong to region" }
        val geoShapeResult = createGeoShape(
            positions,
            shapeType,
            parentScope.worldId,
            null,
            ::validateSubSpaceGeoShapeSize,
            validatePlacement = false
        )
        if (geoShapeResult is Result.Err) return geoShapeResult
        val geoShape = (geoShapeResult as Result.Ok).value
        return validateSubSpaceShapePlacement(geoShape, region, parentScope, excludedSubSpace)?.let { Result.Err(it) }
            ?: Result.Ok(geoShape)
    }

    internal fun validateSubSpaceSelection(
        positions: List<BlockPos>,
        shapeType: GeoShapeType,
        region: Region,
        parentScope: GeoScope,
        excludedSubSpace: SubSpace? = null
    ): CreationError? {
        val result = createSubSpaceShape(positions, shapeType, region, parentScope, excludedSubSpace)
        return (result as? Result.Err)?.error
    }

    internal fun validateSubSpaceSelection(
        positions: List<BlockPos>,
        shapeType: GeoShapeType,
        regionName: String,
        parentScope: GeoScope
    ): CreationError? {
        val geoShapeResult = createGeoShape(
            positions,
            shapeType,
            parentScope.worldId,
            null,
            ::validateSubSpaceGeoShapeSize,
            validatePlacement = false
        )
        if (geoShapeResult is Result.Err) return geoShapeResult.error
        val geoShape = (geoShapeResult as Result.Ok).value
        return validateSubSpaceShapeContainedByParent(geoShape, regionName, parentScope)
    }


    internal fun createSelectionShape(
        positions: List<BlockPos>,
        shapeType: GeoShapeType
    ): Result<GeoShape, CreationError> {
        val requiredPoints = requiredPoints(shapeType)
        if (positions.size < requiredPoints) return Result.Err(CreationError.InsufficientPoints)
        return when (shapeType) {
            GeoShapeType.RECTANGLE -> createRectangle(positions)
            GeoShapeType.CIRCLE -> createCircle(positions)
            GeoShapeType.POLYGON -> createPolygon(positions)
            else -> Result.Err(CreationError.InsufficientPoints)
        }
    }

    private fun createGeoShape(
        positions: List<BlockPos>,
        shapeType: GeoShapeType,
        worldId: Identifier,
        excludedScope: GeoScope? = null,
        sizeValidator: (GeoShape) -> CreationError? = ::validateGeoShapeSize,
        validatePlacement: Boolean = true
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
        sizeValidator(geoShape)?.let { return Result.Err(it) }
        if (validatePlacement) {
            val placementError = validateGeoShapePlacement(geoShape, worldId, excludedScope, validateSize = false)
            if (placementError != null) return Result.Err(placementError)
        }

        return Result.Ok(geoShape)
    }

    internal fun validateGeoShapePlacement(
        geoShape: GeoShape,
        worldId: Identifier,
        excludedScope: GeoScope?,
        currentRegions: List<Region> = RegionDatabase.getRegionList(),
        validateSize: Boolean = true
    ): CreationError? {
        if (validateSize) validateGeoShapeSize(geoShape)?.let { return it }
        val existingScopes = currentRegions
            .flatMap { region ->
                region.scopes
                    .filter { it !== excludedScope && it.worldId == worldId }
                    .map { Pair(it, region.name) }
            }
        val intersections = checkIntersection(geoShape, existingScopes)
        return intersections.takeIf { it.isNotEmpty() }?.let(CreationError::IntersectionBetweenScopes)
    }

    internal fun validateScopeContainsSubSpaces(
        region: Region,
        parentScope: GeoScope,
        geoShape: GeoShape
    ): CreationError? {
        val parentScopeId = parentScope.requireAssignedScopeId()
        return if (region.subSpaces.any { it.parentScopeId == parentScopeId && !it.geoShape.isContainedBy(geoShape) }) {
            CreationError.SubSpaceOutsideParentScope(region.name, parentScope.scopeName)
        } else null
    }


    internal fun validateSubSpaceGeoShapeSize(geoShape: GeoShape): CreationError? =
        when (val geometry = geoShape.typedGeometry) {
            is CircleGeometry -> if (checkCircleSize(geometry.radius.toDouble(), subSpaceGeometrySizeLimits)) null else CreationError.UnderSizeLimit
            is RectangleGeometry -> checkRectangleSize(
                geometry.east.toLong() - geometry.west,
                geometry.south.toLong() - geometry.north,
                subSpaceGeometrySizeLimits
            )
            is PolygonGeometry -> checkPolygonSize(
                List(geometry.vertexCount) { BlockPos(geometry.x(it), 0, geometry.z(it)) },
                subSpaceGeometrySizeLimits
            )
            UnknownGeometry -> CreationError.InsufficientPoints
        }

    internal fun validateSubSpaceShapePlacement(
        geoShape: GeoShape,
        region: Region,
        parentScope: GeoScope,
        excludedSubSpace: SubSpace?
    ): CreationError? {
        validateSubSpaceShapeContainedByParent(geoShape, region.name, parentScope)?.let { return it }
        val existingSubSpaces = region.subSpaces
            .asSequence()
            .filter { it !== excludedSubSpace && it.parentScopeId == parentScope.requireAssignedScopeId() }
            .map { subSpace ->
                GeoScope(
                    subSpace.name,
                    subSpace.worldId,
                    null,
                    geoShape = subSpace.geoShape,
                    scopeId = ScopeId(parentScope.requireAssignedScopeId().raw)
                ) to region.name
            }
            .toList()
        val intersections = checkIntersection(geoShape, existingSubSpaces)
        return intersections.takeIf { it.isNotEmpty() }?.let(CreationError::IntersectionBetweenScopes)
    }

    private fun validateSubSpaceShapeContainedByParent(
        geoShape: GeoShape,
        regionName: String,
        parentScope: GeoScope
    ): CreationError? {
        val parentShape = parentScope.geoShape ?: return CreationError.SubSpaceOutsideParentScope(regionName, parentScope.scopeName)
        return if (geoShape.isContainedBy(parentShape)) null else CreationError.SubSpaceOutsideParentScope(regionName, parentScope.scopeName)
    }

    internal fun validateGeoShapeSize(geoShape: GeoShape): CreationError? =
        when (val geometry = geoShape.typedGeometry) {
            is CircleGeometry -> if (checkCircleSize(geometry.radius.toDouble())) null else CreationError.UnderSizeLimit
            is RectangleGeometry -> checkRectangleSize(
                geometry.east.toLong() - geometry.west,
                geometry.south.toLong() - geometry.north
            )
            is PolygonGeometry -> checkPolygonSize(
                List(geometry.vertexCount) { BlockPos(geometry.x(it), 0, geometry.z(it)) }
            )
            UnknownGeometry -> CreationError.InsufficientPoints
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

        val west = minOf(pos1.x, pos2.x)
        val east = maxOf(pos1.x, pos2.x)
        val north = minOf(pos1.z, pos2.z)
        val south = maxOf(pos1.z, pos2.z)

        return Result.Ok(GeoShape.rectangle(GeoPoint(west, north), GeoPoint(east, south)))
    }

    internal fun createCircle(positions: List<BlockPos>): Result<GeoShape, CreationError> {
        val center = positions[0]
        val circumference = positions[1]

        if (center == circumference) return Result.Err(CreationError.DuplicatedPoints)

        val radius = circleRadius(center, circumference)
        if (radius > Int.MAX_VALUE) return Result.Err(CreationError.CoordinateRangeExceeded)
        val intRadius = radius.toInt()
        if (!circleExtentsFit(center.x, center.z, intRadius)) {
            return Result.Err(CreationError.CoordinateRangeExceeded)
        }

        return Result.Ok(GeoShape.circle(GeoPoint(center.x, center.z), intRadius))
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
        return Result.Ok(GeoShape.polygon(positions.map { GeoPoint(it.x, it.z) }))
    }
}

sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}
