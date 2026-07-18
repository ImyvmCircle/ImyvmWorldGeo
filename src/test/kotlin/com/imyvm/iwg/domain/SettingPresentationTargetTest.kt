package com.imyvm.iwg.domain

import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingPresentationTargetTest {
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

    private fun keys(target: SettingPresentationTarget): List<String> = with(target.keys) {
        listOf(header, globalHeader, personalHeader, permissionHeader, effectHeader, ruleHeader, entryExitHeader, item)
    }
}
