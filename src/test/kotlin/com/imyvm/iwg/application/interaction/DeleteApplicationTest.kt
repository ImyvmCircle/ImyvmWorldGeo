package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.api.RegionDeleteResult
import com.imyvm.iwg.inter.api.ScopeDeleteResult
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeleteApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        clearAllSelections()
        EffectOverlayService.clearAll()
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `last scope is rejected before mutation and persistence`() {
        val scope = scope("only", 7, 0)
        val region = bind(Region("region", 7, mutableListOf(scope)))
        val playerId = trackSelection(scope)
        storeOverlay(scope)
        var saveCalls = 0

        val result = deleteScope(region, scope) {
            saveCalls++
            true
        }

        assertEquals(ScopeDeleteResult.LAST_SCOPE, result)
        assertEquals(0, saveCalls)
        assertSame(scope, region.scopes.single())
        assertTrue(EffectOverlayService.queryOverlay(scope.requireAssignedScopeId(), 1).isNotEmpty())
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerId))
    }

    @Test
    fun `scope persistence failure restores order and transient state`() {
        val first = scope("first", 7, 0)
        val removed = scope("removed", 7, 1)
        val region = bind(Region("region", 7, mutableListOf(first, removed)))
        val playerId = trackSelection(removed)
        storeOverlay(removed)

        val result = deleteScope(region, removed) { false }

        assertEquals(ScopeDeleteResult.PERSISTENCE_FAILED, result)
        assertSame(first, region.scopes[0])
        assertSame(removed, region.scopes[1])
        assertTrue(EffectOverlayService.queryOverlay(removed.requireAssignedScopeId(), 1).isNotEmpty())
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerId))
    }

    @Test
    fun `successful scope deletion clears transient state after persistence`() {
        val retained = scope("retained", 7, 0)
        val removed = scope("removed", 7, 1)
        val region = bind(Region("region", 7, mutableListOf(retained, removed)))
        val playerId = trackSelection(removed)
        storeOverlay(removed)

        val result = deleteScope(region, removed) { true }

        assertEquals(ScopeDeleteResult.SUCCESS, result)
        assertSame(retained, region.scopes.single())
        assertTrue(EffectOverlayService.queryOverlay(removed.requireAssignedScopeId(), 1).isEmpty())
        assertTrue(!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerId))
    }

    @Test
    fun `scope deletion rejects detached and foreign targets before persistence`() {
        val canonical = scope("canonical", 7, 0)
        val retained = scope("retained", 7, 1)
        val region = bind(Region("region", 7, mutableListOf(canonical, retained)))
        val detached = scope("canonical", 7, 0)
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            deleteScope(region, detached) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertEquals(2, region.scopes.size)
    }

    @Test
    fun `region persistence failure restores database and transient state`() {
        val scope = scope("scope", 7, 0)
        val region = bind(Region("region", 7, mutableListOf(scope)))
        val playerId = trackSelection(scope)
        storeOverlay(scope)

        val result = deleteRegion(region) { false }

        assertEquals(RegionDeleteResult.PERSISTENCE_FAILED, result)
        assertSame(region, RegionDatabase.getRegionList().single())
        assertTrue(EffectOverlayService.queryOverlay(scope.requireAssignedScopeId(), 1).isNotEmpty())
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerId))
    }

    @Test
    fun `successful region deletion clears transient state after persistence`() {
        val scope = scope("scope", 7, 0)
        val region = bind(Region("region", 7, mutableListOf(scope)))
        val playerId = trackSelection(scope)
        storeOverlay(scope)

        val result = deleteRegion(region) { true }

        assertEquals(RegionDeleteResult.SUCCESS, result)
        assertTrue(RegionDatabase.getRegionList().isEmpty())
        assertTrue(EffectOverlayService.queryOverlay(scope.requireAssignedScopeId(), 1).isEmpty())
        assertTrue(!ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerId))
    }

    @Test
    fun `region deletion rejects detached target before persistence`() {
        val canonical = bind(Region("region", 7, mutableListOf(scope("scope", 7, 0))))
        val detached = Region("region", 7, mutableListOf(scope("scope", 7, 0)))
        var saveCalls = 0

        assertFailsWith<IllegalArgumentException> {
            deleteRegion(detached) {
                saveCalls++
                true
            }
        }

        assertEquals(0, saveCalls)
        assertSame(canonical, RegionDatabase.getRegionList().single())
    }

    @Test
    fun `scope deletion revalidates ownership after concurrent region deletion`() {
        val removed = scope("removed", 7, 0)
        val retained = scope("retained", 7, 1)
        val region = bind(Region("region", 7, mutableListOf(removed, retained)))
        val regionRemoved = CountDownLatch(1)
        val finishRegionSave = CountDownLatch(1)
        val scopeStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val regionDeletion = executor.submit<RegionDeleteResult> {
                deleteRegion(region) {
                    regionRemoved.countDown()
                    check(finishRegionSave.await(5, TimeUnit.SECONDS))
                    true
                }
            }
            assertTrue(regionRemoved.await(5, TimeUnit.SECONDS))

            val scopeDeletion = executor.submit<ScopeDeleteResult> {
                scopeStarted.countDown()
                deleteScope(region, removed) { error("stale scope must not be persisted") }
            }
            assertTrue(scopeStarted.await(5, TimeUnit.SECONDS))
            finishRegionSave.countDown()

            assertEquals(RegionDeleteResult.SUCCESS, regionDeletion.get(5, TimeUnit.SECONDS))
            val failure = assertFailsWith<ExecutionException> {
                scopeDeletion.get(5, TimeUnit.SECONDS)
            }
            assertTrue(failure.cause is IllegalArgumentException)
            assertTrue(RegionDatabase.getRegionList().isEmpty())
        } finally {
            finishRegionSave.countDown()
            executor.shutdownNow()
        }
    }

    private fun bind(region: Region): Region {
        val directory = Files.createTempDirectory("iwg-delete-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
        RegionDatabase.addRegion(region)
        return region
    }

    private fun scope(name: String, regionId: Int, index: Int) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )

    private fun trackSelection(scope: GeoScope): UUID = UUID.randomUUID().also { playerId ->
        ImyvmWorldGeo.pointSelectingPlayers[playerId] =
            SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(scope))
    }

    private fun storeOverlay(scope: GeoScope) {
        val scopeId = scope.requireAssignedScopeId()
        EffectOverlayService.applyTimedEffectOverlay(
            TimedEffectOverlay(
                "overlay",
                scopeId.raw,
                listOf(TimedEffect(EffectKey.SPEED, 1)),
                0,
                Long.MAX_VALUE,
                0,
                "test"
            )
        ) { it == scopeId }
    }
}
