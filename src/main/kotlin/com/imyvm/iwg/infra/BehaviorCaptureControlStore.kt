package com.imyvm.iwg.infra

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodBounds
import com.imyvm.iwg.domain.WorldGeoMissingCaptureInterval
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

internal object BehaviorCaptureControlStore {
    private const val FORMAT_VERSION = 1
    private const val CONTROL_DIRECTORY = "iwg_behavior_stats/control"
    private const val SESSION_FILE = "session.json"
    private const val INTERVAL_DIRECTORY = "missing"
    private var root: Path? = null
    private var nextIntervalId = 1L
    private var activeInterval: WorldGeoMissingCaptureInterval? = null

    fun bindSession(worldRoot: Path, nowMillis: Long = System.currentTimeMillis()) {
        check(root == null) { "Behavior capture control session is already active" }
        require(nowMillis >= 0L) { "session time must not be negative" }
        val target = worldRoot.toAbsolutePath().normalize().resolve(CONTROL_DIRECTORY)
        Files.createDirectories(target.resolve(INTERVAL_DIRECTORY))
        nextIntervalId = findNextIntervalId(target)
        val sessionPath = target.resolve(SESSION_FILE)
        if (Files.exists(sessionPath)) {
            val previous = readSession(sessionPath)
            if (!previous.closed) {
                appendInterval(
                    target,
                    WorldGeoMissingCaptureInterval(previous.openedAtMillis, nowMillis, 0L)
                )
            }
        }
        writeSession(sessionPath, nowMillis, false)
        activeInterval = null
        root = target
    }

    fun closeSession(nowMillis: Long = System.currentTimeMillis()) {
        val target = root ?: return
        require(nowMillis >= 0L) { "session time must not be negative" }
        activeInterval?.let { appendInterval(target, it.copy(endMillis = nowMillis)) }
        activeInterval = null
        writeSession(target.resolve(SESSION_FILE), nowMillis, true)
        root = null
        nextIntervalId = 1L
    }

    fun abandonSession() {
        root = null
        nextIntervalId = 1L
        activeInterval = null
    }

    fun startMissing(atMillis: Long) {
        requireRoot()
        if (activeInterval == null) {
            activeInterval = WorldGeoMissingCaptureInterval(atMillis, null, 1L)
        } else {
            noteMissing(atMillis)
        }
    }

    fun noteMissing(atMillis: Long) {
        require(atMillis >= 0L) { "missing event time must not be negative" }
        val current = activeInterval ?: error("Missing capture interval is not active")
        activeInterval = current.copy(
            startMillis = minOf(current.startMillis, atMillis),
            droppedEventCount = if (current.droppedEventCount == Long.MAX_VALUE) {
                Long.MAX_VALUE
            } else {
                current.droppedEventCount + 1L
            }
        )
    }

    fun finishMissing(atMillis: Long) {
        val target = requireRoot()
        val current = activeInterval ?: return
        appendInterval(target, current.copy(endMillis = maxOf(current.startMillis, atMillis)))
        activeInterval = null
    }

    fun activeMissingInterval(): WorldGeoMissingCaptureInterval? = activeInterval

    fun intersecting(bounds: NaturalPeriodBounds): List<WorldGeoMissingCaptureInterval> {
        val target = requireRoot()
        val persisted = Files.list(target.resolve(INTERVAL_DIRECTORY)).use { paths ->
            paths.filter { it.name.startsWith("interval-") && it.name.endsWith(".json") }
                .map(::readInterval)
                .filter { intersects(it, bounds) }
                .toList()
        }
        return activeInterval
            ?.takeIf { intersects(it, bounds) }
            ?.let { persisted + it }
            ?: persisted
    }

    fun resetForTest() {
        root = null
        nextIntervalId = 1L
        activeInterval = null
    }

    private fun intersects(interval: WorldGeoMissingCaptureInterval, bounds: NaturalPeriodBounds): Boolean {
        val endExclusive = interval.endMillis ?: Long.MAX_VALUE
        return interval.startMillis < bounds.endMillis && endExclusive > bounds.startMillis
    }

    private fun findNextIntervalId(target: Path): Long =
        Files.list(target.resolve(INTERVAL_DIRECTORY)).use { paths ->
            val max = paths.map { path ->
                path.name.removePrefix("interval-").removeSuffix(".json").toLongOrNull() ?: 0L
            }.max(Long::compareTo).orElse(0L)
            Math.addExact(max, 1L)
        }

    private fun appendInterval(target: Path, interval: WorldGeoMissingCaptureInterval) {
        val end = requireNotNull(interval.endMillis) { "persisted missing interval must be closed" }
        require(interval.startMillis >= 0L && end >= interval.startMillis && interval.droppedEventCount >= 0L)
        val id = nextIntervalId
        nextIntervalId = Math.addExact(id, 1L)
        val obj = JsonObject().also {
            it.addProperty("formatVersion", FORMAT_VERSION)
            it.addProperty("startMillis", interval.startMillis)
            it.addProperty("endMillis", end)
            it.addProperty("droppedEventCount", interval.droppedEventCount)
        }
        RegionDatabase.atomicWrite(target.resolve(INTERVAL_DIRECTORY).resolve("interval-$id.json")) {
            output -> output.write(obj.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun readInterval(path: Path): WorldGeoMissingCaptureInterval = try {
        val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
        require(obj.get("formatVersion").asInt == FORMAT_VERSION)
        WorldGeoMissingCaptureInterval(
            obj.get("startMillis").asLong,
            obj.get("endMillis").asLong,
            obj.get("droppedEventCount").asLong
        ).also {
            require(it.startMillis >= 0L && requireNotNull(it.endMillis) >= it.startMillis)
            require(it.droppedEventCount >= 0L)
        }
    } catch (error: RuntimeException) {
        throw IOException("Invalid behavior capture interval: ${path.name}", error)
    }

    private fun readSession(path: Path): SessionMarker = try {
        val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
        require(obj.get("formatVersion").asInt == FORMAT_VERSION)
        SessionMarker(obj.get("openedAtMillis").asLong, obj.get("closed").asBoolean).also {
            require(it.openedAtMillis >= 0L)
        }
    } catch (error: RuntimeException) {
        throw IOException("Invalid behavior capture session marker", error)
    }

    private fun writeSession(path: Path, openedAtMillis: Long, closed: Boolean) {
        val obj = JsonObject().also {
            it.addProperty("formatVersion", FORMAT_VERSION)
            it.addProperty("openedAtMillis", openedAtMillis)
            it.addProperty("closed", closed)
        }
        RegionDatabase.atomicWrite(path) { it.write(obj.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun requireRoot(): Path = root ?: error("Behavior capture control session is not active")

    private data class SessionMarker(val openedAtMillis: Long, val closed: Boolean)
}
