package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoShape
import java.util.UUID

data class WorldGeoCanonicalSpaceId(
    val regionId: Int,
    val scopeId: Long? = null,
    val subSpaceId: Long? = null
)

sealed interface WorldGeoExpectedSpaceState

data class WorldGeoExpectedSubSpaceCreation(
    val parent: WorldGeoCanonicalSpaceId,
    val name: String,
    val shape: GeoShape,
    val entryMessage: String? = null,
    val stringTags: Set<String> = emptySet(),
    val keyedTags: Map<String, String> = emptyMap()
) : WorldGeoExpectedSpaceState

data class WorldGeoExpectedSubSpaceDeletion(
    val target: WorldGeoCanonicalSpaceId
) : WorldGeoExpectedSpaceState

data class WorldGeoExpectedSubSpaceRange(
    val target: WorldGeoCanonicalSpaceId,
    val shape: GeoShape
) : WorldGeoExpectedSpaceState

data class WorldGeoExpectedSetting(
    val target: WorldGeoCanonicalSpaceId,
    val key: String,
    val value: String?,
    val playerUuid: UUID? = null
) : WorldGeoExpectedSpaceState

data class WorldGeoExpectedPermission(
    val target: WorldGeoCanonicalSpaceId,
    val key: String,
    val value: Boolean?,
    val playerUuid: UUID? = null
) : WorldGeoExpectedSpaceState

data class WorldGeoStructuredSpaceMutationRequest(
    val callerNamespace: String,
    val externalKey: String,
    val expectedState: WorldGeoExpectedSpaceState
)

enum class WorldGeoStructuredSpaceMutationStatus {
    APPLIED,
    ALREADY_MATCHED,
    REJECTED,
    CONFLICT,
    PERSISTENCE_FAILED
}

data class WorldGeoStructuredSpaceMutationResult(
    val status: WorldGeoStructuredSpaceMutationStatus,
    val canonicalSpaceId: WorldGeoCanonicalSpaceId?,
    val beforeSpaceVersion: String?,
    val afterSpaceVersion: String?,
    val reasonKey: String
)
