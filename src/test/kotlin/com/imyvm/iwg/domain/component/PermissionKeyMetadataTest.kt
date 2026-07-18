package com.imyvm.iwg.domain.component

import com.imyvm.iwg.application.interaction.getDefaultValueForPermission
import com.imyvm.iwg.util.text.Translator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PermissionKeyMetadataTest {
    @Test
    fun `permission constants and parent relationships remain stable`() {
        assertEquals(
            listOf(
                "BUILD_BREAK", "FLY", "INTERACTION", "CONTAINER", "BUILD", "BREAK", "REDSTONE",
                "TRADE", "PVP", "BUCKET_BUILD", "BUCKET_SCOOP", "ANIMAL_KILLING",
                "VILLAGER_KILLING", "THROWABLE", "EGG_USE", "SNOWBALL_USE", "POTION_USE",
                "FARMING", "IGNITE", "ARMOR_STAND", "ITEM_FRAME", "WIND_CHARGE_USE",
                "RPG_ITEM_PICKUP", "RPG_BOW_SHOOT", "RPG_VEHICLE_USE", "RPG_EATING", "RPG_FISHING"
            ),
            PermissionKey.entries.map { it.name }
        )

        val expectedParents = mapOf(
            PermissionKey.CONTAINER to PermissionKey.INTERACTION,
            PermissionKey.BUILD to PermissionKey.BUILD_BREAK,
            PermissionKey.BREAK to PermissionKey.BUILD_BREAK,
            PermissionKey.REDSTONE to PermissionKey.INTERACTION,
            PermissionKey.BUCKET_BUILD to PermissionKey.BUILD,
            PermissionKey.BUCKET_SCOOP to PermissionKey.BREAK,
            PermissionKey.EGG_USE to PermissionKey.THROWABLE,
            PermissionKey.SNOWBALL_USE to PermissionKey.THROWABLE,
            PermissionKey.POTION_USE to PermissionKey.THROWABLE,
            PermissionKey.WIND_CHARGE_USE to PermissionKey.THROWABLE
        )
        assertEquals(expectedParents, PermissionKey.entries.mapNotNull { key ->
            key.parent?.let { key to it }
        }.toMap())
    }

    @Test
    fun `permission metadata is complete unique and translated`() {
        assertEquals(
            PermissionKey.entries.size,
            PermissionKey.entries.map { it.displayTranslationKey }.distinct().size
        )
        assertTrue(PermissionKey.entries.all { key ->
            key.displayTranslationKey.isNotBlank() && Translator.hasTranslation(key.displayTranslationKey)
        })

        val restrictedKeys = mutableListOf<String>()
        for (key in PermissionKey.entries) {
            when (val notification = key.entryNotification) {
                PermissionEntryNotification.None -> assertEquals(PermissionCategory.GENERAL, key.category)
                is PermissionEntryNotification.Restricted -> {
                    assertEquals(PermissionCategory.RPG, key.category)
                    assertTrue(Translator.hasTranslation(notification.translationKey))
                    restrictedKeys += notification.translationKey
                }
            }
        }
        assertEquals(5, restrictedKeys.size)
        assertEquals(restrictedKeys.size, restrictedKeys.distinct().size)
    }

    @Test
    fun `restricted notification rejects a blank translation key`() {
        assertFailsWith<IllegalArgumentException> {
            PermissionEntryNotification.Restricted(" ")
        }
    }

    @Test
    fun `default resolver and legacy getters cover every permission`() {
        PermissionKey.entries.forEach(::getDefaultValueForPermission)
        assertSame(PermissionKey.BUILD_BREAK, PermissionKey.BUILD.parent)
        assertIs<PermissionEntryNotification.None>(PermissionKey.BUILD.entryNotification)

        PermissionKey::class.java.getMethod("getParent")
        PermissionKey::class.java.getMethod("getCategory")
        PermissionKey::class.java.getMethod("getDisplayTranslationKey")
        PermissionKey::class.java.getMethod("getEntryNotification")
    }
}
