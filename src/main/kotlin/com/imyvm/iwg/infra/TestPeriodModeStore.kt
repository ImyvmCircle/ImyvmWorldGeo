package com.imyvm.iwg.infra

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodKind
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class TestPeriodModeState(
    val startedAtMillis: Long,
    val weekCount: Int,
    val weekLengthMillis: Long
) {
    val endAtMillis: Long = Math.addExact(startedAtMillis, Math.multiplyExact(weekCount.toLong(), weekLengthMillis))
}

object TestPeriodModeStore {
    private const val FILE_NAME = "iwg_test_period_mode.json"
    private var sessionWorldRoot: Path? = null
    private var state: TestPeriodModeState? = null
    private val processedPeriodIds = linkedMapOf<NaturalPeriodKind, String>()

    internal fun bindSession(worldRoot: Path) {
        check(sessionWorldRoot == null) { "Test period mode store session is already active" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val loaded = read(root.resolve(FILE_NAME))
        state = loaded.first
        processedPeriodIds.clear()
        processedPeriodIds.putAll(loaded.second)
        sessionWorldRoot = root
    }

    internal fun unbindSession() {
        state = null
        processedPeriodIds.clear()
        sessionWorldRoot = null
    }

    fun currentState(): TestPeriodModeState? = state

    fun getProcessedPeriodIds(): Map<NaturalPeriodKind, String> = processedPeriodIds.toMap()

    fun replaceState(newState: TestPeriodModeState, periodIds: Map<NaturalPeriodKind, String>) {
        state = newState
        processedPeriodIds.clear()
        processedPeriodIds.putAll(periodIds)
        save()
    }

    fun replaceProcessedPeriodIds(periodIds: Map<NaturalPeriodKind, String>) {
        processedPeriodIds.clear()
        processedPeriodIds.putAll(periodIds)
        save()
    }

    fun clear() {
        state = null
        processedPeriodIds.clear()
        save()
    }

    internal fun save() {
        val root = sessionWorldRoot ?: error("Test period mode store session is not active")
        write(root.resolve(FILE_NAME), state, processedPeriodIds)
    }

    internal fun read(path: Path): Pair<TestPeriodModeState?, Map<NaturalPeriodKind, String>> {
        if (!Files.exists(path)) return null to emptyMap()
        try {
            val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
            val mode = obj.get("mode")?.takeUnless { it.isJsonNull }?.asJsonObject
            val loadedState = mode?.let {
                TestPeriodModeState(
                    startedAtMillis = longValue(it, "startedAtMillis"),
                    weekCount = intValue(it, "weekCount"),
                    weekLengthMillis = longValue(it, "weekLengthMillis")
                )
            }
            loadedState?.let(::validateState)
            val periodObj = obj.get("processedPeriodIds")?.takeUnless { it.isJsonNull }?.asJsonObject
            val periods = linkedMapOf<NaturalPeriodKind, String>()
            if (periodObj != null) {
                for (kind in NaturalPeriodKind.entries) {
                    val value = periodObj.get(kind.name)?.asString ?: continue
                    require(value.isNotBlank()) { "test period id for ${kind.name} must not be blank" }
                    periods[kind] = value
                }
            }
            return loadedState to periods
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid test period mode store", error)
        } catch (error: IllegalStateException) {
            throw IOException("Invalid test period mode store", error)
        }
    }

    internal fun write(path: Path, state: TestPeriodModeState?, periodIds: Map<NaturalPeriodKind, String>) {
        state?.let(::validateState)
        Files.createDirectories(path.parent)
        val obj = JsonObject()
        if (state != null) {
            val mode = JsonObject()
            mode.addProperty("startedAtMillis", state.startedAtMillis)
            mode.addProperty("weekCount", state.weekCount)
            mode.addProperty("weekLengthMillis", state.weekLengthMillis)
            obj.add("mode", mode)
        }
        val periodObj = JsonObject()
        for (kind in NaturalPeriodKind.entries) {
            val value = periodIds[kind] ?: continue
            require(value.isNotBlank()) { "test period id for ${kind.name} must not be blank" }
            periodObj.addProperty(kind.name, value)
        }
        obj.add("processedPeriodIds", periodObj)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(tmp, obj.toString())
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateState(state: TestPeriodModeState) {
        require(state.weekCount > 0) { "test period week count must be positive" }
        require(state.weekLengthMillis > 0L) { "test period week length must be positive" }
        require(state.startedAtMillis >= 0L) { "test period start must not be negative" }
    }

    private fun intValue(obj: JsonObject, name: String): Int = obj.get(name).asInt
    private fun longValue(obj: JsonObject, name: String): Long = obj.get(name).asLong
}
