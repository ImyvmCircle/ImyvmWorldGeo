package com.imyvm.iwg.application.selection

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionFeedbackTest {
    @Test
    fun `maximum polygon modify feedback displays only eight existing vertices`() {
        val parameters = (-128 until 128).flatMap { value ->
            listOf(value * 10, value * value * 10)
        }.toMutableList()
        val scope = GeoScope(
            "polygon",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape(GeoShapeType.POLYGON, parameters)
        )
        val startText = buildModifyStartMessage(scope).string

        assertEquals(8, Regex("""\(-?[\d,]+,-?[\d,]+\)""").findAll(startText).count(), startText)
        assertTrue(startText.contains("248"))

        val addedPoint = BlockPos(5_000, 0, 5_000)
        val addedState = SelectionState(
            mutableListOf(addedPoint),
            HypotheticalShape.ModifyExisting(scope)
        )
        val emptyState = SelectionState(
            mutableListOf(),
            HypotheticalShape.ModifyExisting(scope)
        )

        assertTrue(buildPointAddedMessage(addedState, addedPoint).string.contains("248"))
        assertTrue(buildPointUndoMessage(emptyState, addedPoint).string.contains("248"))
    }
}
