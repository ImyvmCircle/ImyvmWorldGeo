package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.Result
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreationSelectionTest {
    @Test
    fun `missing and modification selections are rejected`() {
        assertEquals(
            Result.Err(CreationSelectionError.NOT_IN_SELECTION_MODE),
            resolveCreationSelection(null)
        )

        val scope = GeoScope("scope", Identifier.parse("minecraft:overworld"), null, geoShape = null)
        val modifying = SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(scope))
        assertEquals(
            Result.Err(CreationSelectionError.CREATION_MODE_REQUIRED),
            resolveCreationSelection(modifying)
        )
        assertEquals(
            Result.Err(CreationSelectionError.CREATION_MODE_REQUIRED),
            resolveCreationSelection(modifying, GeoShapeType.CIRCLE)
        )
    }

    @Test
    fun `creation shape is inferred or taken from the fixed selection`() {
        val inferredRectangle = selection(2)
        val inferredPolygon = selection(3)
        val fixedCircle = selection(2, HypotheticalShape.Normal(GeoShapeType.CIRCLE))

        assertEquals(GeoShapeType.RECTANGLE, resolved(inferredRectangle).shapeType)
        assertEquals(GeoShapeType.POLYGON, resolved(inferredPolygon).shapeType)
        assertEquals(GeoShapeType.CIRCLE, resolved(fixedCircle).shapeType)
    }

    @Test
    fun `explicit compatibility shape overrides normal inference but rejects unknown`() {
        val state = selection(3)

        assertEquals(
            GeoShapeType.RECTANGLE,
            assertIs<Result.Ok<CreationSelection>>(
                resolveCreationSelection(state, GeoShapeType.RECTANGLE)
            ).value.shapeType
        )
        assertEquals(
            Result.Err(CreationSelectionError.UNSUPPORTED_SHAPE),
            resolveCreationSelection(state, GeoShapeType.UNKNOWN)
        )
        assertEquals(
            Result.Err(CreationSelectionError.UNSUPPORTED_SHAPE),
            resolveCreationSelection(
                selection(2, HypotheticalShape.Normal(GeoShapeType.UNKNOWN))
            )
        )
    }

    @Test
    fun `resolved points are an immutable snapshot of live selection state`() {
        val state = selection(2)
        val resolved = resolved(state)

        state.points.clear()
        state.points.add(BlockPos(99, 0, 99))

        assertEquals(listOf(BlockPos.ZERO, BlockPos(10, 0, 10)), resolved.points)
    }

    private fun selection(count: Int, shape: HypotheticalShape? = null): SelectionState {
        val points = mutableListOf(
            BlockPos.ZERO,
            BlockPos(10, 0, 10),
            BlockPos(20, 0, 5)
        )
        return SelectionState(points.take(count).toMutableList(), shape)
    }

    private fun resolved(state: SelectionState): CreationSelection =
        assertIs<Result.Ok<CreationSelection>>(resolveCreationSelection(state)).value
}
