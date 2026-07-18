package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.application.interaction.scope.onReplacingScopeShape
import com.imyvm.iwg.application.interaction.getDefaultValueForPermission
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeNotFoundException
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionKeyLike
import com.imyvm.iwg.domain.component.RuleKeyLike
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import com.imyvm.iwg.util.text.Translator
import java.util.UUID

/**
 * Supported player-driven mutation API for addons.
 *
 * Compatibility and deprecation policy: `docs/addon-api-compatibility.md`.
 */
@Suppress("unused")
object PlayerInteractionApi {
    /** Starts a normal creation selection. Call selection operations on the Minecraft server thread. */
    fun startSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onStartSelection(player, shapeType)
    fun stopSelection(player: ServerPlayer) = onStopSelection(player)

    /** Clears points without changing mode. A shape may be supplied only for a creation selection. */
    fun resetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null) = onResetSelection(player, shapeType)
    fun setSelectionShape(player: ServerPlayer, shapeType: GeoShapeType) = onSetSelectionShape(player, shapeType)

    /**
     * Starts a modification selection for an exact live Scope in RegionDatabase.
     *
     * The Scope must be assigned, have a supported geometry, and belong to the player's current
     * dimension. Detached copies, orphaned Scopes, and later attempts to modify another Scope fail.
     */
    fun startSelectionForModify(player: ServerPlayer, scope: GeoScope) = onStartSelectionForModify(player, scope)
    fun createRegion(player: ServerPlayer, name: String, idMark: Int = 0) =
        if (createRegionFromSelection(player, name, idMark) != null) 1 else 0
    fun createAndGetRegion(player: ServerPlayer, name: String, idMark: Int = 0) =
        createRegionFromSelection(player, name, idMark)
    /**
     * Compatibility entry point retained for addons compiled against v26.1-1.5.1.
     *
     * The operation remains persistence-safe, but this legacy signature cannot expose its result.
     *
     * @deprecated Use [deleteRegionWithResult].
     */
    @Deprecated("Use deleteRegionWithResult(player, region)")
    fun deleteRegion(player: ServerPlayer, region: Region) {
        deleteRegionWithResult(player, region)
    }

    /** Deletes an exact canonical Region and reports whether persistence succeeded. */
    fun deleteRegionWithResult(player: ServerPlayer, region: Region): RegionDeleteResult =
        onRegionDelete(player, region)
    fun renameRegion(player: ServerPlayer, region: Region, newName: String) = onRegionRename(player, region, newName)
    fun addScope(player: ServerPlayer, region: Region, name: String) =
        if (createScopeFromSelection(player, region, name) != null) 1 else 0
    fun createAndGetRegionScopePair(player: ServerPlayer, region: Region, name: String) =
        createScopeFromSelection(player, region, name)?.let { region to it }
    /**
     * Compatibility entry point retained for addons compiled against v26.1-1.5.1.
     *
     * The Scope name is resolved at the adapter boundary before delegating to the exact-target API.
     *
     * @deprecated Use [deleteScopeWithResult].
     */
    @Deprecated("Use deleteScopeWithResult(player, region, scope)")
    fun deleteScope(player: ServerPlayer, region: Region, scopeName: String) {
        val scope = getScopeOrNotify(player, region, scopeName) ?: return
        when (deleteScopeWithResult(player, region, scope)) {
            ScopeDeleteResult.SUCCESS -> player.sendSystemMessage(
                Translator.tr("interaction.meta.scope.delete.success", scopeName, region.name)
            )
            ScopeDeleteResult.LAST_SCOPE -> player.sendSystemMessage(
                Translator.tr("interaction.meta.scope.delete.error.last_scope")
            )
            ScopeDeleteResult.PERSISTENCE_FAILED -> Unit
        }
    }

    /**
     * Deletes an exact canonical Scope.
     *
     * [ScopeDeleteResult.LAST_SCOPE] leaves the Region unchanged and does not attempt persistence.
     */
    fun deleteScopeWithResult(
        player: ServerPlayer,
        region: Region,
        scope: GeoScope
    ): ScopeDeleteResult = onScopeDelete(player, region, scope)
    fun renameScope(player: ServerPlayer, region: Region, oldName: String, newName: String) = onScopeRename(player, region, oldName, newName)
    fun transferScope(player: ServerPlayer, sourceRegion: Region, scopeName: String, targetRegion: Region) = onScopeTransfer(player, sourceRegion, scopeName, targetRegion)
    fun mergeRegion(player: ServerPlayer, sourceRegion: Region, targetRegion: Region) = onRegionMerge(player, sourceRegion, targetRegion)
    fun addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int) = onAddingTeleportPoint(player, targetRegion, scope, x, y, z)
    fun addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope) =
        onAddingTeleportPoint(player, targetRegion, scope, player.blockPosition().x, player.blockPosition().y, player.blockPosition().z)
    fun resetTeleportPoint(player: ServerPlayer, region: Region, scope: GeoScope) = onResettingTeleportPoint(player, region, scope)
    fun getTeleportPoint(scope: GeoScope) = onGettingTeleportPoint(scope)
    /** Teleports only when the Scope teleport point is publicly accessible. */
    fun teleportPlayerToScope(player: ServerPlayer, targetRegion: Region, scope: GeoScope) = onTeleportingPlayer(player, targetRegion, scope)

    /**
     * Teleports as an administrator, bypassing only teleport-point accessibility.
     * Canonical ownership, dimension availability and physical safety are still enforced.
     */
    fun teleportPlayerToScopeAsAdministrator(player: ServerPlayer, targetRegion: Region, scope: GeoScope) =
        onTeleportingPlayerAsAdministrator(player, targetRegion, scope)
    fun toggleTeleportPointAccessibility(player: ServerPlayer, region: Region, scope: GeoScope) =
        onTogglingTeleportPointAccessibility(player, region, scope)

    /**
     * Compatibility entry point that resolves the Scope's canonical owner from RegionDatabase.
     *
     * Detached copies, orphaned scopes, and unassigned scopes are rejected.
     *
     * @deprecated Since R11 (unreleased). Use [toggleTeleportPointAccessibility] with the
     * executing player and owning Region. Eligible for removal only after two released versions
     * and explicit maintainer approval.
    */
    @Deprecated("Use toggleTeleportPointAccessibility(player, region, scope)")
    @Suppress("DEPRECATION")
    fun toggleTeleportPointAccessibility(scope: GeoScope) = onTogglingTeleportPointAccessibility(scope)
    fun modifyScope(player: ServerPlayer, region: Region, scopeName: String) = onModifyScope(player, region, scopeName)

    /**
     * Replaces the geometry of an exact live Scope with a complete immutable shape of the same type.
     * Size and intersection policy are validated before mutation; persistence failure restores the old shape.
     */
    fun replaceScopeShape(player: ServerPlayer, region: Region, scope: GeoScope, newShape: GeoShape) =
        onReplacingScopeShape(player, region, scope, newShape)

    /**
     * Adds typed settings to an exact live target and reports persistence outcome.
     *
     * Permission and effect methods use overloads: omitting `targetPlayer` selects the global
     * subject, while supplying a non-null UUID selects that player. Extension keys must already
     * be registered. Call these mutations on the Minecraft server thread. A persistence failure
     * restores the exact previous setting identity.
     */
    fun addRegionPermission(player: ServerPlayer, region: Region, key: PermissionKeyLike, value: Boolean): SettingAddResult =
        addPermissionSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Global, value) { saveRegionData(player) }

    fun addRegionPermission(player: ServerPlayer, region: Region, key: PermissionKeyLike, value: Boolean, targetPlayer: UUID): SettingAddResult =
        addPermissionSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Player(targetPlayer), value) { saveRegionData(player) }

    fun addScopePermission(player: ServerPlayer, region: Region, scope: GeoScope, key: PermissionKeyLike, value: Boolean): SettingAddResult =
        addPermissionSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Global, value) { saveRegionData(player) }

    fun addScopePermission(player: ServerPlayer, region: Region, scope: GeoScope, key: PermissionKeyLike, value: Boolean, targetPlayer: UUID): SettingAddResult =
        addPermissionSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Player(targetPlayer), value) { saveRegionData(player) }

    fun removeRegionPermission(player: ServerPlayer, region: Region, key: PermissionKeyLike): SettingRemoveResult =
        removePermissionSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Global) { saveRegionData(player) }

    fun removeRegionPermission(player: ServerPlayer, region: Region, key: PermissionKeyLike, targetPlayer: UUID): SettingRemoveResult =
        removePermissionSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Player(targetPlayer)) { saveRegionData(player) }

    fun removeScopePermission(player: ServerPlayer, region: Region, scope: GeoScope, key: PermissionKeyLike): SettingRemoveResult =
        removePermissionSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Global) { saveRegionData(player) }

    fun removeScopePermission(player: ServerPlayer, region: Region, scope: GeoScope, key: PermissionKeyLike, targetPlayer: UUID): SettingRemoveResult =
        removePermissionSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Player(targetPlayer)) { saveRegionData(player) }

    fun addRegionEffect(player: ServerPlayer, region: Region, key: EffectKey, amplifier: Int): SettingAddResult =
        addEffectSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Global, amplifier) { saveRegionData(player) }

    fun addRegionEffect(player: ServerPlayer, region: Region, key: EffectKey, amplifier: Int, targetPlayer: UUID): SettingAddResult =
        addEffectSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Player(targetPlayer), amplifier) { saveRegionData(player) }

    fun addScopeEffect(player: ServerPlayer, region: Region, scope: GeoScope, key: EffectKey, amplifier: Int): SettingAddResult =
        addEffectSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Global, amplifier) { saveRegionData(player) }

    fun addScopeEffect(player: ServerPlayer, region: Region, scope: GeoScope, key: EffectKey, amplifier: Int, targetPlayer: UUID): SettingAddResult =
        addEffectSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Player(targetPlayer), amplifier) { saveRegionData(player) }

    fun removeRegionEffect(player: ServerPlayer, region: Region, key: EffectKey): SettingRemoveResult =
        removeEffectSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Global) { saveRegionData(player) }

    fun removeRegionEffect(player: ServerPlayer, region: Region, key: EffectKey, targetPlayer: UUID): SettingRemoveResult =
        removeEffectSetting(SettingMutationTarget.RegionTarget(region), key, SettingSubject.Player(targetPlayer)) { saveRegionData(player) }

    fun removeScopeEffect(player: ServerPlayer, region: Region, scope: GeoScope, key: EffectKey): SettingRemoveResult =
        removeEffectSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Global) { saveRegionData(player) }

    fun removeScopeEffect(player: ServerPlayer, region: Region, scope: GeoScope, key: EffectKey, targetPlayer: UUID): SettingRemoveResult =
        removeEffectSetting(SettingMutationTarget.ScopeTarget(region, scope), key, SettingSubject.Player(targetPlayer)) { saveRegionData(player) }

    fun addRegionRule(player: ServerPlayer, region: Region, key: RuleKeyLike, value: Boolean): SettingAddResult =
        addRuleSetting(SettingMutationTarget.RegionTarget(region), key, value) { saveRegionData(player) }

    fun addScopeRule(player: ServerPlayer, region: Region, scope: GeoScope, key: RuleKeyLike, value: Boolean): SettingAddResult =
        addRuleSetting(SettingMutationTarget.ScopeTarget(region, scope), key, value) { saveRegionData(player) }

    fun removeRegionRule(player: ServerPlayer, region: Region, key: RuleKeyLike): SettingRemoveResult =
        removeRuleSetting(SettingMutationTarget.RegionTarget(region), key) { saveRegionData(player) }

    fun removeScopeRule(player: ServerPlayer, region: Region, scope: GeoScope, key: RuleKeyLike): SettingRemoveResult =
        removeRuleSetting(SettingMutationTarget.ScopeTarget(region, scope), key) { saveRegionData(player) }

    fun addRegionEntryExitToggle(player: ServerPlayer, region: Region, key: EntryExitToggleKey, value: Boolean): SettingAddResult =
        addEntryExitToggleSetting(SettingMutationTarget.RegionTarget(region), key, value) { saveRegionData(player) }

    fun addScopeEntryExitToggle(player: ServerPlayer, region: Region, scope: GeoScope, key: EntryExitToggleKey, value: Boolean): SettingAddResult =
        addEntryExitToggleSetting(SettingMutationTarget.ScopeTarget(region, scope), key, value) { saveRegionData(player) }

    fun removeRegionEntryExitToggle(player: ServerPlayer, region: Region, key: EntryExitToggleKey): SettingRemoveResult =
        removeEntryExitToggleSetting(SettingMutationTarget.RegionTarget(region), key) { saveRegionData(player) }

    fun removeScopeEntryExitToggle(player: ServerPlayer, region: Region, scope: GeoScope, key: EntryExitToggleKey): SettingRemoveResult =
        removeEntryExitToggleSetting(SettingMutationTarget.ScopeTarget(region, scope), key) { saveRegionData(player) }

    fun addRegionEntryExitMessage(player: ServerPlayer, region: Region, key: EntryExitMessageKey, message: String): SettingAddResult =
        addEntryExitMessageSetting(SettingMutationTarget.RegionTarget(region), key, message) { saveRegionData(player) }

    fun addScopeEntryExitMessage(player: ServerPlayer, region: Region, scope: GeoScope, key: EntryExitMessageKey, message: String): SettingAddResult =
        addEntryExitMessageSetting(SettingMutationTarget.ScopeTarget(region, scope), key, message) { saveRegionData(player) }

    fun removeRegionEntryExitMessage(player: ServerPlayer, region: Region, key: EntryExitMessageKey): SettingRemoveResult =
        removeEntryExitMessageSetting(SettingMutationTarget.RegionTarget(region), key) { saveRegionData(player) }

    fun removeScopeEntryExitMessage(player: ServerPlayer, region: Region, scope: GeoScope, key: EntryExitMessageKey): SettingRemoveResult =
        removeEntryExitMessageSetting(SettingMutationTarget.ScopeTarget(region, scope), key) { saveRegionData(player) }

    @Deprecated("Use PlayerInteractionApi typed setting mutations (e.g. addRegionPermission, addScopeEffect)")
    fun addSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?) = addRegionSetting(player, region, keyString, valueString, targetPlayerStr)
    @Deprecated("Use PlayerInteractionApi typed setting mutations (e.g. addScopePermission, addScopeRule)")
    fun addSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?) = addScopeSetting(player, region, region.getScopeByName(scopeName), keyString, valueString, targetPlayerStr)
    @Deprecated("Use PlayerInteractionApi typed setting mutations (e.g. removeRegionPermission, removeRegionEffect)")
    fun removeSettingRegion(player: ServerPlayer, region: Region, keyString: String, targetPlayerStr: String?) = removeRegionSetting(player, region, keyString, targetPlayerStr)
    @Deprecated("Use PlayerInteractionApi typed setting mutations (e.g. removeScopePermission, removeScopeRule)")
    fun removeSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?) = removeScopeSetting(player, region, region.getScopeByName(scopeName), keyString, targetPlayerStr)
    fun getDefaultPermissionValue(keyString: String): Boolean {
        val key = PermissionKey.entries.firstOrNull { it.name == keyString }
        if (key != null) return getDefaultValueForPermission(key)
        if (ExtensionSettingRegistry.isRegisteredPermissionKey(keyString)) {
            return getDefaultValueForPermission(ExtensionSettingRegistry.permissionKey(keyString))
        }
        throw IllegalArgumentException("interaction.meta.setting.error.invalid_key")
    }
    fun getRegionPermissionValue(player: ServerPlayer, region: Region, keyString: String): Boolean =
        onCertificatePermissionValue(player, region, null, null, keyString)
    fun getRegionPlayerPermissionValue(
        player: ServerPlayer,
        region: Region,
        targetPlayerName: String,
        keyString: String
    ): Boolean = onCertificatePermissionValue(player, region, null, targetPlayerName, keyString)
    fun getScopePermissionValue(player: ServerPlayer, region: Region, scope: GeoScope, keyString: String): Boolean =
        onCertificatePermissionValue(player, region, scope, null, keyString)
    fun getScopePlayerPermissionValue(
        player: ServerPlayer,
        region: Region,
        scope: GeoScope,
        targetPlayerName: String,
        keyString: String
    ): Boolean = onCertificatePermissionValue(player, region, scope, targetPlayerName, keyString)

    /**
     * Compatibility dispatcher for the former nullable permission API.
     *
     * @deprecated Since R9 (unreleased). Use the explicit default/Region/Scope and
     * global/player methods. Eligible for removal only after two released versions
     * and explicit maintainer approval.
     */
    @Deprecated("Use an explicit default, region, or scope permission query")
    fun getPermissionValueRegion(player: ServerPlayer, region: Region?, scopeName: String?, targetPlayerNameStr: String?, keyString: String): Boolean {
        if (region == null) {
            require(scopeName == null) { "scope requires region" }
            return getDefaultPermissionValue(keyString)
        }
        val scope = scopeName?.let {
            region.scopes.firstOrNull { s -> s.scopeName.equals(it, ignoreCase = true) }
                ?: throw ScopeNotFoundException(it, region.name)
        }
        return when {
            scope != null && targetPlayerNameStr != null -> getScopePlayerPermissionValue(player, region, scope, targetPlayerNameStr, keyString)
            scope != null -> getScopePermissionValue(player, region, scope, keyString)
            targetPlayerNameStr != null -> getRegionPlayerPermissionValue(player, region, targetPlayerNameStr, keyString)
            else -> getRegionPermissionValue(player, region, keyString)
        }
    }
    @Deprecated("Use RegionDataApi.getRegionRuleValue, getScopeRuleValue, or the extension rule queries")
    fun getRuleValueRegion(region: Region?, keyString: String) =
        onCertificateRuleValue(region, null, keyString)
    @Deprecated("Use RegionDataApi.getScopeRuleValue or getScopeExtensionRuleValue")
    fun getRuleValueScope(region: Region?, scopeName: String, keyString: String): Boolean? {
        val targetRegion = requireNotNull(region) { "scope requires region" }
        val scope = targetRegion.scopes.firstOrNull { it.scopeName.equals(scopeName, ignoreCase = true) }
            ?: throw ScopeNotFoundException(scopeName, targetRegion.name)
        return onCertificateRuleValue(targetRegion, scope, keyString)
    }
    fun queryRegionInfo(player: ServerPlayer, region: Region) = onQueryRegion(player, region, true)
    fun queryRegionNaturalStats(player: ServerPlayer, region: Region, categoryName: String? = null) =
        onQueryRegionNaturalStats(player, region, categoryName, true)
    fun queryRegionPlayerStats(player: ServerPlayer, region: Region) =
        onQueryRegionPlayerStats(player, region, true)
    fun toggleActionBar(player: ServerPlayer) = onToggleActionBar(player)
    fun estimateRegionArea(player: ServerPlayer, shapeTypeName: String, customPositions: List<BlockPos>? = null) = onEstimateRegionArea(player, shapeTypeName, customPositions)
    fun estimateScopeAreaChange(player: ServerPlayer, region: Region, scopeName: String, customPositions: List<BlockPos>? = null) = onEstimateScopeAreaChange(player, region, scopeName, customPositions)

    @Deprecated("Use addRegionEntryExitToggle or addRegionEntryExitMessage")
    fun addEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String, valueString: String?) = addRegionSetting(player, region, keyString, valueString, null)

    @Deprecated("Use addScopeEntryExitToggle or addScopeEntryExitMessage")
    fun addEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String, valueString: String?) = addScopeSetting(player, region, region.getScopeByName(scopeName), keyString, valueString, null)

    @Deprecated("Use removeRegionEntryExitToggle or removeRegionEntryExitMessage")
    fun removeEntryExitSettingRegion(player: ServerPlayer, region: Region, keyString: String) = removeRegionSetting(player, region, keyString, null)

    @Deprecated("Use removeScopeEntryExitToggle or removeScopeEntryExitMessage")
    fun removeEntryExitSettingScope(player: ServerPlayer, region: Region, scopeName: String, keyString: String) = removeScopeSetting(player, region, region.getScopeByName(scopeName), keyString, null)
}
