package com.imyvm.iwg.inter.api

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.WorldGeoSettingVisibility
import com.imyvm.iwg.domain.WorldGeoSpaceType
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.domain.component.SubSpace
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegionDataApiTest {
    @Test
    fun `region scopes are returned as a snapshot`() {
        val first = scope("first")
        val region = Region("region", 1, mutableListOf(first))

        val snapshot = RegionDataApi.getRegionScopes(region)
        region.addScope(scope("second", 2))

        assertEquals(listOf(first), snapshot)
    }

    @Test
    fun `space snapshot exposes immutable subspace facts`() {
        val scope = scope("first", geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(30, 30)))
        val subSpace = SubSpace(
            7,
            "room",
            scope.requireAssignedScopeId(),
            scope.worldId,
            GeoShape.rectangle(GeoPoint(5, 5), GeoPoint(10, 10)),
            enterMessage = "enter",
            settings = mutableListOf(EntryExitToggleSetting(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED, true)),
            stringTags = setOf("debug"),
            keyedTags = mapOf("worldgeo:dominant_biome" to "minecraft:plains")
        )
        val region = Region("green-region", 1, mutableListOf(scope), subSpaces = mutableListOf(subSpace))

        val snapshot = RegionDataApi.getSubSpaceSnapshot(region, scope, subSpace)

        assertEquals(WorldGeoSpaceType.SUBSPACE, snapshot.type)
        assertEquals(7, snapshot.id)
        assertEquals(1, snapshot.parentRegionId)
        assertEquals(scope.requireAssignedScopeId().raw, snapshot.parentScopeId)
        assertEquals(setOf("debug"), snapshot.stringTags)
        assertEquals(Identifier.parse("minecraft:plains"), snapshot.dominantBiomeId)
        assertEquals(true, snapshot.entryMessageEnabled)
        assertEquals(true, snapshot.entryMessageConfigured)
        assertEquals(0x44BB44, snapshot.mapColorSuggestion)
        assertEquals(listOf("ENTRY_EXIT_MESSAGE_ENABLED"), snapshot.settingSummary.map { it.key })
        assertFailsWith<UnsupportedOperationException> { (snapshot.stringTags as MutableSet).add("later") }
    }

    @Test
    fun `region snapshot reports entry message configuration`() {
        val region = Region(
            "green-region",
            1,
            mutableListOf(scope("first")),
            settings = mutableListOf(
                EntryExitToggleSetting(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED, false),
                EntryExitMessageSetting(EntryExitMessageKey.ENTER_MESSAGE, "hello")
            )
        )

        val snapshot = RegionDataApi.getRegionSpaceSnapshot(region)

        assertEquals(false, snapshot.entryMessageEnabled)
        assertEquals(true, snapshot.entryMessageConfigured)
        assertEquals(0x44BB44, snapshot.mapColorSuggestion)
        assertEquals(2, snapshot.settingSummary.size)
    }

    @Test
    fun `setting summaries keep personal settings out of public visibility`() {
        val player = UUID.randomUUID()
        val region = Region(
            "region",
            1,
            mutableListOf(scope("first")),
            settings = mutableListOf(
                PermissionSetting(PermissionKey.BUILD, true),
                PermissionSetting(PermissionKey.BREAK, false, player)
            )
        )

        val publicSummaries = RegionDataApi.listRegionSettingSummaries(region, WorldGeoSettingVisibility.PUBLIC)
        val debugSummaries = RegionDataApi.listRegionSettingSummaries(region, WorldGeoSettingVisibility.OP_DEBUG)

        assertEquals(listOf("GLOBAL"), publicSummaries.map { it.subject })
        assertEquals(setOf("GLOBAL", player.toString()), debugSummaries.map { it.subject }.toSet())
    }

    @Test
    fun `timed overlay factory snapshots effects`() {
        val effects = mutableListOf(TimedEffect(EffectKey.SPEED, 1))
        val scopeId = AssignedScopeId.require(ScopeId(generateCompatScopeIdRaw(1, 0)))

        val overlay = RegionDataApi.createTimedEffectOverlay(
            "overlay", scopeId, effects, 0, 1, 0, "test"
        )
        effects.clear()

        assertEquals(listOf(TimedEffect(EffectKey.SPEED, 1)), overlay.effects)
        assertFailsWith<UnsupportedOperationException> { (overlay.effects as MutableList).clear() }
    }

    private fun scope(name: String, id: Long = 1, geoShape: GeoShape? = null) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = geoShape,
        scopeId = ScopeId(generateCompatScopeIdRaw(1, id.toInt()))
    )
}
