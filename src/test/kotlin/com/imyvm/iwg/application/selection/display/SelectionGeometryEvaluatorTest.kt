package com.imyvm.iwg.application.selection.display

import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.util.geo.subSpaceGeometrySizeLimits
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertIs

class SelectionGeometryEvaluatorTest {
    @Test
    fun `modify rectangle preview can be valid for subspace limits while invalid for region limits`() {
        val geometry = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(40, 40)).typedGeometry
        val selected = listOf(BlockPos(20, 0, 25))

        assertIs<ModifiedShapeEvaluation.Invalid>(evaluateModifiedShapePreview(geometry, selected))
        assertIs<ModifiedShapeEvaluation.Valid>(evaluateModifiedShapePreview(geometry, selected, subSpaceGeometrySizeLimits))
    }
}
