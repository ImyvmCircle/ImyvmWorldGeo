package com.imyvm.iwg

import com.imyvm.iwg.domain.Region
import net.fabricmc.loader.api.FabricLoader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class RegionNotFoundException(message: String) : RuntimeException(message)

class RegionDatabase {

    @Throws(IOException::class)
    fun save() {
        val file = this.getDatabasePath()
        DataOutputStream(file.outputStream()).use { stream ->
            stream.writeInt(regions.size)
            for (region in regions) {
                stream.writeUTF(region.name)
                stream.writeInt(region.numberID)

                stream.writeInt(region.geometryScope.size)
                for (scope in region.geometryScope) {
                    stream.writeUTF(scope.scopeName)

                    val shape = scope.geoShape
                    if (shape == null) {
                        stream.writeBoolean(false)
                    } else {
                        stream.writeBoolean(true)
                        stream.writeInt(shape.geoShapeType.ordinal)
                        stream.writeInt(shape.shapeParameter.size)
                        for (p in shape.shapeParameter) {
                            stream.writeInt(p)
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun load() {
        val file = this.getDatabasePath()
        if (!file.exists()) {
            regions = mutableListOf()
            return
        }

        DataInputStream(file.inputStream()).use { stream ->
            val size = stream.readInt()
            regions = ArrayList(size)
            for (i in 0 until size) {
                val name = stream.readUTF()
                val numberID = stream.readInt()

                val scopeCount = stream.readInt()
                val scopes = ArrayList<Region.Companion.GeoScope>(scopeCount)

                for (s in 0 until scopeCount) {
                    val scopeName = stream.readUTF()
                    val hasShape = stream.readBoolean()

                    val shape = if (hasShape) {
                        val typeOrdinal = stream.readInt()
                        val geoShapeType = Region.Companion.GeoShapeType.entries[typeOrdinal]
                        val paramSize = stream.readInt()
                        val params = MutableList(paramSize) { stream.readInt() }
                        Region.Companion.GeoShape(geoShapeType, params)
                    } else {
                        null
                    }

                    scopes.add(Region.Companion.GeoScope(scopeName, shape))
                }

                regions.add(Region(name, numberID, scopes))
            }
        }
    }

    private fun getDatabasePath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DATABASE_FILENAME)
    }

    fun getRegionByName(name: String): Region {
        return regions.find { it.name == name }
            ?: throw RegionNotFoundException("Region with name '$name' not found.")
    }

    fun getRegionByNumberId(id: Int): Region {
        return regions.find { it.numberID == id }
            ?: throw RegionNotFoundException("Region with ID '$id' not found.")
    }

    fun getRegionList(): List<Region> {
        return regions
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

    fun getRegionAt(x: Int, z: Int): Region? {
        for (region in regions) {
            for (scope in region.geometryScope) {
                val geoShape = scope.geoShape
                if (geoShape != null) {
                    if (geoShape.isInside(x, z)) {
                        return region
                    }
                }
            }
        }
        return null
    }

    companion object{
        private const val DATABASE_FILENAME = "iwg_regions.db"
        lateinit var regions: MutableList<Region>
    }
}
