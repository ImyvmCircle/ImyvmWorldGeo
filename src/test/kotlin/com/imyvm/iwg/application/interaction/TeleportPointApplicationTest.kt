package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TeleportPointApplicationTest {
    @Test
    fun `teleport point inquiry formats canonical region and scope names`() {
        val present = formatTeleportPointInquiry("market", "spawn", BlockPos(1, 2, 3)).string
        assertContains(present, "market")
        assertContains(present, "spawn")
        assertContains(present, "1")
        assertContains(present, "2")
        assertContains(present, "3")
        assertFalse(present.contains("{3}"))
        assertFalse(present.contains("{5}"))

        val absent = formatTeleportPointInquiry("market", "spawn", null).string
        assertContains(absent, "market")
        assertContains(absent, "spawn")
        assertFalse(absent.contains("Region@"))
    }

    @Test
    fun `public teleport selector skips private and unset scopes`() {
        val privateScope = assignedScope("private", 7, 1, BlockPos.ZERO, false)
        val unsetPublicScope = assignedScope("unset", 7, 2, null, true)
        val publicScope = assignedScope("public", 7, 3, BlockPos(1, 2, 3), true)
        val laterPublicScope = assignedScope("later", 7, 4, BlockPos(4, 5, 6), true)
        val region = Region("region", 7, mutableListOf(privateScope, unsetPublicScope, publicScope, laterPublicScope))

        assertFalse(isTeleportPointPubliclyAccessible(privateScope))
        assertTrue(isTeleportPointPubliclyAccessible(publicScope))
        assertSame(publicScope, findPublicTeleportScope(region))
        assertNull(findPublicTeleportScope(Region("empty", 7, mutableListOf(privateScope, unsetPublicScope))))
    }

    @Test
    fun `accessibility mutation validates owner and restores state when save fails`() {
        val scope = assignedScope("scope", 7, 1)
        val region = Region("region", 7, mutableListOf(scope))
        val findScope = { _: AssignedScopeId -> region to scope }

        assertEquals(1, toggleTeleportPointAccessibility(region, scope, findScope) { true })
        assertTrue(scope.isTeleportPointPublic)

        assertEquals(0, toggleTeleportPointAccessibility(region, scope, findScope) { false })
        assertTrue(scope.isTeleportPointPublic)

        val otherScope = assignedScope("other-scope", 8, 1)
        assertFailsWith<IllegalArgumentException> {
            toggleTeleportPointAccessibility(Region("other", 8, mutableListOf(otherScope)), scope, findScope) { true }
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

    private fun assignedScope(
        name: String,
        regionId: Int,
        index: Int,
        teleportPoint: BlockPos? = null,
        isTeleportPointPublic: Boolean = false
    ) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        teleportPoint,
        isTeleportPointPublic,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )
}
