package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.inter.api.helper.filterSettingsByType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object RegionDataApi {

    fun addRegion(region: Region): Unit = RegionDatabase.addRegion(region)

    fun removeRegion(region: Region): Unit = RegionDatabase.removeRegion(region)

    fun renameRegion(region: Region, newName: String): Unit =
        RegionDatabase.renameRegion(region, newName)

    fun getRegion(id: Int): Region? {
        return try {
            RegionDatabase.getRegionByNumberId(id)
        } catch (e: RegionNotFoundException) {
            null
        }
    }

    fun getRegionList(): List<Region> = RegionDatabase.getRegionList()

    fun getRegionListFiltered(idMark: Int): List<Region> = filterRegionsByMark(idMark)

    fun getRegionFoundingTime(region: Region): Long =
        parseFoundingTimeFromRegionId(region.numberID)

    fun getRegionScopes(region: Region): List<Region.Companion.GeoScope> =
        region.geometryScope

    fun getRegionScopePair(region: Region, scopeName: String): Pair<Region, Region.Companion.GeoScope?> =
        RegionDatabase.getRegionAndScope(region, scopeName)

    fun getRegionScopePair(regionId: Int, scopeName: String): Pair<Region?, Region.Companion.GeoScope?> =
        RegionDatabase.getRegionAndScope(regionId, scopeName)

    fun getRegionScopePairByLocation(x: Int, z: Int): Pair<Region, Region.Companion.GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(x, z)

    fun getRegionScopePairByLocation(blockPos: BlockPos): Pair<Region, Region.Companion.GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(blockPos.x, blockPos.z)

    fun getScopeShape(scope: Region.Companion.GeoScope): Region.Companion.GeoShape? = scope.geoShape

    fun getScopeArea(scope: Region.Companion.GeoScope): Double? =
        scope.geoShape?.calculateArea()

    fun getRegionArea(region: Region): Double = region.calculateTotalArea()

    fun getRegionGlobalSettings(region: Region): List<Setting> =
        region.settings.toSet().filter { !it.isPersonal }

    fun getRegionGlobalSettingsByType(region: Region, settingTypes: SettingTypes): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = false)

    fun getRegionPersonalSettings(region: Region, player: ServerPlayerEntity): List<Setting> =
        region.settings.toSet().filter { it.isPersonal && it.playerUUID == player.uuid }

    fun getRegionPersonalSettingsByType(
        region: Region,
        player: ServerPlayerEntity,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = true, playerUUID = player.uuid)

    fun getScopeGlobalSettings(scope: Region.Companion.GeoScope): List<Setting> =
        scope.settings.toSet().filter { !it.isPersonal }

    fun getScopeGlobalSettingsByType(
        scope: Region.Companion.GeoScope,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = false)

    fun getScopePersonalSettings(scope: Region.Companion.GeoScope, player: ServerPlayerEntity): List<Setting> =
        scope.settings.toSet().filter { it.isPersonal && it.playerUUID == player.uuid }

    fun getScopePersonalSettingsByType(
        scope: Region.Companion.GeoScope,
        player: ServerPlayerEntity,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = true, playerUUID = player.uuid)

}