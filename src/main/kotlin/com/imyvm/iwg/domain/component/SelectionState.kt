package com.imyvm.iwg.domain.component

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier

sealed class HypotheticalShape {
    data class Normal(val shapeType: GeoShapeType) : HypotheticalShape()
    data class SubSpace(val regionName: String, val parentScope: GeoScope, val shapeType: GeoShapeType?) : HypotheticalShape()
    data class ModifySubSpace(
        val regionName: String,
        val parentScope: GeoScope,
        val subSpace: com.imyvm.iwg.domain.component.SubSpace,
        val shapeType: GeoShapeType?
    ) : HypotheticalShape()
    data class ModifyExisting(val scope: GeoScope) : HypotheticalShape()
}

class SelectionState(
    val points: MutableList<BlockPos> = mutableListOf(),
    var hypotheticalShape: HypotheticalShape? = null,
    val worldId: Identifier? = null
)