package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.application.interaction.getRegionPermissionValue
import com.imyvm.iwg.application.interaction.getScopePermissionValue
import com.imyvm.iwg.application.interaction.getSubSpacePermissionValue
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PermissionHelperTest {
    private val player = UUID.randomUUID()
    private val otherPlayer = UUID.randomUUID()
    private val scope = GeoScope(
        "scope",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(1, 0))
    )
    private val region = Region("region", 1, mutableListOf(scope))

    @Test
    fun `uses supplied default when no setting exists`() {
        assertTrue(hasRegionPermission(region, player, PermissionKey.PVP, defaultValue = true))
        assertFalse(hasRegionPermission(region, player, PermissionKey.PVP, defaultValue = false))
    }

    @Test
    fun `personal setting wins over global setting in same container`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, true, player))

        assertTrue(hasRegionPermission(region, player, PermissionKey.PVP))
        assertFalse(hasRegionPermission(region, otherPlayer, PermissionKey.PVP))
    }

    @Test
    fun `scope setting wins over region setting`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false, player))
        scope.settingStore.put(PermissionSetting(PermissionKey.PVP, true))

        assertTrue(hasScopePermission(region, scope, player, PermissionKey.PVP))
    }

    @Test
    fun `region setting is fallback when scope has no matching setting`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))

        assertFalse(hasScopePermission(region, scope, player, PermissionKey.PVP))
    }

    @Test
    fun `child permission inherits nearest configured parent`() {
        region.settingStore.put(PermissionSetting(PermissionKey.BUILD_BREAK, false))
        region.settingStore.put(PermissionSetting(PermissionKey.BUILD, true))

        assertTrue(hasRegionPermission(region, player, PermissionKey.BUCKET_BUILD))
        assertFalse(hasRegionPermission(region, player, PermissionKey.BREAK))
    }

    @Test
    fun `explicit child setting wins over parent setting`() {
        region.settingStore.put(PermissionSetting(PermissionKey.THROWABLE, false))
        region.settingStore.put(PermissionSetting(PermissionKey.EGG_USE, true))

        assertTrue(hasRegionPermission(region, player, PermissionKey.EGG_USE))
    }

    @Test
    fun `API resolver falls back to global setting for a player`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))

        assertFalse(getRegionPermissionValue(region, player, PermissionKey.PVP))
    }

    @Test
    fun `API resolver uses the same parent inheritance as runtime checks`() {
        region.settingStore.put(PermissionSetting(PermissionKey.BUILD, false))

        assertFalse(getRegionPermissionValue(region, player, PermissionKey.BUCKET_BUILD))
    }

    @Test
    fun `denial source identifies scope region and default`() {
        scope.settingStore.put(PermissionSetting(PermissionKey.PVP, false))
        region.settingStore.put(PermissionSetting(PermissionKey.BUILD, false))

        assertEquals(PermissionDenialSource.AtScope, getScopePermissionDenialSource(region, scope, player, PermissionKey.PVP))
        assertEquals(PermissionDenialSource.AtRegion, getScopePermissionDenialSource(region, scope, player, PermissionKey.BUILD))
        assertEquals(PermissionDenialSource.ByDefault, getScopePermissionDenialSource(region, scope, player, PermissionKey.FLY, false))
    }

    @Test
    fun `denial context names the container that rejected the action`() {
        assertEquals("Scope &bscope&7 of Region &bregion&7", buildScopePermissionDenialContext(region, scope, PermissionDenialSource.AtScope))
        assertEquals("Region &bregion&7", buildScopePermissionDenialContext(region, scope, PermissionDenialSource.AtRegion))
    }

    @Test
    fun `extension permissions use the same container and personal precedence`() {
        ExtensionSettingRegistry.registerPermissionKey("test:permission", true)
        val key = ExtensionSettingRegistry.permissionKey("test:permission")
        region.settingStore.put(ExtensionPermissionSetting(key, false))
        scope.settingStore.put(ExtensionPermissionSetting(key, true, player))

        assertTrue(resolveScopePlayerPermission(region, scope, player, key)!!.value)
        assertFalse(resolveScopePlayerPermission(region, scope, otherPlayer, key)!!.value)
    }

    @Test
    fun `explicit queries distinguish global player region and scope`() {
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, false))
        region.settingStore.put(PermissionSetting(PermissionKey.PVP, true, player))
        scope.settingStore.put(PermissionSetting(PermissionKey.PVP, true))
        scope.settingStore.put(PermissionSetting(PermissionKey.PVP, false, player))

        assertFalse(getRegionPermissionValue(region, PermissionKey.PVP))
        assertTrue(getRegionPermissionValue(region, player, PermissionKey.PVP))
        assertTrue(getScopePermissionValue(region, scope, PermissionKey.PVP))
        assertFalse(getScopePermissionValue(region, scope, player, PermissionKey.PVP))
    }

    @Test
    fun `subspace permission wins over scope and region settings`() {
        val parentScope = GeoScope(
            "parent",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(10, 10)),
            scopeId = ScopeId(generateCompatScopeIdRaw(3, 0))
        )
        val parentRegion = Region("parent-region", 3, mutableListOf(parentScope))
        val subSpace = SubSpace(
            1,
            "plot",
            parentScope.requireAssignedScopeId(),
            parentScope.worldId,
            GeoShape.rectangle(GeoPoint(1, 1), GeoPoint(2, 2))
        )
        parentRegion.addSubSpace(subSpace)
        parentRegion.settingStore.put(PermissionSetting(PermissionKey.PVP, true, player))
        parentScope.settingStore.put(PermissionSetting(PermissionKey.PVP, true))
        subSpace.settingStore.put(PermissionSetting(PermissionKey.PVP, false, player))

        assertFalse(getSubSpacePermissionValue(parentRegion, parentScope, subSpace, player, PermissionKey.PVP))
        assertTrue(getSubSpacePermissionValue(parentRegion, parentScope, subSpace, otherPlayer, PermissionKey.PVP))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy API rejects scope without region`() {
        assertFailsWith<IllegalArgumentException> {
            RegionDataApi.getPermissionValueRegion(null, scope, player, PermissionKey.PVP)
        }
    }

    @Test
    fun `scope query rejects a scope owned by another region`() {
        val otherScope = GeoScope(
            "other-scope",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            scopeId = ScopeId(generateCompatScopeIdRaw(2, 0))
        )
        val otherRegion = Region("other", 2, mutableListOf(otherScope))

        assertFailsWith<IllegalArgumentException> {
            getScopePermissionDenialSource(otherRegion, scope, player, PermissionKey.PVP)
        }
    }

    @Test
    fun `legacy settings getter returns a detached snapshot`() {
        region.settings.add(PermissionSetting(PermissionKey.PVP, false))

        assertTrue(hasRegionPermission(region, player, PermissionKey.PVP))
        assertTrue(region.settings.isEmpty())
    }
}
