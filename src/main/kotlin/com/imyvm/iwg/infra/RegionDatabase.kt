package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList

class RegionNotFoundException(message: String) : RuntimeException(message)

object RegionDatabase {

    private lateinit var regions: MutableList<Region>
    private const val DATABASE_FILENAME = "iwg_regions.db"

    @Throws(IOException::class)
    fun save() {
        val file = getDatabasePath()
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
        val file = getDatabasePath()
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

    fun getRegionAndScope(regionId: Int, scopeName: String): Pair<Region?, GeoScope?> {
        val region = try {
            getRegionByNumberId(regionId)
        } catch (e: RegionNotFoundException) {
            return Pair(null, null)
        }
        val scope = region.geometryScope.find { it.scopeName == scopeName }
        return Pair(region, scope)
    }

    fun getRegionAndScope(region: Region, scopeName: String): Pair<Region, GeoScope?> {
        val scope = region.geometryScope.find { it.scopeName == scopeName }
        return Pair(region, scope)
    }

    fun getRegionAndScopeAt(world: World, x: Int, z: Int): Pair<Region, GeoScope>? {
        val server = world.server
        for (region in regions) {
            for (scope in region.geometryScope) {
                if (server?.let { scope.getWorld(it) } == world) {
                    val geoShape = scope.geoShape
                    if (geoShape != null) {
                        if (geoShape.containsPoint(x, z)) {
                            return Pair(region, scope)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun saveGeoScopes(stream: DataOutputStream, scopes: List<GeoScope>) {
        stream.writeInt(scopes.size)
        for (scope in scopes) {
            stream.writeUTF(scope.scopeName)
            stream.writeUTF(scope.worldId.toString())
            saveTeleportPoint(stream, scope.teleportPoint)
            stream.writeBoolean(scope.isTeleportPointPublic)
            saveGeoShape(stream, scope.geoShape)
            saveSettings(stream, scope.settings)
        }
    }

    private fun loadGeoScopes(stream: DataInputStream): MutableList<GeoScope> {
        val count = stream.readInt()
        val list = ArrayList<GeoScope>(count)

        repeat(count) {
            val scopeName = stream.readUTF()
            val worldId = Identifier.of(stream.readUTF())
            val teleportPoint = loadTeleportPoint(stream)
            val isTeleportPointPublic = stream.readBoolean()
            val geoShape = loadGeoShape(stream)
            val scopeSettings = loadSettings(stream)

            val scope = GeoScope(scopeName, worldId, teleportPoint, isTeleportPointPublic, geoShape)
            scope.settings.addAll(scopeSettings)
            list.add(scope)
        }

        return list
    }

    private fun saveTeleportPoint(stream: DataOutputStream, teleportPoint: BlockPos?) {
        if (teleportPoint == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeInt(teleportPoint.x)
            stream.writeInt(teleportPoint.y)
            stream.writeInt(teleportPoint.z)
        }
    }

    private fun loadTeleportPoint(stream: DataInputStream): BlockPos? {
        val hasTeleportPoint = stream.readBoolean()
        return if (!hasTeleportPoint) null
        else {
            val x = stream.readInt()
            val y = stream.readInt()
            val z = stream.readInt()
            BlockPos(x,y,z)
        }
    }

    private fun saveGeoShape(stream: DataOutputStream, shape: GeoShape?) {
        if (shape == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeInt(shape.geoShapeType.ordinal)
            stream.writeInt(shape.shapeParameter.size)
            shape.shapeParameter.forEach { stream.writeInt(it) }
        }
    }

    private fun loadGeoShape(stream: DataInputStream): GeoShape? {
        val hasShape = stream.readBoolean()
        return if (!hasShape) null
        else {
            val typeOrdinal = stream.readInt()
            val geoShapeType = GeoShapeType.entries[typeOrdinal]
            val paramCount = stream.readInt()
            val params = MutableList(paramCount) { stream.readInt() }
            GeoShape(geoShapeType, params)
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
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadPermissionSetting(stream: DataInputStream): PermissionSetting {
        val key = PermissionKey.entries[stream.readInt()]
        val value = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return PermissionSetting(key, value, uuid)
    }

    private fun saveEffectSetting(stream: DataOutputStream, setting: EffectSetting) {
        stream.writeInt(1)
        stream.writeInt(setting.key.ordinal)
        stream.writeInt(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadEffectSetting(stream: DataInputStream): EffectSetting {
        val key = EffectKey.entries[stream.readInt()]
        val value = stream.readInt()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return EffectSetting(key, value, uuid)
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
}
