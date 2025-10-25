package com.imyvm.iwg.inter.api

import com.imyvm.iwg.application.region.filterRegionsByMark
import com.imyvm.iwg.application.region.parseFoundingTimeFromRegionId
import com.imyvm.iwg.domain.*
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.inter.api.helper.filterSettingsByType
import com.imyvm.iwg.util.translator.getUUIDFromPlayerName
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import java.util.*

@Suppress("unused")
object RegionDataApi {
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

    fun getRegionScopes(region: Region): List<GeoScope> =
        region.geometryScope

    fun getRegionScopePair(region: Region, scopeName: String): Pair<Region, GeoScope?> =
        RegionDatabase.getRegionAndScope(region, scopeName)

    fun getRegionScopePair(regionId: Int, scopeName: String): Pair<Region?, GeoScope?> =
        RegionDatabase.getRegionAndScope(regionId, scopeName)

    fun getRegionScopePairByLocation(x: Int, z: Int): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(x, z)

    fun getRegionScopePairByLocation(blockPos: BlockPos): Pair<Region, GeoScope>? =
        RegionDatabase.getRegionAndScopeAt(blockPos.x, blockPos.z)

    fun getScopeShape(scope: GeoScope): GeoShape? = scope.geoShape

    fun getScopeArea(scope: GeoScope): Double? =
        scope.geoShape?.calculateArea()

    fun getRegionArea(region: Region): Double = region.calculateTotalArea()

    fun getRegionGlobalSettings(region: Region): List<Setting> =
        region.settings.toSet().filter { !it.isPersonal }

    fun getRegionGlobalSettingsByType(region: Region, settingTypes: SettingTypes): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = false)

    fun getRegionPersonalSettings(region: Region, playerUUID: UUID,): List<Setting> =
        region.settings.toSet().filter { it.isPersonal && it.playerUUID == playerUUID }

    fun getRegionPersonalSettingsByType(
        region: Region,
        playerUUID: UUID,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(region.settings, settingTypes, isPersonal = true, playerUUID = playerUUID)

    fun getScopeGlobalSettings(scope: GeoScope): List<Setting> =
        scope.settings.toSet().filter { !it.isPersonal }

    fun getScopeGlobalSettingsByType(
        scope: GeoScope,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = false)

    fun getScopePersonalSettings(scope: GeoScope, playerUUID: UUID): List<Setting> =
        scope.settings.toSet().filter { it.isPersonal && it.playerUUID == playerUUID }

    fun getScopePersonalSettingsByType(
        scope: GeoScope,
        playerUUID: UUID,
        settingTypes: SettingTypes
    ): List<Setting> =
        filterSettingsByType(scope.settings, settingTypes, isPersonal = true, playerUUID = playerUUID)

    fun getPlayerUUID(server: MinecraftServer, playerName: String): UUID? {
        return getUUIDFromPlayerName(server, playerName)
    }
}