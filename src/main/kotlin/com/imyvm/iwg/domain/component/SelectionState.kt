package com.imyvm.iwg.domain.component

import net.minecraft.util.math.BlockPos

sealed class HypotheticalShape {
    data class Normal(val shapeType: GeoShapeType) : HypotheticalShape()
    data class ModifyExisting(val scope: GeoScope) : HypotheticalShape()
}

class SelectionState(
    val points: MutableList<BlockPos> = mutableListOf(),
    var hypotheticalShape: HypotheticalShape? = null
)
