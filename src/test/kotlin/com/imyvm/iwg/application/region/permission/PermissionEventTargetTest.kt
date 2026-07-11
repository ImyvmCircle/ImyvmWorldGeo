package com.imyvm.iwg.application.region.permission

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionEventTargetTest {
    private val clicked = BlockPos(10, 64, 20)

    @Test
    fun `adjacent placement checks the created entity position`() {
        assertEquals(BlockPos(11, 64, 20), adjacentTarget(clicked, Direction.EAST))
    }

    @Test
    fun `ignition checks clicked block only when vanilla lights it directly`() {
        assertEquals(clicked, igniteTarget(clicked, Direction.UP, true))
        assertEquals(BlockPos(10, 65, 20), igniteTarget(clicked, Direction.UP, false))
    }
}
