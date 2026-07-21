package com.imyvm.iwg.application.selection

import com.imyvm.iwg.application.event.formatSimpleXZList
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
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
    fun `subspace selection feedback uses subspace limits and parent containment`() {
        val scope = GeoScope(
            "main",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100)),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
        )
        val insidePoints = mutableListOf(BlockPos(10, 0, 10), BlockPos(25, 0, 40))
        val insideState = SelectionState(insidePoints, HypotheticalShape.SubSpace("region", scope, GeoShapeType.RECTANGLE))
        val insideText = buildPointAddedMessage(insideState, insidePoints.last()).string

        assertTrue(insideText.contains("子空间"), insideText)
        assertFalse(insideText.contains("过小"), insideText)

        val outsidePoints = mutableListOf(BlockPos(80, 0, 80), BlockPos(120, 0, 120))
        val outsideState = SelectionState(outsidePoints, HypotheticalShape.SubSpace("region", scope, GeoShapeType.RECTANGLE))
        val outsideText = buildPointAddedMessage(outsideState, outsidePoints.last()).string

        assertTrue(outsideText.contains("没有完整包含"), outsideText)
    }


    @Test
    fun `normal selection inside existing scope uses subspace warning and limits`() {
        val worldId = Identifier.parse("minecraft:overworld")
        val scope = GeoScope(
            "main",
            worldId,
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100)),
            scopeId = ScopeId(generateCompatScopeIdRaw(31, 0))
        )
        val region = Region("autoSub", 31, mutableListOf(scope))
        RegionDatabase.addRegion(region)
        try {
            val points = mutableListOf(BlockPos(10, 0, 10), BlockPos(25, 0, 40))
            val state = SelectionState(points, HypotheticalShape.Normal(GeoShapeType.RECTANGLE), worldId)
            val text = buildPointAddedMessage(state, points.last()).string

            assertTrue(text.contains("只能创建子空间"), text)
            assertFalse(text.contains("矩形过小"), text)
        } finally {
            RegionDatabase.removeRegion(region)
        }
    }

    @Test
    fun `normal selection partially overlapping existing scope reports overlap warning`() {
        val worldId = Identifier.parse("minecraft:overworld")
        val scope = GeoScope(
            "main",
            worldId,
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100)),
            scopeId = ScopeId(generateCompatScopeIdRaw(32, 0))
        )
        val region = Region("overlapSub", 32, mutableListOf(scope))
        RegionDatabase.addRegion(region)
        try {
            val points = mutableListOf(BlockPos(90, 0, 90), BlockPos(130, 0, 130))
            val state = SelectionState(points, HypotheticalShape.Normal(GeoShapeType.RECTANGLE), worldId)
            val text = buildPointAddedMessage(state, points.last()).string

            assertTrue(text.contains("部分重叠"), text)
            assertFalse(text.contains("只能创建子空间"), text)
        } finally {
            RegionDatabase.removeRegion(region)
        }
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
}
