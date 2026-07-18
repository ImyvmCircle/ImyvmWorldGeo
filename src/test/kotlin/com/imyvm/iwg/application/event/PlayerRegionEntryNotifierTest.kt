package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.util.text.Translator
import net.minecraft.resources.Identifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerRegionEntryNotifierTest {
    private val player = UUID.randomUUID()
    private val scope = GeoScope(
        "spawn",
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(7, 1))
    )
    private val region = Region("market", 7, mutableListOf(scope))

    @Test
    fun `RPG entry catalog is derived in stable enum order`() {
        assertEquals(
            listOf(
                PermissionKey.RPG_ITEM_PICKUP,
                PermissionKey.RPG_BOW_SHOOT,
                PermissionKey.RPG_VEHICLE_USE,
                PermissionKey.RPG_EATING,
                PermissionKey.RPG_FISHING
            ),
            RPG_ENTRY_PERMISSIONS.map { it.key }
        )
        assertTrue(RPG_ENTRY_PERMISSIONS.all { entry ->
            Translator.hasTranslation(entry.notification.translationKey)
        })
    }

    @Test
    fun `region notification target resolves global and player permission`() {
        val target = EntryPermissionTarget.RegionTarget(region)
        region.settingStore.put(PermissionSetting(PermissionKey.RPG_FISHING, false))
        assertFalse(hasEntryPermission(target, player, PermissionKey.RPG_FISHING))

        region.settingStore.put(PermissionSetting(PermissionKey.RPG_FISHING, true, player))
        assertTrue(hasEntryPermission(target, player, PermissionKey.RPG_FISHING))
    }

    @Test
    fun `scope notification target resolves global and player permission`() {
        val target = EntryPermissionTarget.ScopeTarget(region, scope)
        region.settingStore.put(PermissionSetting(PermissionKey.RPG_FISHING, false))
        scope.settingStore.put(PermissionSetting(PermissionKey.RPG_FISHING, true))
        assertTrue(hasEntryPermission(target, player, PermissionKey.RPG_FISHING))

        scope.settingStore.put(PermissionSetting(PermissionKey.RPG_FISHING, false, player))
        assertFalse(hasEntryPermission(target, player, PermissionKey.RPG_FISHING))
    }

    @Test
    fun `scope notification target rejects a scope from another region`() {
        val unrelatedScope = GeoScope(
            "other",
            Identifier.parse("minecraft:overworld"),
            null,
            geoShape = null,
            scopeId = ScopeId(generateCompatScopeIdRaw(8, 1))
        )

        assertFailsWith<IllegalArgumentException> {
            EntryPermissionTarget.ScopeTarget(region, unrelatedScope)
        }
    }
}
