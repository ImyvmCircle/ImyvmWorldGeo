package com.imyvm.iwg.application.interaction.scope.shape

import com.imyvm.iwg.application.interaction.scope.recreateScope
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

fun modifyScopeCircleRadius(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (!checkCircleParams(player, shapeParams, "circle_radius.invalid_circle")) return

    val centerX = shapeParams!![0]
    val centerZ = shapeParams[1]
    val oldRadius = shapeParams[2]

    val pos = selectedPositions[0]
    val dx = pos.x - centerX
    val dz = pos.z - centerZ
    val newRadius = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toInt()

    if (newRadius <= 0) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.circle_radius.nonpositive"))
        return
    }

    recreateCircleScope(
        player, region, existingScope, centerX, centerZ, newRadius,
        "interaction.meta.scope.modify.circle_radius.success",
        oldRadius, newRadius
    )
}

fun modifyScopeCircleCenter(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    selectedPositions: MutableList<BlockPos>
) {
    val shapeParams = existingScope.geoShape?.shapeParameter
    if (!checkCircleParams(player, shapeParams, "circle_center.invalid_circle")) return

    val radius = shapeParams!![2]
    val oldCenter = selectedPositions[0]
    val newCenter = selectedPositions[1]

    recreateCircleScope(
        player, region, existingScope, newCenter.x, newCenter.z, radius,
        "interaction.meta.scope.modify.circle_center.success",
        "${oldCenter.x},${oldCenter.z}",
        "${newCenter.x},${newCenter.z}",
        radius
    )
}

private fun checkCircleParams(
    player: ServerPlayerEntity,
    shapeParams: List<Int>?,
    messageKey: String
): Boolean {
    if (shapeParams == null || shapeParams.size < 3) {
        player.sendMessage(Translator.tr("interaction.meta.scope.modify.$messageKey"))
        return false
    }
    return true
}

private fun recreateCircleScope(
    player: ServerPlayerEntity,
    region: Region,
    existingScope: Region.Companion.GeoScope,
    centerX: Int,
    centerZ: Int,
    radius: Int,
    successKey: String,
    vararg params: Any
) {
    val newPositions = mutableListOf(
        BlockPos(centerX, 0, centerZ),
        BlockPos(centerX + radius, 0, centerZ)
    )

    recreateScope(
        player, region, existingScope, newPositions,
        Region.Companion.GeoShapeType.CIRCLE,
        successKey,
        *params
    )
}