package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.application.region.WorldGeoGeographicProfileSupport
import com.imyvm.iwg.domain.WorldGeoCanonicalSpaceId
import com.imyvm.iwg.domain.WorldGeoExpectedPermission
import com.imyvm.iwg.domain.WorldGeoExpectedSetting
import com.imyvm.iwg.domain.WorldGeoExpectedSpaceState
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceCreation
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceDeletion
import com.imyvm.iwg.domain.WorldGeoExpectedSubSpaceRange
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationRequest
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationResult
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationStatus
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.ExtensionPermissionKey
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.ExtensionRuleKey
import com.imyvm.iwg.domain.component.ExtensionRuleSetting
import com.imyvm.iwg.domain.component.ExtensionSettingRegistry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionKeyLike
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.Setting
import com.imyvm.iwg.domain.component.SettingKey
import com.imyvm.iwg.domain.component.SettingStore
import com.imyvm.iwg.domain.component.SettingSubject
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.infra.StructuredSpaceMutationEvidence
import com.imyvm.iwg.infra.StructuredSpaceMutationStore
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal object StructuredSpaceMutationService {
    private val namespacePattern = Regex("^[a-z0-9_.-]+$")

    fun mutate(request: WorldGeoStructuredSpaceMutationRequest): WorldGeoStructuredSpaceMutationResult {
        val fixed = request.copy(expectedState = freeze(request.expectedState))
        val validationFailure = validateRequest(fixed)
        if (validationFailure != null) return rejected(validationFailure)
        val fingerprint = requestFingerprint(fixed.expectedState)
        val existing = StructuredSpaceMutationStore.evidence(fixed.callerNamespace, fixed.externalKey)
        if (existing != null) {
            return if (existing.fingerprint == fingerprint) {
                existing.result.copy(
                    status = WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED,
                    reasonKey = "imyvmworldgeo.structured_mutation.already_matched"
                )
            } else {
                existing.result.copy(
                    status = WorldGeoStructuredSpaceMutationStatus.CONFLICT,
                    reasonKey = "imyvmworldgeo.structured_mutation.conflict"
                )
            }
        }
        return mutateExpected(fixed.expectedState) { result ->
            StructuredSpaceMutationStore.saveEvidence(
                fixed.callerNamespace,
                fixed.externalKey,
                StructuredSpaceMutationEvidence(fingerprint, result)
            )
        }
    }

    internal fun mutateExpected(
        expected: WorldGeoExpectedSpaceState,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit = { RegionDatabase.save() }
    ): WorldGeoStructuredSpaceMutationResult = try {
        when (expected) {
            is WorldGeoExpectedSubSpaceCreation -> create(expected, persist)
            is WorldGeoExpectedSubSpaceDeletion -> delete(expected, persist)
            is WorldGeoExpectedSubSpaceRange -> replaceRange(expected, persist)
            is WorldGeoExpectedSetting -> writeSetting(expected, false, persist)
            is WorldGeoExpectedPermission -> writePermission(expected, persist)
        }
    } catch (error: IOException) {
        persistenceFailed(null, null)
    } catch (error: RegionNotFoundException) {
        rejected("imyvmworldgeo.structured_mutation.rejected")
    } catch (error: IllegalArgumentException) {
        rejected("imyvmworldgeo.structured_mutation.rejected")
    } catch (error: IllegalStateException) {
        rejected("imyvmworldgeo.structured_mutation.rejected")
    }

    private fun create(
        expected: WorldGeoExpectedSubSpaceCreation,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val parent = resolveTarget(expected.parent, requireScope = true, requireSubSpace = false)
        val existing = parent.region.subSpaces.firstOrNull {
            it.parentScopeId == parent.scope!!.requireAssignedScopeId() &&
                it.name.equals(expected.name, ignoreCase = true)
        }
        if (existing != null) {
            if (!matches(existing, expected)) return rejected("imyvmworldgeo.structured_mutation.rejected")
            val id = canonical(parent.region, parent.scope, existing)
            return persistMatched(id, version(existing), persist)
        }
        val before: String? = null
        val subSpace = SubSpace(
            RegionDatabase.nextSubSpaceId(),
            expected.name,
            parent.scope!!.requireAssignedScopeId(),
            parent.scope.worldId,
            expected.shape,
            expected.entryMessage,
            stringTags = expected.stringTags,
            keyedTags = expected.keyedTags
        )
        parent.region.addSubSpaceFromOwner(subSpace)
        val id = canonical(parent.region, parent.scope, subSpace)
        val result = applied(id, before, version(subSpace))
        return persistApplied(result, { parent.region.removeSubSpaceFromOwner(subSpace) }, persist)
    }

    private fun delete(
        expected: WorldGeoExpectedSubSpaceDeletion,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val owner = resolveOwner(expected.target)
        val resolved = RegionDatabase.getSubSpaceById(expected.target.subSpaceId!!)
        if (resolved != null) {
            require(resolved.first === owner.region && resolved.second === owner.scope)
        }
        val subSpace = resolved?.third
        if (subSpace == null) return persistMatched(expected.target, null, persist)
        require(subSpace.parentScopeId == owner.scope!!.requireAssignedScopeId())
        val before = version(subSpace)
        val index = owner.region.removeSubSpaceFromOwner(subSpace)
        val result = applied(expected.target, before, null)
        return persistApplied(result, { owner.region.restoreSubSpaceFromOwner(index, subSpace) }, persist)
    }

    private fun replaceRange(
        expected: WorldGeoExpectedSubSpaceRange,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val target = resolveTarget(expected.target, requireScope = true, requireSubSpace = true)
        val subSpace = target.subSpace!!
        if (sameShape(subSpace.geoShape, expected.shape)) {
            return persistMatched(expected.target, version(subSpace), persist)
        }
        val oldShape = subSpace.geoShape
        val before = version(subSpace)
        target.region.replaceSubSpaceGeometryFromOwner(subSpace, expected.shape)
        val result = applied(expected.target, before, version(subSpace))
        return persistApplied(
            result,
            { target.region.replaceSubSpaceGeometryFromOwner(subSpace, oldShape) },
            persist
        )
    }

    private fun writeSetting(
        expected: WorldGeoExpectedSetting,
        allowPermission: Boolean,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val target = resolveTarget(expected.target)
        val key = parseKey(expected.key)
        require(allowPermission || key !is PermissionKeyLike)
        require(expected.playerUuid == null || key is EffectKey || key is PermissionKeyLike)
        val store = target.store
        val subject = expected.playerUuid?.let(SettingSubject::Player) ?: SettingSubject.Global
        val current = currentValue(store, key, expected.playerUuid)
        if (current?.toString() == expected.value || current == null && expected.value == null) {
            return persistMatched(expected.target, version(target), persist)
        }
        val previous = store.toLegacyList()
        if (expected.value == null) {
            store.remove(key, subject)
        } else {
            store.put(buildSetting(key, expected.value, expected.playerUuid))
        }
        val result = applied(expected.target, version(target, previous), version(target))
        return persistApplied(result, { store.replaceAll(previous) }, persist)
    }

    private fun writePermission(
        expected: WorldGeoExpectedPermission,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val key = parseKey(expected.key)
        require(key is PermissionKeyLike)
        return writeSetting(
            WorldGeoExpectedSetting(
                expected.target,
                expected.key,
                expected.value?.toString(),
                expected.playerUuid
            ),
            true,
            persist
        )
    }

    private fun persistApplied(
        result: WorldGeoStructuredSpaceMutationResult,
        rollback: () -> Unit,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult = try {
        persist(result)
        WorldGeoGeographicProfileSupport.invalidateAll("structured_space_mutation")
        result
    } catch (error: Exception) {
        rollback()
        persistenceFailed(result.canonicalSpaceId, result.beforeSpaceVersion)
    }

    private fun persistMatched(
        id: WorldGeoCanonicalSpaceId,
        version: String?,
        persist: (WorldGeoStructuredSpaceMutationResult) -> Unit
    ): WorldGeoStructuredSpaceMutationResult {
        val result = WorldGeoStructuredSpaceMutationResult(
            WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED,
            id,
            version,
            version,
            "imyvmworldgeo.structured_mutation.already_matched"
        )
        return try {
            persist(result)
            result
        } catch (error: Exception) {
            persistenceFailed(id, version)
        }
    }

    private fun resolveOwner(id: WorldGeoCanonicalSpaceId): ResolvedTarget {
        require(id.scopeId != null && id.subSpaceId != null)
        val region = RegionDatabase.getRegionByNumberId(id.regionId)
        val owner = RegionDatabase.getScopeById(ScopeId(id.scopeId))
            ?: throw IllegalArgumentException("unknown scope")
        require(owner.first === region)
        return ResolvedTarget(region, owner.second, null)
    }

    private fun resolveTarget(
        id: WorldGeoCanonicalSpaceId,
        requireScope: Boolean = false,
        requireSubSpace: Boolean = false
    ): ResolvedTarget {
        require(id.regionId > 0)
        require(id.scopeId != 0L)
        require(id.subSpaceId == null || id.subSpaceId > 0L)
        require(id.subSpaceId == null || id.scopeId != null)
        val region = RegionDatabase.getRegionByNumberId(id.regionId)
        val scope = id.scopeId?.let { raw ->
            val owner = RegionDatabase.getScopeById(ScopeId(raw))
                ?: throw IllegalArgumentException("unknown scope")
            require(owner.first === region)
            owner.second
        }
        val subSpace = id.subSpaceId?.let { raw ->
            val resolved = RegionDatabase.getSubSpaceById(raw)
                ?: throw IllegalArgumentException("unknown subspace")
            require(resolved.first === region && resolved.second === scope)
            resolved.third
        }
        require(!requireScope || scope != null)
        require(!requireSubSpace || subSpace != null)
        return ResolvedTarget(region, scope, subSpace)
    }

    private fun parseKey(value: String): SettingKey =
        runCatching { PermissionKey.valueOf(value) }.getOrNull()
            ?: runCatching { EffectKey.valueOf(value) }.getOrNull()
            ?: runCatching { RuleKey.valueOf(value) }.getOrNull()
            ?: runCatching { EntryExitToggleKey.valueOf(value) }.getOrNull()
            ?: runCatching { EntryExitMessageKey.valueOf(value) }.getOrNull()
            ?: when {
                ExtensionSettingRegistry.isRegisteredPermissionKey(value) ->
                    ExtensionSettingRegistry.permissionKey(value)
                ExtensionSettingRegistry.isRegisteredRuleKey(value) ->
                    ExtensionSettingRegistry.ruleKey(value)
                else -> throw IllegalArgumentException("unknown setting key")
            }

    private fun buildSetting(key: SettingKey, value: String, playerUuid: UUID?): Setting = when (key) {
        is PermissionKey -> PermissionSetting(key, value.toBooleanStrict(), playerUuid)
        is ExtensionPermissionKey -> ExtensionPermissionSetting(key, value.toBooleanStrict(), playerUuid)
        is EffectKey -> EffectSetting(key, value.toInt(), playerUuid)
        is RuleKey -> RuleSetting(key, value.toBooleanStrict())
        is ExtensionRuleKey -> ExtensionRuleSetting(key, value.toBooleanStrict())
        is EntryExitToggleKey -> EntryExitToggleSetting(key, value.toBooleanStrict())
        is EntryExitMessageKey -> EntryExitMessageSetting(key, value)
    }

    private fun currentValue(store: SettingStore, key: SettingKey, playerUuid: UUID?): Any? = when (key) {
        is PermissionKeyLike ->
            if (playerUuid == null) store.globalPermission(key) else store.playerPermission(key, playerUuid)
        is EffectKey ->
            if (playerUuid == null) store.globalEffect(key) else store.playerEffect(key, playerUuid)
        is RuleKey -> store.rule(key)
        is ExtensionRuleKey -> store.rule(key)
        is EntryExitToggleKey -> store.entryExitToggle(key)
        is EntryExitMessageKey -> store.entryExitMessage(key)
    }

    private fun matches(subSpace: SubSpace, expected: WorldGeoExpectedSubSpaceCreation): Boolean =
        sameShape(subSpace.geoShape, expected.shape) &&
            subSpace.entryMessage == expected.entryMessage &&
            subSpace.stringTags == expected.stringTags &&
            subSpace.keyedTags == expected.keyedTags

    @Suppress("DEPRECATION")
    private fun sameShape(first: GeoShape, second: GeoShape): Boolean =
        first.geoShapeType == second.geoShapeType && first.shapeParameter == second.shapeParameter

    private fun canonical(region: Region, scope: GeoScope?, subSpace: SubSpace?) =
        WorldGeoCanonicalSpaceId(
            region.numberID,
            scope?.requireAssignedScopeId()?.raw,
            subSpace?.subSpaceId
        )

    private fun version(value: Any, settings: List<Setting>? = null): String {
        val text = when (value) {
            is SubSpace -> buildString {
                append(value.subSpaceId).append('|').append(value.name).append('|')
                append(shapeText(value.geoShape)).append('|').append(value.entryMessage).append('|')
                append(value.stringTags.sorted()).append('|').append(value.keyedTags.toSortedMap()).append('|')
                append(settingsText(settings ?: value.settings))
            }
            is GeoScope -> buildString {
                append(value.requireAssignedScopeId().raw).append('|').append(value.scopeName).append('|')
                append(value.geoShape?.let(::shapeText)).append('|')
                append(settingsText(settings ?: value.settings))
            }
            is Region -> buildString {
                append(value.numberID).append('|').append(value.name).append('|')
                append(settingsText(settings ?: value.settings))
            }
            is ResolvedTarget -> when {
                value.subSpace != null -> return version(value.subSpace, settings)
                value.scope != null -> return version(value.scope, settings)
                else -> return version(value.region, settings)
            }
            else -> error("unsupported space version target")
        }
        return sha256(text)
    }

    @Suppress("DEPRECATION")
    private fun shapeText(shape: GeoShape): String =
        "${shape.geoShapeType}:${shape.shapeParameter.joinToString(",")}"

    private fun settingsText(settings: List<Setting>): String = settings
        .map(::settingText)
        .sorted()
        .joinToString(";")

    private fun settingText(setting: Setting): String =
        "${setting.javaClass.simpleName}:${setting.key}:${setting.playerUUID}:${setting.value}"

    private fun freeze(expected: WorldGeoExpectedSpaceState): WorldGeoExpectedSpaceState = when (expected) {
        is WorldGeoExpectedSubSpaceCreation -> expected.copy(
            stringTags = expected.stringTags.toSet(),
            keyedTags = expected.keyedTags.toMap()
        )
        else -> expected
    }

    private fun requestFingerprint(expected: WorldGeoExpectedSpaceState): String = sha256(
        when (expected) {
            is WorldGeoExpectedSubSpaceCreation ->
                "create:${expected.parent}:${expected.name}:${shapeText(expected.shape)}:${expected.entryMessage}:" +
                    "${expected.stringTags.sorted()}:${expected.keyedTags.toSortedMap()}"
            is WorldGeoExpectedSubSpaceDeletion -> "delete:${expected.target}"
            is WorldGeoExpectedSubSpaceRange -> "range:${expected.target}:${shapeText(expected.shape)}"
            is WorldGeoExpectedSetting ->
                "setting:${expected.target}:${expected.key}:${expected.value}:${expected.playerUuid}"
            is WorldGeoExpectedPermission ->
                "permission:${expected.target}:${expected.key}:${expected.value}:${expected.playerUuid}"
        }
    )

    private fun validateRequest(request: WorldGeoStructuredSpaceMutationRequest): String? {
        if (!namespacePattern.matches(request.callerNamespace)) {
            return "imyvmworldgeo.structured_mutation.invalid_namespace"
        }
        if (request.externalKey.isBlank()) {
            return "imyvmworldgeo.structured_mutation.invalid_external_key"
        }
        return null
    }

    private fun applied(id: WorldGeoCanonicalSpaceId, before: String?, after: String?) =
        WorldGeoStructuredSpaceMutationResult(
            WorldGeoStructuredSpaceMutationStatus.APPLIED,
            id,
            before,
            after,
            "imyvmworldgeo.structured_mutation.applied"
        )

    private fun rejected(reasonKey: String) = WorldGeoStructuredSpaceMutationResult(
        WorldGeoStructuredSpaceMutationStatus.REJECTED,
        null,
        null,
        null,
        reasonKey
    )

    private fun persistenceFailed(id: WorldGeoCanonicalSpaceId?, version: String?) =
        WorldGeoStructuredSpaceMutationResult(
            WorldGeoStructuredSpaceMutationStatus.PERSISTENCE_FAILED,
            id,
            version,
            version,
            "imyvmworldgeo.structured_mutation.persistence_failed"
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class ResolvedTarget(
        val region: Region,
        val scope: GeoScope?,
        val subSpace: SubSpace?
    ) {
        val store: SettingStore
            get() = subSpace?.settingStore ?: scope?.settingStore ?: region.settingStore
    }
}
