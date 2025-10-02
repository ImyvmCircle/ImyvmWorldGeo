package com.imyvm.iwg.inter.api

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object RegionDataApi {
    fun addRegion(region: Region) = RegionDatabase.addRegion(region)
    fun removeRegion(region: Region) = RegionDatabase.removeRegion(region)
    fun renameRegion(region: Region, newName: String) = RegionDatabase.renameRegion(region, newName)
    fun getRegionList() = RegionDatabase.getRegionList()
    fun getRegionById(id: Int) = RegionDatabase.getRegionByNumberId(id)
    fun getRegionScopeByLocation(x: Int, z: Int) = RegionDatabase.getRegionAndScopeAt(x,z)
    fun getRegionScopeByLocation(blockPos: BlockPos) = RegionDatabase.getRegionAndScopeAt(blockPos.x, blockPos.z)
}