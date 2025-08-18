package com.imyvm.iwg

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
    fun save(path: Path) {
        val file = path.resolve(DATABASE_FILENAME)
        DataOutputStream(file.outputStream()).use { stream ->
            stream.writeInt(regions.size)
            for (region in regions) {
                stream.writeUTF(region.name)
                stream.writeInt(region.numberID)
                stream.writeInt(region.geometryScope.scopeId)
            }
        }
    }

    @Throws(IOException::class)
    fun load(path: Path) {
        val file = path.resolve(DATABASE_FILENAME)
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
                val scope = region.geometryScope
                scope.scopeId = stream.readInt()
                region.geometryScope = scope
                regions.add(region)
            }
        }
    }
}