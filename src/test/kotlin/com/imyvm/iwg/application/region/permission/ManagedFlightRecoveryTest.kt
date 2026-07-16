package com.imyvm.iwg.application.region.permission

import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagedFlightRecoveryTest {
    @Test
    fun `codec round trips stable recovery names`() {
        for (recovery in ManagedFlightRecovery.entries) {
            val encoded = MANAGED_FLIGHT_RECOVERY_CODEC
                .encodeStart(JsonOps.INSTANCE, recovery)
                .result()
                .orElseThrow()
            val decoded = MANAGED_FLIGHT_RECOVERY_CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow()

            assertEquals(recovery.serializedName, encoded.asString)
            assertEquals(recovery, decoded)
        }
    }

    @Test
    fun `unknown future recovery name fails safe to landing required`() {
        val decoded = MANAGED_FLIGHT_RECOVERY_CODEC
            .parse(JsonOps.INSTANCE, JsonPrimitive("future_state"))
            .result()
            .orElseThrow()

        assertEquals(ManagedFlightRecovery.LANDING_REQUIRED, decoded)
    }
}
