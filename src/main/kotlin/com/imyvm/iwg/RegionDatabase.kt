package com.imyvm.iwg

import com.imyvm.iwg.domain.*
import net.fabricmc.loader.api.FabricLoader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path

class RegionNotFoundException(message: String) : RuntimeException(message)

class RegionDatabase {

    @Throws(IOException::class)
    fun save() {
        val file = this.getDatabasePath()
        DataOutputStream(file.toFile().outputStream()).use { stream ->
            stream.writeInt(regions.size)
            for (region in regions) {
                stream.writeUTF(region.name)
                stream.writeInt(region.numberID)
                saveGeoScopes(stream, region.geometryScope)
                saveSettings(stream, region.settings)
            }
        }
    }

    @Throws(IOException::class)
    fun load() {
        val file = this.getDatabasePath()
        if (!file.toFile().exists()) {
            regions = mutableListOf()
            return
        }

        DataInputStream(file.toFile().inputStream()).use { stream ->
            val regionCount = stream.readInt()
            regions = ArrayList(regionCount)

            repeat(regionCount) {
                val name = stream.readUTF()
                val numberID = stream.readInt()
                val geometryScopes = loadGeoScopes(stream)
                val settings = loadSettings(stream)

                val region = Region(name, numberID, geometryScopes)
                region.settings.addAll(settings)
                regions.add(region)
            }
        }
    }

    fun addRegion(region: Region) {
        regions.add(region)
    }

    fun removeRegion(regionToDelete: Region) {
        regions.removeIf { it.name == regionToDelete.name && it.numberID == regionToDelete.numberID }
    }

    fun renameRegion(region: Region, newName: String) {
        val isDuplicate = regions.any { otherRegion ->
            otherRegion.name.equals(newName, ignoreCase = true) && otherRegion.numberID != region.numberID
        }
        if (isDuplicate) {
            throw IllegalArgumentException("A region with the name '$newName' already exists.")
        }
        region.name = newName
    }

    fun getRegionList(): List<Region> {
        return regions
    }

    fun getRegionByName(name: String): Region {
        return regions.find { it.name == name }
            ?: throw RegionNotFoundException("Region with name '$name' not found.")
    }

    fun getRegionByNumberId(id: Int): Region {
        return regions.find { it.numberID == id }
            ?: throw RegionNotFoundException("Region with ID '$id' not found.")
    }

    fun getRegionAndScopeAt(x: Int, z: Int): Pair<Region, Region.Companion.GeoScope>? {
        for (region in regions) {
            for (scope in region.geometryScope) {
                val geoShape = scope.geoShape
                if (geoShape != null) {
                    if (geoShape.isInside(x, z)) {
                        return Pair(region, scope)
                    }
                }
            }
        }
        return null
    }

    private fun saveGeoScopes(stream: DataOutputStream, scopes: List<Region.Companion.GeoScope>) {
        stream.writeInt(scopes.size)
        for (scope in scopes) {
            stream.writeUTF(scope.scopeName)
            saveGeoShape(stream, scope.geoShape)
            saveSettings(stream, scope.settings)
        }
    }

    private fun loadGeoScopes(stream: DataInputStream): MutableList<Region.Companion.GeoScope> {
        val count = stream.readInt()
        val list = ArrayList<Region.Companion.GeoScope>(count)

        repeat(count) {
            val scopeName = stream.readUTF()
            val geoShape = loadGeoShape(stream)
            val scopeSettings = loadSettings(stream)

            val scope = Region.Companion.GeoScope(scopeName, geoShape)
            scope.settings.addAll(scopeSettings)
            list.add(scope)
        }

        return list
    }

    private fun saveGeoShape(stream: DataOutputStream, shape: Region.Companion.GeoShape?) {
        if (shape == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeInt(shape.geoShapeType.ordinal)
            stream.writeInt(shape.shapeParameter.size)
            shape.shapeParameter.forEach { stream.writeInt(it) }
        }
    }

    private fun loadGeoShape(stream: DataInputStream): Region.Companion.GeoShape? {
        val hasShape = stream.readBoolean()
        return if (!hasShape) null
        else {
            val typeOrdinal = stream.readInt()
            val geoShapeType = Region.Companion.GeoShapeType.entries[typeOrdinal]
            val paramCount = stream.readInt()
            val params = MutableList(paramCount) { stream.readInt() }
            Region.Companion.GeoShape(geoShapeType, params)
        }
    }

    private fun saveSettings(stream: DataOutputStream, settings: List<Setting>) {
        stream.writeInt(settings.size)
        settings.forEach { setting ->
            when (setting) {
                is PermissionSetting -> savePermissionSetting(stream, setting)
                is EffectSetting -> saveEffectSetting(stream, setting)
                is RuleSetting -> saveRuleSetting(stream, setting)
            }
        }
    }

    private fun loadSettings(stream: DataInputStream): MutableList<Setting> {
        val count = stream.readInt()
        val list = mutableListOf<Setting>()
        repeat(count) {
            val type = stream.readInt()
            val setting = when (type) {
                0 -> loadPermissionSetting(stream)
                1 -> loadEffectSetting(stream)
                2 -> loadRuleSetting(stream)
                else -> throw IOException("Unknown setting type")
            }
            list.add(setting)
        }
        return list
    }

    private fun savePermissionSetting(stream: DataOutputStream, setting: PermissionSetting) {
        stream.writeInt(0)
        stream.writeInt(setting.key.ordinal)
        stream.writeBoolean(setting.value)
        stream.writeBoolean(setting.isPersonal)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadPermissionSetting(stream: DataInputStream): PermissionSetting {
        val key = PermissionKey.entries[stream.readInt()]
        val value = stream.readBoolean()
        val isPersonal = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) java.util.UUID.fromString(uuidStr) else null
        return PermissionSetting(key, value, isPersonal, uuid)
    }

    private fun saveEffectSetting(stream: DataOutputStream, setting: EffectSetting) {
        stream.writeInt(1)
        stream.writeInt(setting.key.ordinal)
        stream.writeInt(setting.value)
        stream.writeBoolean(setting.isPersonal)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadEffectSetting(stream: DataInputStream): EffectSetting {
        val key = EffectKey.entries[stream.readInt()]
        val value = stream.readInt()
        val isPersonal = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) java.util.UUID.fromString(uuidStr) else null
        return EffectSetting(key, value, isPersonal, uuid)
    }

    private fun saveRuleSetting(stream: DataOutputStream, setting: RuleSetting) {
        stream.writeInt(2)
        stream.writeInt(setting.key.ordinal)
        stream.writeBoolean(setting.value)
    }

    private fun loadRuleSetting(stream: DataInputStream): RuleSetting {
        val key = RuleKey.entries[stream.readInt()]
        val value = stream.readBoolean()
        return RuleSetting(key, value)
    }

    private fun getDatabasePath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DATABASE_FILENAME)
    }

    companion object {
        private const val DATABASE_FILENAME = "iwg_regions.db"
        lateinit var regions: MutableList<Region>
    }
}
