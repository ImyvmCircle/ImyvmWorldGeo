package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.domain.component.generateCompatScopeIdRaw
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.api.SettingAddResult
import com.imyvm.iwg.inter.api.SettingRemoveResult
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingMutationApplicationTest {
    private val tempDirectories = mutableListOf<Path>()

    @AfterTest
    fun clearState() {
        RegionDatabase.unbindSession()
        tempDirectories.forEach { it.toFile().deleteRecursively() }
        tempDirectories.clear()
    }

    @Test
    fun `permission overload subjects remain distinct and duplicate does not save`() {
        val (region, scope) = bindTarget()
        val regionTarget = SettingMutationTarget.RegionTarget(region)
        val scopeTarget = SettingMutationTarget.ScopeTarget(region, scope)
        val playerId = UUID.randomUUID()
        var saveCalls = 0
        val save = { saveCalls++; true }

        assertEquals(
            SettingAddResult.SUCCESS,
            addPermissionSetting(regionTarget, PermissionKey.BUILD, SettingSubject.Global, false, save)
        )
        assertEquals(
            SettingAddResult.SUCCESS,
            addPermissionSetting(regionTarget, PermissionKey.BUILD, SettingSubject.Player(playerId), true, save)
        )
        assertEquals(
            SettingAddResult.SUCCESS,
            addPermissionSetting(scopeTarget, PermissionKey.BUILD, SettingSubject.Global, true, save)
        )
        assertEquals(
            SettingAddResult.SUCCESS,
            addPermissionSetting(scopeTarget, PermissionKey.BUILD, SettingSubject.Player(playerId), false, save)
        )
        assertEquals(
            SettingAddResult.ALREADY_EXISTS,
            addPermissionSetting(regionTarget, PermissionKey.BUILD, SettingSubject.Global, true, save)
        )

        assertEquals(4, saveCalls)
        assertEquals(false, region.settingStore.globalPermission(PermissionKey.BUILD))
        assertEquals(true, region.settingStore.playerPermission(PermissionKey.BUILD, playerId))
        assertEquals(true, scope.settingStore.globalPermission(PermissionKey.BUILD))
        assertEquals(false, scope.settingStore.playerPermission(PermissionKey.BUILD, playerId))
    }

    @Test
    fun `permission remove rollback restores only the removed identity`() {
        val (region, _) = bindTarget()
        val target = SettingMutationTarget.RegionTarget(region)
        val playerId = UUID.randomUUID()
        region.settingStore.putPermissionIfAbsent(PermissionKey.PVP, SettingSubject.Global, false)
        region.settingStore.putPermissionIfAbsent(PermissionKey.BUILD, SettingSubject.Player(playerId), true)

        assertEquals(
            SettingRemoveResult.PERSISTENCE_FAILED,
            removePermissionSetting(target, PermissionKey.PVP, SettingSubject.Global) { false }
        )

        assertEquals(false, region.settingStore.globalPermission(PermissionKey.PVP))
        assertEquals(true, region.settingStore.playerPermission(PermissionKey.BUILD, playerId))
    }

    @Test
    fun `scope families mutate the exact canonical store`() {
        val (region, scope) = bindTarget()
        val regionTarget = SettingMutationTarget.RegionTarget(region)
        val scopeTarget = SettingMutationTarget.ScopeTarget(region, scope)

        assertEquals(SettingAddResult.SUCCESS, addRuleSetting(regionTarget, RuleKey.PISTON, true) { true })
        assertEquals(SettingAddResult.SUCCESS, addRuleSetting(scopeTarget, RuleKey.PISTON, false) { true })
        assertEquals(SettingAddResult.SUCCESS, addEntryExitToggleSetting(regionTarget, EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED, true) { true })
        assertEquals(SettingAddResult.SUCCESS, addEntryExitToggleSetting(scopeTarget, EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED, false) { true })
        assertEquals(SettingAddResult.SUCCESS, addEntryExitMessageSetting(regionTarget, EntryExitMessageKey.ENTER_MESSAGE, "region") { true })
        assertEquals(SettingAddResult.SUCCESS, addEntryExitMessageSetting(scopeTarget, EntryExitMessageKey.ENTER_MESSAGE, "scope") { true })

        assertEquals(true, region.settingStore.rule(RuleKey.PISTON))
        assertEquals(false, scope.settingStore.rule(RuleKey.PISTON))
        assertEquals(true, region.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED))
        assertEquals(false, scope.settingStore.entryExitToggle(EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED))
        assertEquals("region", region.settingStore.entryExitMessage(EntryExitMessageKey.ENTER_MESSAGE))
        assertEquals("scope", scope.settingStore.entryExitMessage(EntryExitMessageKey.ENTER_MESSAGE))
    }

    @Test
    fun `rule add and remove restore the exact identity after failed save`() {
        val (region, _) = bindTarget()
        val target = SettingMutationTarget.RegionTarget(region)

        assertEquals(
            SettingAddResult.PERSISTENCE_FAILED,
            addRuleSetting(target, RuleKey.PISTON, false) { false }
        )
        assertEquals(null, region.settingStore.rule(RuleKey.PISTON))

        region.settingStore.putRuleIfAbsent(RuleKey.PISTON, false)
        region.settingStore.putRuleIfAbsent(RuleKey.DISPENSER, true)
        assertEquals(
            SettingRemoveResult.PERSISTENCE_FAILED,
            removeRuleSetting(target, RuleKey.PISTON) { false }
        )
        assertEquals(false, region.settingStore.rule(RuleKey.PISTON))
        assertEquals(true, region.settingStore.rule(RuleKey.DISPENSER))
    }

    @Test
    fun `entry exit toggle add and remove restore the exact identity after failed save`() {
        val (region, _) = bindTarget()
        val target = SettingMutationTarget.RegionTarget(region)
        val key = EntryExitToggleKey.ENTRY_EXIT_MESSAGE_ENABLED

        assertEquals(
            SettingAddResult.PERSISTENCE_FAILED,
            addEntryExitToggleSetting(target, key, false) { false }
        )
        assertEquals(null, region.settingStore.entryExitToggle(key))

        region.settingStore.putEntryExitToggleIfAbsent(key, true)
        region.settingStore.putRuleIfAbsent(RuleKey.DISPENSER, false)
        assertEquals(
            SettingRemoveResult.PERSISTENCE_FAILED,
            removeEntryExitToggleSetting(target, key) { false }
        )
        assertEquals(true, region.settingStore.entryExitToggle(key))
        assertEquals(false, region.settingStore.rule(RuleKey.DISPENSER))
    }

    @Test
    fun `entry exit message add and remove restore the exact identity after failed save`() {
        val (region, _) = bindTarget()
        val target = SettingMutationTarget.RegionTarget(region)

        assertEquals(
            SettingAddResult.PERSISTENCE_FAILED,
            addEntryExitMessageSetting(target, EntryExitMessageKey.ENTER_MESSAGE, "new") { false }
        )
        assertEquals(null, region.settingStore.entryExitMessage(EntryExitMessageKey.ENTER_MESSAGE))

        region.settingStore.putEntryExitMessageIfAbsent(EntryExitMessageKey.ENTER_MESSAGE, "original")
        region.settingStore.putEntryExitMessageIfAbsent(EntryExitMessageKey.EXIT_MESSAGE, "other")
        assertEquals(
            SettingRemoveResult.PERSISTENCE_FAILED,
            removeEntryExitMessageSetting(target, EntryExitMessageKey.ENTER_MESSAGE) { false }
        )
        assertEquals("original", region.settingStore.entryExitMessage(EntryExitMessageKey.ENTER_MESSAGE))
        assertEquals("other", region.settingStore.entryExitMessage(EntryExitMessageKey.EXIT_MESSAGE))
    }

    @Test
    fun `effect validates range and restores personal amplifier after failed remove`() {
        val (region, scope) = bindTarget()
        val regionTarget = SettingMutationTarget.RegionTarget(region)
        val scopeTarget = SettingMutationTarget.ScopeTarget(region, scope)
        val playerId = UUID.randomUUID()
        val subject = SettingSubject.Player(playerId)

        assertFailsWith<IllegalArgumentException> {
            addEffectSetting(scopeTarget, EffectKey.SPEED, subject, 256) { true }
        }
        assertEquals(SettingAddResult.SUCCESS, addEffectSetting(regionTarget, EffectKey.SPEED, SettingSubject.Global, 1) { true })
        assertEquals(SettingAddResult.SUCCESS, addEffectSetting(regionTarget, EffectKey.SPEED, subject, 2) { true })
        assertEquals(SettingAddResult.SUCCESS, addEffectSetting(scopeTarget, EffectKey.SPEED, SettingSubject.Global, 3) { true })
        assertEquals(SettingAddResult.SUCCESS, addEffectSetting(scopeTarget, EffectKey.SPEED, subject, 0) { true })
        assertEquals(
            SettingRemoveResult.PERSISTENCE_FAILED,
            removeEffectSetting(scopeTarget, EffectKey.SPEED, subject) { false }
        )
        assertEquals(1, region.settingStore.globalEffect(EffectKey.SPEED))
        assertEquals(2, region.settingStore.playerEffect(EffectKey.SPEED, playerId))
        assertEquals(3, scope.settingStore.globalEffect(EffectKey.SPEED))
        assertEquals(0, scope.settingStore.playerEffect(EffectKey.SPEED, playerId))
    }

    @Test
    fun `registered extension keys work and unregistered keys fail before save`() {
        val (region, _) = bindTarget()
        val target = SettingMutationTarget.RegionTarget(region)
        val permissionId = "test:b6_r1_permission"
        val ruleId = "test:b6_r1_rule"
        ExtensionSettingRegistry.registerPermissionKey(permissionId, true)
        ExtensionSettingRegistry.registerRuleKey(ruleId, true)

        assertEquals(
            SettingAddResult.SUCCESS,
            addPermissionSetting(target, ExtensionPermissionKey(permissionId), SettingSubject.Global, false) { true }
        )
        assertEquals(SettingAddResult.SUCCESS, addRuleSetting(target, ExtensionRuleKey(ruleId), false) { true })

        var saveCalls = 0
        assertFailsWith<IllegalArgumentException> {
            addPermissionSetting(
                target,
                ExtensionPermissionKey("test:b6_r1_unregistered"),
                SettingSubject.Global,
                true
            ) { saveCalls++; true }
        }
        assertEquals(0, saveCalls)
    }

    @Test
    fun `not found removal skips persistence`() {
        val (region, _) = bindTarget()
        var saveCalls = 0

        assertEquals(
            SettingRemoveResult.NOT_FOUND,
            removeRuleSetting(SettingMutationTarget.RegionTarget(region), RuleKey.SPAWN_MONSTERS) {
                saveCalls++
                true
            }
        )
        assertEquals(0, saveCalls)
    }

    @Test
    fun `foreign and detached scope fail before persistence`() {
        val (region, _) = bindTarget()
        val detached = scope("scope", 7, 0)

        assertFailsWith<IllegalArgumentException> {
            SettingMutationTarget.ScopeTarget(region, detached)
        }
    }

    private fun bindTarget(): Pair<Region, GeoScope> {
        val scope = scope("scope", 7, 0)
        val region = Region("region", 7, mutableListOf(scope))
        val directory = Files.createTempDirectory("iwg-setting-mutation-test")
        tempDirectories.add(directory)
        RegionDatabase.bindSession(directory)
        RegionDatabase.insertRegion(region)
        return region to scope
    }

    private fun scope(name: String, regionId: Int, index: Int) = GeoScope(
        name,
        Identifier.parse("minecraft:overworld"),
        null,
        geoShape = null,
        scopeId = ScopeId(generateCompatScopeIdRaw(regionId, index))
    )
}
