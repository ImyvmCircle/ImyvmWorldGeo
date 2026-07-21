package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.component.SelectionState
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandPointUndoTest {
    @Test
    fun `undo can remove the final selected point`() {
        val point = BlockPos(10, 64, 20)
        val state = SelectionState(mutableListOf(point))

        assertEquals(point, undoLastSelectionPoint(state))
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun `undo rejects only when selection is already empty`() {
        val state = SelectionState()

        assertNull(undoLastSelectionPoint(state))
        assertTrue(state.points.isEmpty())
    }
}
