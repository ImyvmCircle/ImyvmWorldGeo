package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.ImyvmWorldGeo.Companion.MOD_ID
import com.mojang.serialization.Codec
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * Persistent crash-recovery marker for flight controlled by ImyvmWorldGeo.
 *
 * This marker is not a permission source and is never synchronized to clients. It only records
 * whether a saved player snapshot contains mod-owned flight or still requires landing protection.
 * Runtime permission is always recalculated from the player's current location. The marker is
 * removed after a safe landing or when vanilla creative/spectator flight takes ownership.
 */
internal enum class ManagedFlightRecovery(val serializedName: String) {
    OWNED_FLIGHT("owned_flight"),
    LANDING_REQUIRED("landing_required")
}

internal fun decodeManagedFlightRecovery(serializedName: String): ManagedFlightRecovery =
    ManagedFlightRecovery.entries.firstOrNull { it.serializedName == serializedName }
        ?: ManagedFlightRecovery.LANDING_REQUIRED

internal val MANAGED_FLIGHT_RECOVERY_CODEC: Codec<ManagedFlightRecovery> = Codec.STRING.xmap(
    ::decodeManagedFlightRecovery,
    ManagedFlightRecovery::serializedName
)

/**
 * Player-NBT attachment used only to coordinate crash-safe managed-flight recovery.
 *
 * It is persistent so it shares the player save boundary with abilities and fall distance. It has
 * no initializer, client synchronization, or copy-on-death behavior. Unknown serialized states are
 * decoded conservatively as [ManagedFlightRecovery.LANDING_REQUIRED].
 */
internal val MANAGED_FLIGHT_RECOVERY_ATTACHMENT: AttachmentType<ManagedFlightRecovery> by lazy(
    LazyThreadSafetyMode.NONE
) {
    AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "managed_flight_recovery"),
        MANAGED_FLIGHT_RECOVERY_CODEC
    )
}

internal fun registerManagedFlightRecoveryAttachment() {
    MANAGED_FLIGHT_RECOVERY_ATTACHMENT
}

internal fun ServerPlayer.getManagedFlightRecovery(): ManagedFlightRecovery? =
    (this as AttachmentTarget).getAttached(MANAGED_FLIGHT_RECOVERY_ATTACHMENT)

internal fun ServerPlayer.setManagedFlightRecovery(recovery: ManagedFlightRecovery?) {
    val target = this as AttachmentTarget
    if (target.getAttached(MANAGED_FLIGHT_RECOVERY_ATTACHMENT) != recovery) {
        target.setAttached(MANAGED_FLIGHT_RECOVERY_ATTACHMENT, recovery)
    }
}
