package com.imyvm.iwg.entrypoint.register

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.TimedEffectOverlay
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataLoadSaveRegisterTest {
    private val scopeId = ScopeId(generateCompatScopeIdRaw(7, 0))

    @Test
    fun `opening and closing a session clears transient state`() = withTempDirectory { directory ->
        storeOverlayWithoutDatabaseCheck()

        openRegionSession(directory)
        RegionDatabase.addRegion(regionWithScope())

        assertTrue(EffectOverlayService.queryOverlay(scopeId, 1).isEmpty())
        ImyvmWorldGeo.locationActionBarEnabledPlayers.add(UUID.randomUUID())

        closeRegionSession()

        assertFalse(RegionDatabase.hasActiveSession())
        assertTrue(ImyvmWorldGeo.locationActionBarEnabledPlayers.isEmpty())
    }

    @Test
    fun `closing a session is atomic with concurrent addon overlay apply`() = withTempDirectory { directory ->
        openRegionSession(directory)
        RegionDatabase.addRegion(regionWithScope())
        val existenceChecked = CountDownLatch(1)
        val continueApply = CountDownLatch(1)
        val closeCompleted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val apply = executor.submit<String> {
                EffectOverlayService.applyTimedEffectOverlay(overlay()) { assignedScopeId ->
                    val exists = RegionDatabase.getScopeByAssignedId(assignedScopeId) != null
                    existenceChecked.countDown()
                    check(continueApply.await(5, TimeUnit.SECONDS))
                    exists
                }
            }
            assertTrue(existenceChecked.await(5, TimeUnit.SECONDS))

            val close = executor.submit {
                closeRegionSession()
                closeCompleted.countDown()
            }
            assertFalse(closeCompleted.await(200, TimeUnit.MILLISECONDS))

            continueApply.countDown()
            apply.get(5, TimeUnit.SECONDS)
            close.get(5, TimeUnit.SECONDS)

            assertFalse(RegionDatabase.hasActiveSession())
            assertTrue(EffectOverlayService.queryOverlay(scopeId, 1).isEmpty())
            assertFailsWith<IllegalArgumentException> {
                EffectOverlayService.applyTimedEffectOverlay(overlay())
            }
        } finally {
            continueApply.countDown()
            executor.shutdownNow()
        }
    }

    private fun regionWithScope(): Region {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            scopeId = scopeId
        )
        return Region("region", 7, mutableListOf(scope))
    }

    private fun storeOverlayWithoutDatabaseCheck() {
        EffectOverlayService.applyTimedEffectOverlay(overlay()) { true }
    }

    private fun overlay() = TimedEffectOverlay(
        "overlay",
        scopeId.raw,
        listOf(TimedEffect(EffectKey.SPEED, 1)),
        0,
        Long.MAX_VALUE,
        0,
        "test"
    )

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iwg-session-lifecycle-test")
        try {
            block(directory)
        } finally {
            closeRegionSession()
            directory.toFile().deleteRecursively()
        }
    }
}
