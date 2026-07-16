package com.imyvm.iwg.application.region.permission

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionEventTargetTest {
    private val clicked = BlockPos(10, 64, 20)

    @Test
    fun `adjacent placement checks the created entity position`() {
        assertEquals(BlockPos(11, 64, 20), adjacentTarget(clicked, Direction.EAST))
    }

    @Test
    fun `filled bucket follows vanilla clicked and adjacent target rules`() {
        assertEquals(clicked, filledBucketTarget(clicked, Direction.EAST, true, false))
        assertEquals(BlockPos(11, 64, 20), filledBucketTarget(clicked, Direction.EAST, false, false))
        assertEquals(BlockPos(11, 64, 20), filledBucketTarget(clicked, Direction.EAST, true, true))
    }

    @Test
    fun `ignition checks the block vanilla actually mutates`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        assertEquals(clicked, igniteTarget(clicked, Direction.UP, Blocks.TNT.defaultBlockState()))
        assertEquals(
            BlockPos(10, 65, 20),
            igniteTarget(clicked, Direction.UP, Blocks.STONE.defaultBlockState())
        )
    }
}
