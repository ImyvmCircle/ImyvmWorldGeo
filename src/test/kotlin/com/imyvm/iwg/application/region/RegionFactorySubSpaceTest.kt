package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionFactorySubSpaceTest {
    @Test
    fun `subspace rectangle uses smaller size limit but region scope keeps main limit`() {
        val scope = scope()
        val region = Region("region", 7, mutableListOf(scope))
        val points = listOf(BlockPos(10, 0, 10), BlockPos(25, 0, 40))

        val subSpace = RegionFactory.createSubSpaceShape(points, GeoShapeType.RECTANGLE, region, scope)
        val mainScope = RegionFactory.validateGeoShapeSize(
            GeoShape.rectangle(GeoPoint(10, 10), GeoPoint(25, 40))
        )

        assertTrue(subSpace is Result.Ok, subSpace.toString())
        assertEquals(CreationError.UnderSizeLimit, mainScope)
    }

    @Test
    fun `subspace rectangle must be fully contained by parent scope`() {
        val scope = scope()
        val region = Region("region", 7, mutableListOf(scope))
        val points = listOf(BlockPos(80, 0, 80), BlockPos(120, 0, 120))

        val result = RegionFactory.createSubSpaceShape(points, GeoShapeType.RECTANGLE, region, scope)

        assertEquals(
            CreationError.SubSpaceOutsideParentScope("region", "main"),
            (result as Result.Err).error
        )
    }

    private fun scope() = GeoScope(
        "main",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100)),
        scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
    )
}
