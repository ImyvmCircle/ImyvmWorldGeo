package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.WorldGeoCanonicalSpaceId
import com.imyvm.iwg.domain.WorldGeoExpectedPermission
import com.imyvm.iwg.domain.WorldGeoExpectedSetting
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceCreation
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceDeletion
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceRange
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationRequest
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationStatus
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.SpaceIdentityAllocationStore
import com.imyvm.iwg.infra.StructuredSpaceMutationStore
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredSpaceMutationServiceTest {
    @AfterTest
    fun tearDown() {
        StructuredSpaceMutationStore.unbindSession()
        SpaceIdentityAllocationStore.unbindSession()
        RegionDatabase.onSave = null
        RegionDatabase.unbindSession()
    }

    @Test
    fun `five structured operations return applied and preserve canonical versions`() =
        withSession { _, region, scope ->
            val parent = canonical(region, scope)
            val created = PlayerInteractionApi.applyStructuredSpaceMutation(
                request("create", WorldGeoExpectedSubSpaceCreation(parent, "plot", rectangle(100, 100, 200, 200)))
            )
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, created.status)
            assertNull(created.beforeSpaceVersion)
            assertNotNull(created.afterSpaceVersion)
            assertNotNull(created.afterSpaceVersion)
            val subSpaceId = assertNotNull(created.canonicalSpaceId?.subSpaceId)
            val target = WorldGeoCanonicalSpaceId(region.numberID, scope.requireAssignedScopeId().raw, subSpaceId)

            val ranged = mutate("range", WorldGeoExpectedSubSpaceRange(target, rectangle(110, 110, 210, 210)))
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, ranged.status)
            assertNotEquals(ranged.beforeSpaceVersion, ranged.afterSpaceVersion)

            val setting = mutate("setting", WorldGeoExpectedSetting(target, RuleKey.PISTON.name, "false"))
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, setting.status)
            assertEquals(
                false,
                RegionDatabase.getSubSpaceById(subSpaceId)!!.third.settings
                    .filterIsInstance<RuleSetting>().single().value
            )

            val permission = mutate(
                "permission",
                WorldGeoExpectedPermission(target, PermissionKey.BUILD.name, false, PLAYER)
            )
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, permission.status)
            assertEquals(
                false,
                RegionDatabase.getSubSpaceById(subSpaceId)!!.third.settings
                    .filterIsInstance<PermissionSetting>().single().value
            )

            val deleted = mutate("delete", WorldGeoExpectedSubSpaceDeletion(target))
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, deleted.status)
            assertNull(deleted.afterSpaceVersion)
            assertNull(RegionDatabase.getSubSpaceById(subSpaceId))
        }

    @Test
    fun `external key replay survives restart and changed expectation conflicts`() =
        withSession { directory, region, scope ->
            val expected = WorldGeoExpectedSubSpaceCreation(
                canonical(region, scope),
                "stable",
                rectangle(100, 100, 200, 200)
            )
            val first = mutate("stable-key", expected)
            val canonicalId = assertNotNull(first.canonicalSpaceId)
            restart(directory)

            val replay = mutate("stable-key", expected)
            val conflict = mutate(
                "stable-key",
                expected.copy(shape = rectangle(300, 300, 400, 400))
            )

            assertEquals(WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED, replay.status)
            assertEquals(canonicalId, replay.canonicalSpaceId)
            assertEquals(first.beforeSpaceVersion, replay.beforeSpaceVersion)
            assertEquals(first.afterSpaceVersion, replay.afterSpaceVersion)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.CONFLICT, conflict.status)
            assertEquals(1, RegionDatabase.getRegionList().single().subSpaces.size)
        }

    @Test
    fun `persistence failure rolls memory and files back without consuming external key`() =
        withSession { _, region, scope ->
            val create = mutate(
                "create-for-failure",
                WorldGeoExpectedSubSpaceCreation(
                    canonical(region, scope),
                    "rollback",
                    rectangle(100, 100, 200, 200)
                )
            )
            val id = assertNotNull(create.canonicalSpaceId)
            val subSpace = RegionDatabase.getSubSpaceById(assertNotNull(id.subSpaceId))!!.third
            val oldParameters = subSpace.geoShape.shapeParameter
            StructuredSpaceMutationStore.failureInjector = { throw IOException("simulated ledger failure") }

            val failed = mutate(
                "failed-range",
                WorldGeoExpectedSubSpaceRange(id, rectangle(300, 300, 400, 400))
            )

            assertEquals(WorldGeoStructuredSpaceMutationStatus.PERSISTENCE_FAILED, failed.status)
            assertEquals(oldParameters, subSpace.geoShape.shapeParameter)
            StructuredSpaceMutationStore.failureInjector = null
            val retry = mutate(
                "failed-range",
                WorldGeoExpectedSubSpaceRange(id, rectangle(300, 300, 400, 400))
            )
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, retry.status)
        }

    @Test
    fun `already matched delete setting and permission are idempotent and invalid target rejects`() =
        withSession { _, region, scope ->
            val regionTarget = WorldGeoCanonicalSpaceId(region.numberID)
            val setting = WorldGeoExpectedSetting(regionTarget, RuleKey.PISTON.name, "false")
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, mutate("set-1", setting).status)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED, mutate("set-2", setting).status)
            val removeSetting = setting.copy(value = null)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, mutate("unset-1", removeSetting).status)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED, mutate("unset-2", removeSetting).status)

            val permission = WorldGeoExpectedPermission(regionTarget, PermissionKey.BUILD.name, false)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.APPLIED, mutate("permission-1", permission).status)
            assertEquals(WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED, mutate("permission-2", permission).status)

            val absent = WorldGeoCanonicalSpaceId(region.numberID, scope.requireAssignedScopeId().raw, 999_999L)
            assertEquals(
                WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED,
                mutate("delete-absent", WorldGeoExpectedSubSpaceDeletion(absent)).status
            )
            assertEquals(
                WorldGeoStructuredSpaceMutationStatus.REJECTED,
                mutate(
                    "invalid",
                    WorldGeoExpectedSetting(WorldGeoCanonicalSpaceId(999_999), RuleKey.PISTON.name, "false")
                ).status
            )
            assertEquals(
                WorldGeoStructuredSpaceMutationStatus.REJECTED,
                StructuredSpaceMutationService.mutate(
                    WorldGeoStructuredSpaceMutationRequest(
                        "Invalid Namespace",
                        "key",
                        setting
                    )
                ).status
            )
        }

    private fun mutate(key: String, expected: com.imyvm.iwg.domain.WorldGeoExpectedSpaceState) =
        StructuredSpaceMutationService.mutate(request(key, expected))

    private fun request(key: String, expected: com.imyvm.iwg.domain.WorldGeoExpectedSpaceState) =
        WorldGeoStructuredSpaceMutationRequest("community", key, expected)

    private fun canonical(region: Region, scope: GeoScope) =
        WorldGeoCanonicalSpaceId(region.numberID, scope.requireAssignedScopeId().raw)

    private fun restart(directory: Path) {
        StructuredSpaceMutationStore.unbindSession()
        SpaceIdentityAllocationStore.unbindSession()
        RegionDatabase.unbindSession()
        RegionDatabase.bindSession(directory)
        StructuredSpaceMutationStore.bindSession(directory)
        SpaceIdentityAllocationStore.bindSession(directory, regionHours = 0, scopeHours = 0L)
    }

    private fun withSession(block: (Path, Region, GeoScope) -> Unit) {
        val directory = Files.createTempDirectory("iwg-structured-mutation-test")
        try {
            RegionDatabase.bindSession(directory)
            val scope = GeoScope(
                "scope",
                Identifier.parse("minecraft:overworld"),
                null,
                geoShape = rectangle(0, 0, 1_000, 1_000),
                scopeId = ScopeId(generateCompatScopeIdRaw(7, 0))
            )
            val region = Region("region", 7, mutableListOf(scope))
            RegionDatabase.addRegion(region)
            RegionDatabase.saveForShutdown()
            StructuredSpaceMutationStore.bindSession(directory)
            SpaceIdentityAllocationStore.bindSession(directory, regionHours = 0, scopeHours = 0L)
            block(directory, region, scope)
        } finally {
            StructuredSpaceMutationStore.unbindSession()
            SpaceIdentityAllocationStore.unbindSession()
            RegionDatabase.unbindSession()
            directory.toFile().deleteRecursively()
        }
    }

    private fun rectangle(west: Int, north: Int, east: Int, south: Int): GeoShape =
        GeoShape.rectangle(GeoPoint(west, north), GeoPoint(east, south))

    private companion object {
        val PLAYER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
