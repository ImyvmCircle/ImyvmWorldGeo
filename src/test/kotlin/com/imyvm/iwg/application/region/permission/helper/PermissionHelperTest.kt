package com.imyvm.iwg.application.region.permission.helper

import com.imyvm.iwg.application.interaction.onCertificatePermissionValue
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionHelperTest {
    private val player = UUID.randomUUID()
    private val otherPlayer = UUID.randomUUID()
    private val region = Region("region", 1, mutableListOf())
    private val scope = GeoScope("scope", Identifier.parse("minecraft:overworld"), null, geoShape = null)

    @Test
    fun `uses supplied default when no setting exists`() {
        assertTrue(hasPermission(region, player, PermissionKey.PVP, defaultValue = true))
        assertFalse(hasPermission(region, player, PermissionKey.PVP, defaultValue = false))
    }

    @Test
    fun `personal setting wins over global setting in same container`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)
        region.settings += PermissionSetting(PermissionKey.PVP, true, player)

        assertTrue(hasPermission(region, player, PermissionKey.PVP))
        assertFalse(hasPermission(region, otherPlayer, PermissionKey.PVP))
    }

    @Test
    fun `scope setting wins over region setting`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false, player)
        scope.settings += PermissionSetting(PermissionKey.PVP, true)

        assertTrue(hasPermission(region, player, PermissionKey.PVP, scope))
    }

    @Test
    fun `region setting is fallback when scope has no matching setting`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)

        assertFalse(hasPermission(region, player, PermissionKey.PVP, scope))
    }

    @Test
    fun `child permission inherits nearest configured parent`() {
        region.settings += PermissionSetting(PermissionKey.BUILD_BREAK, false)
        region.settings += PermissionSetting(PermissionKey.BUILD, true)

        assertTrue(hasPermission(region, player, PermissionKey.BUCKET_BUILD))
        assertFalse(hasPermission(region, player, PermissionKey.BREAK))
    }

    @Test
    fun `explicit child setting wins over parent setting`() {
        region.settings += PermissionSetting(PermissionKey.THROWABLE, false)
        region.settings += PermissionSetting(PermissionKey.EGG_USE, true)

        assertTrue(hasPermission(region, player, PermissionKey.EGG_USE))
    }

    @Test
    fun `API resolver falls back to global setting for a player`() {
        region.settings += PermissionSetting(PermissionKey.PVP, false)

        assertFalse(onCertificatePermissionValue(region, null, player, PermissionKey.PVP))
    }

    @Test
    fun `API resolver uses the same parent inheritance as runtime checks`() {
        region.settings += PermissionSetting(PermissionKey.BUILD, false)

        assertFalse(onCertificatePermissionValue(region, null, player, PermissionKey.BUCKET_BUILD))
    }

    @Test
    fun `denial source identifies scope region and default`() {
        scope.settings += PermissionSetting(PermissionKey.PVP, false)
        region.settings += PermissionSetting(PermissionKey.BUILD, false)

        assertEquals(PermissionDenialSource.AtScope, getPermissionDenialSource(region, player, PermissionKey.PVP, scope))
        assertEquals(PermissionDenialSource.AtRegion, getPermissionDenialSource(region, player, PermissionKey.BUILD, scope))
        assertEquals(PermissionDenialSource.ByDefault, getPermissionDenialSource(region, player, PermissionKey.FLY, scope, false))
    }

    @Test
    fun `denial context names the container that rejected the action`() {
        assertEquals("Scope &bscope&7 of Region &bregion&7", buildPermissionDenialContext(region, scope, PermissionDenialSource.AtScope))
        assertEquals("Region &bregion&7", buildPermissionDenialContext(region, scope, PermissionDenialSource.AtRegion))
    }
}
