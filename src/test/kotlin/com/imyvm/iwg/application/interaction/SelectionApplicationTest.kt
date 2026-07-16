package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SelectionState
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionApplicationTest {
    private val overworld = Identifier.parse("minecraft:overworld")
    private val nether = Identifier.parse("minecraft:the_nether")

    @AfterTest
    fun clearSelectionState() {
        clearAllSelections()
    }

    @Test
    fun `creation and modification selection modes cannot cross over`() {
        val first = assignedScope("first", 7, 1)
        val second = assignedScope("second", 7, 2)
        val inferred = SelectionState()
        val fixed = SelectionState(hypotheticalShape = HypotheticalShape.Normal(GeoShapeType.CIRCLE))
        val modifying = SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(first))

        assertTrue(isCreationSelection(inferred))
        assertTrue(isCreationSelection(fixed))
        assertFalse(isCreationSelection(modifying))
        assertTrue(isModifySelectionFor(modifying, first))
        assertFalse(isModifySelectionFor(modifying, second))
        assertFalse(isModifySelectionFor(inferred, first))
    }

    @Test
    fun `reset with shape cannot replace modification mode or clear its points`() {
        val scope = assignedScope("scope", 7, 1)
        val state = SelectionState(
            mutableListOf(BlockPos.ZERO),
            HypotheticalShape.ModifyExisting(scope)
        )

        assertFalse(resetSelectionState(state, GeoShapeType.CIRCLE))
        assertEquals(listOf(BlockPos.ZERO), state.points)
        assertTrue(isModifySelectionFor(state, scope))

        assertTrue(resetSelectionState(state, null))
        assertTrue(state.points.isEmpty())
        assertTrue(isModifySelectionFor(state, scope))
    }

    @Test
    fun `modify start accepts only the canonical assigned scope with a supported shape in the player world`() {
        val canonical = assignedScope("scope", 7, 1)
        val region = Region("region", 7, mutableListOf(canonical))
        val found = region to canonical
        val clone = assignedScope("clone", 7, 1)
        val unassigned = scope("new", overworld, rectangle())
        val noShape = assignedScope("no-shape", 7, 2, shape = null)
        val unknown = assignedScope("unknown", 7, 3, shape = GeoShape(GeoShapeType.UNKNOWN, mutableListOf()))

        assertNull(validateModifySelectionStartTarget(canonical, overworld) { found })
        assertEquals(
            ModifySelectionTargetError.INVALID_TARGET,
            validateModifySelectionStartTarget(clone, overworld) { found }
        )
        assertEquals(
            ModifySelectionTargetError.INVALID_TARGET,
            validateModifySelectionStartTarget(unassigned, overworld) { error("unassigned scope must not be looked up") }
        )
        assertEquals(
            ModifySelectionTargetError.INVALID_TARGET,
            validateModifySelectionStartTarget(noShape, overworld) { region to noShape }
        )
        assertEquals(
            ModifySelectionTargetError.INVALID_TARGET,
            validateModifySelectionStartTarget(unknown, overworld) { region to unknown }
        )
        assertEquals(
            ModifySelectionTargetError.WRONG_WORLD,
            validateModifySelectionStartTarget(canonical, nether) { found }
        )
    }

    @Test
    fun `modify application target requires the canonical owner and exact scope`() {
        val scope = assignedScope("scope", 7, 1)
        val region = Region("region", 7, mutableListOf(scope))
        val otherRegion = Region("other", 8, mutableListOf(assignedScope("other-scope", 8, 1)))

        assertNull(validateModifySelectionTarget(region, scope, overworld) { region to scope })
        assertEquals(
            ModifySelectionTargetError.INVALID_TARGET,
            validateModifySelectionTarget(otherRegion, scope, overworld) { region to scope }
        )
    }

    @Test
    fun `lifecycle cleanup removes only matching selection state`() {
        val firstScope = assignedScope("first", 7, 1)
        val secondScope = assignedScope("second", 7, 2)
        val firstPlayer = UUID.randomUUID()
        val secondPlayer = UUID.randomUUID()
        val normalPlayer = UUID.randomUUID()
        val clonePlayer = UUID.randomUUID()
        val firstScopeClone = assignedScope("first-clone", 7, 1)
        ImyvmWorldGeo.pointSelectingPlayers[firstPlayer] =
            SelectionState(mutableListOf(BlockPos.ZERO), HypotheticalShape.ModifyExisting(firstScope))
        ImyvmWorldGeo.pointSelectingPlayers[secondPlayer] =
            SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(secondScope))
        ImyvmWorldGeo.pointSelectingPlayers[normalPlayer] = SelectionState()
        ImyvmWorldGeo.pointSelectingPlayers[clonePlayer] =
            SelectionState(hypotheticalShape = HypotheticalShape.ModifyExisting(firstScopeClone))

        clearSelectionsReferencing(listOf(firstScope))

        assertFalse(ImyvmWorldGeo.pointSelectingPlayers.containsKey(firstPlayer))
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(secondPlayer))
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(normalPlayer))
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.containsKey(clonePlayer))
        assertTrue(clearPlayerSelection(secondPlayer))
        assertFalse(clearPlayerSelection(secondPlayer))

        clearAllSelections()
        assertTrue(ImyvmWorldGeo.pointSelectingPlayers.isEmpty())
    }

    private fun assignedScope(
        name: String,
        regionId: Int,
        index: Int,
        shape: GeoShape? = rectangle()
    ) = GeoScope(
        name,
        overworld,
        null,
        geoShape = shape,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )

    private fun scope(name: String, worldId: Identifier, shape: GeoShape?) =
        GeoScope(name, worldId, null, geoShape = shape)

    private fun rectangle() = GeoShape(GeoShapeType.RECTANGLE, mutableListOf(0, 0, 100, 100))
}
