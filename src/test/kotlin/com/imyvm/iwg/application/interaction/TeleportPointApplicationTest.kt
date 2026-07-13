package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TeleportPointApplicationTest {
    @Test
    fun `accessibility mutation validates owner and restores state when save fails`() {
        val scope = assignedScope("scope", 7, 1)
        val region = Region("region", 7, mutableListOf(scope))
        val findScope = { _: AssignedScopeId -> region to scope }

        assertEquals(1, toggleTeleportPointAccessibility(region, scope, findScope) { true })
        assertTrue(scope.isTeleportPointPublic)

        assertEquals(0, toggleTeleportPointAccessibility(region, scope, findScope) { false })
        assertTrue(scope.isTeleportPointPublic)

        assertFailsWith<IllegalArgumentException> {
            toggleTeleportPointAccessibility(Region("other", 8, mutableListOf()), scope, findScope) { true }
        }
    }

    @Test
    fun `accessibility mutation rejects a detached region and scope before saving`() {
        val canonicalScope = assignedScope("scope", 7, 1)
        val canonicalRegion = Region("region", 7, mutableListOf(canonicalScope))
        val detachedScope = assignedScope("scope", 7, 1)
        val detachedRegion = Region("region", 7, mutableListOf(detachedScope))
        var saveCalled = false

        assertFailsWith<IllegalArgumentException> {
            toggleTeleportPointAccessibility(
                detachedRegion,
                detachedScope,
                findScope = { canonicalRegion to canonicalScope }
            ) {
                saveCalled = true
                true
            }
        }

        assertFalse(saveCalled)
        assertFalse(detachedScope.isTeleportPointPublic)
    }

    @Test
    fun `legacy owner resolution accepts only the canonical assigned scope`() {
        val canonical = assignedScope("scope", 7, 1)
        val clone = assignedScope("clone", 7, 1)
        val unassigned = GeoScope(
            "new",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null
        )
        val region = Region("region", 7, mutableListOf(canonical))
        val found = region to canonical

        assertSame(region, resolveTeleportAccessibilityOwner(canonical) { found })
        assertNull(resolveTeleportAccessibilityOwner(clone) { found })
        assertNull(resolveTeleportAccessibilityOwner(canonical) { null })
        assertNull(resolveTeleportAccessibilityOwner(unassigned) {
            error("unassigned scopes must not be looked up")
        })
    }

    private fun assignedScope(name: String, regionId: Int, index: Int) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )
}
