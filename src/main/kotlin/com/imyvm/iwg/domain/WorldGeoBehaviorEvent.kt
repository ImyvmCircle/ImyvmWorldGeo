package com.imyvm.iwg.domain

import net.minecraft.resources.Identifier
import java.util.UUID

enum class WorldGeoBehaviorType {
    BLOCK_PLACE,
    BLOCK_BREAK,
    ENTITY_DAMAGE,
    ENTITY_KILL,
    PLAYER_DEATH,
    CONTAINER_INTERACT,
    ITEM_USE,
    SPACE_ENTER,
    SPACE_EXIT,
    DEBUG_TEST
}

enum class WorldGeoSpaceLevel {
    REGION,
    SCOPE,
    SUBSPACE
}

data class WorldGeoBehaviorEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val type: WorldGeoBehaviorType,
    val playerUuid: UUID,
    val playerName: String,
    val dimensionId: Identifier,
    val x: Int,
    val y: Int,
    val z: Int,
    val unixMillis: Long,
    val regionId: Int? = null,
    val regionName: String? = null,
    val scopeId: Long? = null,
    val scopeName: String? = null,
    val subSpaceId: Long? = null,
    val subSpaceName: String? = null,
    val spaceLevel: WorldGeoSpaceLevel? = null,
    val objectId: String? = null,
    val targetId: String? = null,
    val quantity: Long = 1L,
    val source: String = "BEHAVIOR"
)
