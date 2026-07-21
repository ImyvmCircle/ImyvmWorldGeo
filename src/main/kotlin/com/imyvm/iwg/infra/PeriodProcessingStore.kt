package com.imyvm.iwg.infra

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodKind
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object PeriodProcessingStore {
    private const val FILE_NAME = "iwg_periods.json"
    private var sessionWorldRoot: Path? = null
    private val processedPeriodIds = linkedMapOf<NaturalPeriodKind, String>()

    internal fun bindSession(worldRoot: Path) {
        check(sessionWorldRoot == null) { "Period processing store session is already active" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        processedPeriodIds.clear()
        processedPeriodIds.putAll(readProcessedPeriodIds(root.resolve(FILE_NAME)))
        sessionWorldRoot = root
    }

    internal fun unbindSession() {
        processedPeriodIds.clear()
        sessionWorldRoot = null
    }

    fun getProcessedPeriodIds(): Map<NaturalPeriodKind, String> = processedPeriodIds.toMap()

    fun replaceProcessedPeriodIds(periodIds: Map<NaturalPeriodKind, String>) {
        processedPeriodIds.clear()
        processedPeriodIds.putAll(periodIds)
        save()
    }

    internal fun save() {
        val root = sessionWorldRoot ?: error("Period processing store session is not active")
        writeProcessedPeriodIds(root.resolve(FILE_NAME), processedPeriodIds)
    }

    internal fun readProcessedPeriodIds(path: Path): Map<NaturalPeriodKind, String> {
        if (!Files.exists(path)) return emptyMap()
        try {
            val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
            val result = linkedMapOf<NaturalPeriodKind, String>()
            for (kind in NaturalPeriodKind.entries) {
                val value = obj.get(kind.name)?.asString ?: continue
                require(value.isNotBlank()) { "period id for ${kind.name} must not be blank" }
                result[kind] = value
            }
            return result
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid period processing store", error)
        } catch (error: IllegalStateException) {
            throw IOException("Invalid period processing store", error)
        }
    }

    internal fun writeProcessedPeriodIds(path: Path, periodIds: Map<NaturalPeriodKind, String>) {
        Files.createDirectories(path.parent)
        val obj = JsonObject()
        for (kind in NaturalPeriodKind.entries) {
            val value = periodIds[kind] ?: continue
            require(value.isNotBlank()) { "period id for ${kind.name} must not be blank" }
            obj.addProperty(kind.name, value)
        }
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(tmp, obj.toString())
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
