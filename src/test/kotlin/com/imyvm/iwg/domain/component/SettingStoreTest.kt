package com.imyvm.iwg.domain.component

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingStoreTest {
    @Test
    fun `effect amplifier is bounded at construction`() {
        EffectSetting(EffectKey.SPEED, 0)
        EffectSetting(EffectKey.SPEED, 255)

        assertFailsWith<IllegalArgumentException> { EffectSetting(EffectKey.SPEED, -1) }
        assertFailsWith<IllegalArgumentException> { EffectSetting(EffectKey.SPEED, 256) }
    }

    @Test
    fun `replace all rejects duplicate identities before changing the store`() {
        val player = UUID.randomUUID()
        val store = SettingStore(listOf(PermissionSetting(PermissionKey.BUILD, true)))

        assertFailsWith<IllegalArgumentException> {
            store.replaceAll(
                listOf(
                    PermissionSetting(PermissionKey.BREAK, true, player),
                    PermissionSetting(PermissionKey.BREAK, false, player)
                )
            )
        }

        assertEquals(true, store.globalPermission(PermissionKey.BUILD))
        assertEquals(null, store.playerPermission(PermissionKey.BREAK, player))
    }

    @Test
    fun `global and player settings are distinct identities`() {
        val player = UUID.randomUUID()
        val store = SettingStore(
            listOf(
                EffectSetting(EffectKey.SPEED, 1),
                EffectSetting(EffectKey.SPEED, 2, player)
            )
        )

        assertEquals(1, store.globalEffect(EffectKey.SPEED))
        assertEquals(2, store.playerEffect(EffectKey.SPEED, player))
    }
}
