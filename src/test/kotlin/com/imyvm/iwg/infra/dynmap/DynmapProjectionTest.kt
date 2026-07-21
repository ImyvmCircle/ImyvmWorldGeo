package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DynmapProjectionTest {
    @Test
    fun `projection includes visible boundary and teleport point`() {
        val scope = scope(
            "scope",
            0,
            GeoShape.rectangle(GeoPoint(1, 2), GeoPoint(5, 7)),
            BlockPos(3, 64, 4)
        )

        val projection = buildDynmapProjection(listOf(Region("region", 7, mutableListOf(scope))))

        assertEquals(2, projection.size)
        val area = assertIs<DynmapAreaMarkerSpec>(projection[0])
        assertEquals(DynmapMarkerIdentity(DynmapMarkerKind.AREA, dynmapScopeMarkerId(scope)), area.identity)
        assertEquals("region:scope", area.label)
        assertEquals("world", area.world)
        assertContentEquals(doubleArrayOf(1.0, 5.0, 5.0, 1.0), area.x)
        assertContentEquals(doubleArrayOf(2.0, 2.0, 7.0, 7.0), area.z)
        val point = assertIs<DynmapPointMarkerSpec>(projection[1])
        assertEquals(DynmapMarkerIdentity(DynmapMarkerKind.POINT, dynmapTeleportMarkerId(scope)), point.identity)
        assertEquals(3.0, point.x)
        assertEquals(64.0, point.y)
        assertEquals(4.0, point.z)
    }

    @Test
    fun `projection excludes hidden region and scope`() {
        val visibleShape = GeoShape.circle(GeoPoint(0, 0), 5)
        val hiddenScope = scope("hidden", 0, visibleShape, showOnDynmap = false)
        val hiddenRegionScope = scope("scope", 1, visibleShape)

        assertTrue(buildDynmapProjection(listOf(Region("region", 7, mutableListOf(hiddenScope)))).isEmpty())
        assertTrue(
            buildDynmapProjection(
                listOf(Region("region", 7, mutableListOf(hiddenRegionScope), showOnDynmap = false))
            ).isEmpty()
        )
    }

    @Test
    fun `shape type changes marker kind without changing stable boundary id`() {
        val circle = scope("scope", 0, GeoShape.circle(GeoPoint(0, 0), 5))
        val rectangle = scope("scope", 0, GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(5, 5)))

        val circleSpec = buildDynmapProjection(listOf(Region("region", 7, mutableListOf(circle)))).single()
        val areaSpec = buildDynmapProjection(listOf(Region("region", 7, mutableListOf(rectangle)))).single()

        assertEquals(circleSpec.identity.id, areaSpec.identity.id)
        assertEquals(DynmapMarkerKind.CIRCLE, circleSpec.identity.kind)
        assertEquals(DynmapMarkerKind.AREA, areaSpec.identity.kind)
    }

    @Test
    fun `upsert failures do not stop later markers and suppress stale cleanup`() {
        val first = pointSpec("first")
        val failing = pointSpec("failing")
        val last = pointSpec("last")
        val stale = DynmapMarkerIdentity(DynmapMarkerKind.POINT, "stale")
        val upserts = mutableListOf<String>()
        val deletions = mutableListOf<String>()

        val result = reconcileDynmapProjection(
            listOf(first, failing, last),
            listOf(stale),
            { it },
            {
                upserts.add(it.identity.id)
                if (it.identity == failing.identity) error("failure")
            },
            { deletions.add(it.id) }
        )

        assertEquals(listOf("first", "failing", "last"), upserts)
        assertTrue(deletions.isEmpty())
        assertEquals(listOf(failing.identity), result.upsertFailures.map { it.identity })
        assertTrue(result.deletionFailures.isEmpty())
    }

    @Test
    fun `successful upserts finish before stale markers are deleted`() {
        val desired = pointSpec("shared")
        val oldType = DynmapMarkerIdentity(DynmapMarkerKind.CIRCLE, "shared")
        val stale = DynmapMarkerIdentity(DynmapMarkerKind.POINT, "stale")
        val operations = mutableListOf<String>()

        val result = reconcileDynmapProjection(
            listOf(desired),
            listOf(oldType, desired.identity, stale),
            { it },
            { operations.add("upsert:${it.identity.kind}:${it.identity.id}") },
            { operations.add("delete:${it.kind}:${it.id}") }
        )

        assertEquals(
            listOf(
                "upsert:POINT:shared",
                "delete:CIRCLE:shared",
                "delete:POINT:stale"
            ),
            operations
        )
        assertTrue(result.upsertFailures.isEmpty())
        assertTrue(result.deletionFailures.isEmpty())
    }

    @Test
    fun `stale deletion failures do not stop later cleanup`() {
        val first = DynmapMarkerIdentity(DynmapMarkerKind.AREA, "first")
        val second = DynmapMarkerIdentity(DynmapMarkerKind.AREA, "second")
        val attempted = mutableListOf<String>()

        val result = reconcileDynmapProjection(
            emptyList(),
            listOf(first, second),
            { it },
            {},
            {
                attempted.add(it.id)
                if (it == first) error("failure")
            }
        )

        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf(first), result.deletionFailures.map { it.identity })
    }

    @Test
    fun `Dynmap refusing marker creation is an upsert failure`() {
        val markerSet = nullReturningProxy<MarkerSet>()
        val markerApi = nullReturningProxy<MarkerAPI>()

        assertFailsWith<IllegalStateException> {
            DynmapRegionRenderer.upsertMarker(
                markerSet,
                markerApi,
                DynmapAreaMarkerSpec(
                    DynmapMarkerIdentity(DynmapMarkerKind.AREA, "area"),
                    "label",
                    "world",
                    doubleArrayOf(0.0, 1.0, 1.0),
                    doubleArrayOf(0.0, 0.0, 1.0),
                    0xffffff
                )
            )
        }
    }

    @Test
    fun `missing Dynmap point icon is an upsert failure`() {
        val markerSet = nullReturningProxy<MarkerSet>()
        val markerApi = nullReturningProxy<MarkerAPI>()

        val error = assertFailsWith<IllegalStateException> {
            DynmapRegionRenderer.upsertMarker(markerSet, markerApi, pointSpec("point"))
        }

        assertTrue(error.message.orEmpty().contains("marker icon"))
    }

    private fun pointSpec(id: String) = DynmapPointMarkerSpec(
        DynmapMarkerIdentity(DynmapMarkerKind.POINT, id),
        id,
        "world",
        0.0,
        64.0,
        0.0
    )

    private fun scope(
        name: String,
        index: Int,
        shape: GeoShape,
        teleportPoint: BlockPos? = null,
        showOnDynmap: Boolean = true
    ) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        teleportPoint,
        geoShape = shape,
        showOnDynmap = showOnDynmap,
        scopeId = ScopeId(generateCompatScopeIdRaw(7, index))
    )

    private inline fun <reified T> nullReturningProxy(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, _, _ -> null } as T
}
