package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object RegionDataApi {
    fun addRegion(region: Region) = RegionDatabase.addRegion(region)
    fun removeRegion(region: Region) = RegionDatabase.removeRegion(region)
    fun renameRegion(region: Region, newName: String) = RegionDatabase.renameRegion(region, newName)
    fun getRegion(id: Int) = RegionDatabase.getRegionByNumberId(id)
    fun getRegionScopePairByLocation(x: Int, z: Int) = RegionDatabase.getRegionAndScopeAt(x,z)
    fun getRegionScopePairByLocation(blockPos: BlockPos) = RegionDatabase.getRegionAndScopeAt(blockPos.x, blockPos.z)
    fun getRegionArea(region: Region) = region.calculateTotalArea()
    fun getRegionArea(id: Int) = RegionDatabase.getRegionByNumberId(id).calculateTotalArea()
    fun getRegionFoundingTime(region: Region) = parseFoundingTimeFromRegionId(region.numberID)
    fun getRegionFoundingTime(id: Int) = parseFoundingTimeFromRegionId(id)
    fun getRegionList() = RegionDatabase.getRegionList()
    fun getRegionListFiltered(idMark: Int) = filterRegionsByMark(idMark)
}