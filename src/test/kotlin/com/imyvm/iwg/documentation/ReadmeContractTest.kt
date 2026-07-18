package com.imyvm.iwg.documentation

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.TimedEffect
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.inter.api.ScopeDeleteResult
import com.imyvm.iwg.inter.api.SettingAddResult
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadmeContractTest {
    private val readme: String by lazy {
        val path = Path.of("README.md").toAbsolutePath()
        check(Files.isRegularFile(path)) { "README.md is missing at $path" }
        Files.readString(path)
    }

    @Test
    fun `README excludes stale mappings deprecated facades and obsolete command grammar`() {
        val forbidden = listOf(
            "ServerPlayerEntity",
            "Region.Companion.GeoShape",
            "`World`",
            "AnimalEntity",
            "shapeParameter",
            "src/main/kotlin/com/imyvm/iwg/inter/api",
            "parseMarkFromRegionId",
            "ENTER_ENABLED",
            "EXIT_ENABLED",
            "getPermissionValueRegion(",
            "getRuleValueForRegion(",
            "getEffectValueForRegion(",
            "getActiveEffectsForRegion(",
            "addSettingRegion(",
            "removeSettingRegion(",
            "addEntryExitSettingRegion(",
            "/imyvmWorldGeo create [shapeType]",
            "/imyvmWorldGeo addScope [shapeType]",
            "omitted or invalid"
        )

        forbidden.forEach { token ->
            assertFalse(token in readme, "README still contains stale token: $token")
        }
        assertFalse(
            Regex("(?<!Assigned)\\bScopeId\\b").containsMatchIn(readme),
            "README should recommend AssignedScopeId instead of legacy ScopeId"
        )
    }

    @Test
    fun `README records current typed API precedence and entry exit contracts`() {
        val required = listOf(
            "docs/addon-api-compatibility.md",
            "ServerPlayer",
            "Level",
            "GeoShape.rectangle",
            "deleteScopeWithResult",
            "SettingAddResult",
            "SettingRemoveResult",
            "addScopePermission",
            "getScopePlayerPermissionValue",
            "AssignedScopeId",
            "getScopeByAssignedIdRaw",
            "createTimedEffectOverlayRaw",
            "scope personal → region personal → timed overlay → scope global → region global",
            "ENTRY_EXIT_MESSAGE_ENABLED",
            "ENTER_MESSAGE",
            "EXIT_MESSAGE"
        )

        required.forEach { token ->
            assertTrue(token in readme, "README is missing required contract: $token")
        }
    }

    @Test
    fun `README records the current representative command grammar`() {
        val required = listOf(
            "/imyvmWorldGeo create [name]",
            "/imyvmWorldGeo addScope <regionIdentifier> [scopeName]",
            "/imyvmWorldGeo select modifyScope <regionIdentifier> <scopeName>",
            "/imyvmWorldGeo modifyScope <regionIdentifier> <scopeName> [newName]",
            "/imyvmWorldGeo teleportPoint set [<x> <y> <z>]",
            "/imyvmWorldGeo teleportPoint reset`",
            "/imyvmWorldGeo teleportPoint inquiry`"
        )

        required.forEach { command ->
            assertTrue(command in readme, "README is missing command grammar: $command")
        }
    }

    @Suppress("unused")
    private fun compileRepresentativeKotlinCalls(
        player: ServerPlayer,
        region: Region,
        scope: GeoScope,
        targetPlayer: UUID,
        startsAt: Long,
        expiresAt: Long
    ) {
        PlayerInteractionApi.startSelection(player, GeoShapeType.CIRCLE)
        val created: Region? = PlayerInteractionApi.createAndGetRegion(player, "Market", 0)
        val shape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(10, 10))
        PlayerInteractionApi.replaceScopeShape(player, region, scope, shape)

        val deleted: ScopeDeleteResult =
            PlayerInteractionApi.deleteScopeWithResult(player, region, scope)
        val added: SettingAddResult = PlayerInteractionApi.addScopePermission(
            player, region, scope, PermissionKey.BUILD, false, targetPlayer
        )
        val allowed: Boolean = RegionDataApi.getScopePlayerPermissionValue(
            region, scope, targetPlayer, PermissionKey.BUILD
        )

        val scopeId = RegionDataApi.getAssignedScopeIdOrNull(scope) ?: return
        RegionDataApi.getScopeByAssignedId(scopeId)
        RegionDataApi.getAssignedScopeOwnershipHistory(scopeId)
        val overlay = RegionDataApi.createTimedEffectOverlay(
            "event:festival",
            scopeId,
            listOf(TimedEffect(EffectKey.SPEED, 1)),
            startsAt,
            expiresAt,
            0,
            "documentation"
        )
        RegionDataApi.applyTimedEffectOverlay(overlay)
        RegionDataApi.queryOverlay(scopeId)
        RegionDataApi.clearTimedEffectOverlay(scopeId, overlay.overlayId)

        listOf(created, deleted, added, allowed)
    }
}
