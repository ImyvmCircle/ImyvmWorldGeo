package com.imyvm.iwg.application.selection.display

import net.minecraft.server.level.ServerPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionDisplayCompatibilityTest {
    @Test
    fun `legacy clear selection display JVM descriptor remains available`() {
        val method = Class.forName(
            "com.imyvm.iwg.application.selection.display.SelectionPillarEmitterKt"
        ).getDeclaredMethod("clearSelectionDisplay", ServerPlayer::class.java)

        assertEquals(Void.TYPE, method.returnType)
    }
}
