package com.imyvm.iwg

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
    private val DATABASE_FILENAME = "iwg_regions.db"
    private lateinit var regions: MutableList<Region>

    fun getRegionByName(name: String): Region {
        return regions.find { it.name == name }
            ?: throw RegionNotFoundException("Region with name '$name' not found.")
    }

    fun getRegionByNumberId(id: Int): Region {
        return regions.find { it.numberID == id }
            ?: throw RegionNotFoundException("Region with ID '$id' not found.")
    }

    fun addRegion(region: Region) {
        regions.add(region)
    }

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
                val region = Region()
                region.name = stream.readUTF()
                region.numberID = stream.readInt()

                val scopeCount = stream.readInt()
                for (s in 0 until scopeCount) {
                    val scope = Region.Companion.GeoScope()
                    scope.scopeName = stream.readUTF()

                    val hasShape = stream.readBoolean()
                    if (hasShape) {
                        val shape = Region.Companion.GeoShape()
                        shape.geoShapeType =
                            Region.Companion.GeoShapeType.entries.toTypedArray()[stream.readInt()]
                        val paramSize = stream.readInt()
                        shape.shapeParameter = MutableList(paramSize) { stream.readInt() }
                        scope.geoShape = shape
                    }

                    region.geometryScope.add(scope)
                }

                regions.add(region)
            }
        }
    }

    private fun getDatabasePath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DATABASE_FILENAME)
    }
}
