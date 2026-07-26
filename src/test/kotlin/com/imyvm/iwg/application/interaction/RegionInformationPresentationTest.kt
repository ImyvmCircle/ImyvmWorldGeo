package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.GeoPoint
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import net.minecraft.server.MinecraftServer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.Locale

class RegionInformationPresentationTest {
    @Test
    fun `built-in permissions use translated names while extensions retain their id`() {
        val builtIn = PermissionSetting(PermissionKey.BUILD, true)
        val extension = ExtensionPermissionSetting(ExtensionPermissionKey("addon:custom"), true)

        assertEquals(Translator.raw(PermissionKey.BUILD.displayTranslationKey), permissionSettingDisplayName(builtIn))
        assertEquals("addon:custom", permissionSettingDisplayName(extension))
    }

    @Test
    fun `region and scope targets expose complete required translation keys`() {
        val region = SettingPresentationTarget.RegionSettings
        val scope = SettingPresentationTarget.ScopeSettings("spawn")

        assertEquals("region.setting.header", region.keys.header)
        assertEquals("geo.scope.setting.header", scope.keys.header)
        assertTrue(keys(region).all(Translator::hasTranslation))
        assertTrue(keys(scope).all(Translator::hasTranslation))
        assertContains(scope.translateHeader().string, "spawn")
    }

    @Test
    fun `legacy setting presentation accepts only historical valid combinations`() {
        assertSame(
            SettingPresentationTarget.RegionSettings,
            legacySettingPresentationTarget("region.setting", null)
        )
        assertIs<SettingPresentationTarget.ScopeSettings>(
            legacySettingPresentationTarget("geo.scope.setting", "spawn")
        )

        assertFailsWith<IllegalArgumentException> {
            legacySettingPresentationTarget("region.setting", "spawn")
        }
        assertFailsWith<IllegalArgumentException> {
            legacySettingPresentationTarget("geo.scope.setting", null)
        }
        assertFailsWith<IllegalArgumentException> {
            legacySettingPresentationTarget("addon.setting", null)
        }
    }

    @Test
    fun `legacy formatSettings JVM method remains available`() {
        Region.Companion::class.java.getMethod(
            "formatSettings",
            MinecraftServer::class.java,
            List::class.java,
            String::class.java,
            String::class.java
        )
    }

    @Test
    fun `shape area formatting uses dot decimal separator regardless of default locale`() {
        val savedLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val info = buildShapeInfoLine(GeoShape.circle(GeoPoint(0, 0), 7)).string

            assertTrue(Regex("""\d+\.\d+""").containsMatchIn(info), "Area should contain dot decimal: $info")
            assertFalse(Regex("""\d+,\d+""").containsMatchIn(info), "Area should not use comma decimal: $info")
        } finally {
            Locale.setDefault(savedLocale)
        }
    }

    private fun keys(target: SettingPresentationTarget): List<String> = with(target.keys) {
        listOf(header, globalHeader, personalHeader, permissionHeader, effectHeader, ruleHeader, entryExitHeader, item)
    }
}
