package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.resources.Identifier
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionAreaTest {
    @Test
    fun `total area rounding is locale independent`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val scope = GeoScope(
                "circle",
                Identifier.parse("minecraft:overworld"),
                null,
                geoShape = GeoShape(GeoShapeType.CIRCLE, mutableListOf(0, 0, 1))
            )

            assertEquals(3.14, Region("region", 1, mutableListOf(scope)).calculateTotalArea())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
