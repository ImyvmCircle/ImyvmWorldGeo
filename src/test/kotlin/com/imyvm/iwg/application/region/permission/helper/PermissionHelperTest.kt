package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.application.interaction.getRegionPermissionValue
import com.imyvm.iwg.application.interaction.getScopePermissionValue
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
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
    private val scope = GeoScope("scope", Identifier.parse("minecraft:overworld"), null, geoShape = null)
    private val region = Region("region", 1, mutableListOf(scope))

    @Test
    fun `uses supplied default when no setting exists`() {
        assertTrue(hasRegionPermission(region, player, PermissionKey.PVP, defaultValue = true))
        assertFalse(hasRegionPermission(region, player, PermissionKey.PVP, defaultValue = false))
    }

    @Test
    fun `personal setting wins over global setting in same container`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)
        region.settings += PermissionSetting(PermissionKey.PVP, true, player)

        assertTrue(hasRegionPermission(region, player, PermissionKey.PVP))
        assertFalse(hasRegionPermission(region, otherPlayer, PermissionKey.PVP))
    }

    @Test
    fun `scope setting wins over region setting`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false, player)
        scope.settings += PermissionSetting(PermissionKey.PVP, true)

        assertTrue(hasScopePermission(region, scope, player, PermissionKey.PVP))
    }

    @Test
    fun `region setting is fallback when scope has no matching setting`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)

        assertFalse(hasScopePermission(region, scope, player, PermissionKey.PVP))
    }

    @Test
    fun `child permission inherits nearest configured parent`() {
        region.settings += PermissionSetting(PermissionKey.BUILD_BREAK, false)
        region.settings += PermissionSetting(PermissionKey.BUILD, true)

        assertTrue(hasRegionPermission(region, player, PermissionKey.BUCKET_BUILD))
        assertFalse(hasRegionPermission(region, player, PermissionKey.BREAK))
    }

    @Test
    fun `explicit child setting wins over parent setting`() {
        region.settings += PermissionSetting(PermissionKey.THROWABLE, false)
        region.settings += PermissionSetting(PermissionKey.EGG_USE, true)

        assertTrue(hasRegionPermission(region, player, PermissionKey.EGG_USE))
    }

    @Test
    fun `API resolver falls back to global setting for a player`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)

        assertFalse(getRegionPermissionValue(region, player, PermissionKey.PVP))
    }

    @Test
    fun `API resolver uses the same parent inheritance as runtime checks`() {
        region.settings += PermissionSetting(PermissionKey.BUILD, false)

        assertFalse(getRegionPermissionValue(region, player, PermissionKey.BUCKET_BUILD))
    }

    @Test
    fun `denial source identifies scope region and default`() {
        scope.settings += PermissionSetting(PermissionKey.PVP, false)
        region.settings += PermissionSetting(PermissionKey.BUILD, false)

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
        val key = ExtensionPermissionKey("test:permission")
        region.settings += ExtensionPermissionSetting(key, false)
        scope.settings += ExtensionPermissionSetting(key, true, player)

        assertTrue(resolveScopePlayerPermission(region, scope, player, key)!!.value)
        assertFalse(resolveScopePlayerPermission(region, scope, otherPlayer, key)!!.value)
    }

    @Test
    fun `explicit queries distinguish global player region and scope`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)
        region.settings += PermissionSetting(PermissionKey.PVP, true, player)
        scope.settings += PermissionSetting(PermissionKey.PVP, true)
        scope.settings += PermissionSetting(PermissionKey.PVP, false, player)

        assertFalse(getRegionPermissionValue(region, PermissionKey.PVP))
        assertTrue(getRegionPermissionValue(region, player, PermissionKey.PVP))
        assertTrue(getScopePermissionValue(region, scope, PermissionKey.PVP))
        assertFalse(getScopePermissionValue(region, scope, player, PermissionKey.PVP))
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
        val otherRegion = Region("other", 2, mutableListOf())

        assertFailsWith<IllegalArgumentException> {
            getScopePermissionDenialSource(otherRegion, scope, player, PermissionKey.PVP)
        }
    }
}
