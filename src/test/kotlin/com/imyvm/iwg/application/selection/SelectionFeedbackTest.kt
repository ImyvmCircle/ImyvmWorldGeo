package com.imyvm.iwg.application.selection

import com.imyvm.iwg.application.event.formatSimpleXZList
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.util.text.Translator
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `selection feedback bounds new point presentation`() {
        val points = (100 until 120).map { BlockPos(it, 0, it) }.toMutableList()
        val normalState = SelectionState(points, HypotheticalShape.Normal(GeoShapeType.POLYGON))
        val normalText = buildPointAddedMessage(normalState, points.last()).string
        val omittedText = requireNotNull(Translator.tr("selection.feedback.points_omitted", 8)).string

        assertTrue(normalText.contains("(111,111)"), normalText)
        assertFalse(normalText.contains("(112,112)"), normalText)
        assertTrue(normalText.contains(omittedText), normalText)

        val scope = GeoScope(
            "rectangle",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(
                GeoPoint(0, 0),
                GeoPoint(100, 100)
            )
        )
        val modifyState = SelectionState(points, HypotheticalShape.ModifyExisting(scope))
        val modifyText = buildPointAddedMessage(modifyState, points.last()).string
        val rejectionText = formatSimpleXZList(points)

        assertFalse(modifyText.contains("(112,112)"), modifyText)
        assertTrue(modifyText.contains(omittedText), modifyText)
        assertFalse(rejectionText.contains("(112,112)"), rejectionText)
        assertTrue(
            rejectionText.contains(requireNotNull(Translator.raw("selection.feedback.points_omitted", 8))),
            rejectionText
        )
    }

    @Test
    fun `decimal formatting uses dot separator regardless of default locale`() {
        val savedLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val points = mutableListOf(BlockPos.ZERO, BlockPos(1, 0, 0))
            val state = SelectionState(points, HypotheticalShape.Normal(GeoShapeType.CIRCLE))
            val text = buildPointAddedMessage(state, points.last()).string
            assertTrue(text.contains("1.0"), "Radius should use dot decimal '1.0' but got: $text")
        } finally {
            java.util.Locale.setDefault(savedLocale)
        }
    }
}
