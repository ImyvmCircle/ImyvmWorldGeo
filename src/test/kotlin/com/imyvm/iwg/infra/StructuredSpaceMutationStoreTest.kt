package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StructuredSpaceMutationStoreTest {
    @AfterTest
    fun tearDown() {
        StructuredSpaceMutationStore.unbindSession()
        RegionDatabase.unbindSession()
    }

    @Test
    fun `malformed evidence is rejected without replacement`() {
        val directory = Files.createTempDirectory("iwg-structured-store-test")
        try {
            RegionDatabase.bindSession(directory)
            RegionDatabase.addRegion(region())
            RegionDatabase.saveForShutdown()
            RegionDatabase.unbindSession()
            val path = directory.resolve("iwg_structured_space_mutations.json")
            val malformed = "{}"
            Files.writeString(path, malformed)
            RegionDatabase.bindSession(directory)

            assertFailsWith<IOException> { StructuredSpaceMutationStore.bindSession(directory) }
            assertEquals(malformed, Files.readString(path))
        } finally {
            StructuredSpaceMutationStore.unbindSession()
            RegionDatabase.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `orphan evidence is rejected when region database is missing`() {
        val directory = Files.createTempDirectory("iwg-structured-orphan-test")
        try {
            val path = directory.resolve("iwg_structured_space_mutations.json")
            Files.writeString(path, "{}")

            assertFailsWith<IOException> { RegionDatabase.bindSession(directory) }
            assertEquals("{}", Files.readString(path))
        } finally {
            RegionDatabase.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }

    private fun region(): Region {
        val scope = GeoScope(
            "scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(1_000, 1_000)),
            scopeId = ScopeId(generateCompatScopeIdRaw(7, 0))
        )
        return Region("region", 7, mutableListOf(scope))
    }
}
