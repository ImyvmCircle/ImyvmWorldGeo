package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodTimeline
import com.imyvm.iwg.domain.NaturalPeriodTimelineType
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

object PeriodTimelineStore {
    const val PRODUCTION_TIMELINE_ID = "production"
    private const val FORMAT_VERSION = 1
    private const val FILE_NAME = "iwg_period_timelines.json"
    private var sessionWorldRoot: Path? = null
    private var nextTestSequence = 1L
    private val timelines = mutableListOf<NaturalPeriodTimeline>()

    internal fun bindSession(worldRoot: Path, nowMillis: Long = System.currentTimeMillis()) {
        check(sessionWorldRoot == null) { "Period timeline store session is already active" }
        require(nowMillis >= 0L) { "timeline bind time must not be negative" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val path = root.resolve(FILE_NAME)
        val loaded = if (Files.exists(path)) read(path) else null
        timelines.clear()
        if (loaded == null) {
            timelines.add(productionTimeline(nowMillis))
            nextTestSequence = 1L
        } else {
            nextTestSequence = loaded.first
            timelines.addAll(loaded.second)
        }
        var changed = loaded == null
        if (timelines.none { it.timelineId == PRODUCTION_TIMELINE_ID }) {
            timelines.add(0, productionTimeline(nowMillis))
            changed = true
        }
        val testState = TestPeriodModeStore.currentState()
        val activeTest = timelines.singleOrNull { it.type == NaturalPeriodTimelineType.TEST && !it.closed }
        when {
            testState != null && activeTest == null -> {
                timelines.add(newTestTimeline(testState))
                changed = true
            }
            testState == null && activeTest != null -> {
                replace(activeTest.copy(endedAtMillis = nowMillis, closed = true))
                changed = true
            }
        }
        if (changed) write(path, nextTestSequence, timelines)
        sessionWorldRoot = root
    }

    internal fun unbindSession() {
        timelines.clear()
        nextTestSequence = 1L
        sessionWorldRoot = null
    }

    internal fun startTestTimeline(state: TestPeriodModeState): NaturalPeriodTimeline {
        check(timelines.none { it.type == NaturalPeriodTimelineType.TEST && !it.closed }) {
            "A test period timeline is already active"
        }
        val timeline = newTestTimeline(state)
        val next = timelines + timeline
        write(requireRoot().resolve(FILE_NAME), nextTestSequence, next)
        timelines.add(timeline)
        return timeline
    }

    internal fun closeActiveTestTimeline(endedAtMillis: Long) {
        require(endedAtMillis >= 0L) { "timeline end must not be negative" }
        val active = timelines.singleOrNull { it.type == NaturalPeriodTimelineType.TEST && !it.closed } ?: return
        val closed = active.copy(
            endedAtMillis = maxOf(active.startedAtMillis, endedAtMillis),
            closed = true
        )
        val next = timelines.map { if (it.timelineId == active.timelineId) closed else it }
        write(requireRoot().resolve(FILE_NAME), nextTestSequence, next)
        replace(closed)
    }

    fun activeTimeline(): NaturalPeriodTimeline =
        timelines.singleOrNull { it.type == NaturalPeriodTimelineType.TEST && !it.closed }
            ?: timelines.single { it.timelineId == PRODUCTION_TIMELINE_ID }

    fun getTimeline(timelineId: String): NaturalPeriodTimeline? =
        timelines.firstOrNull { it.timelineId == timelineId }

    fun getTimelines(): List<NaturalPeriodTimeline> = timelines.toList()

    private fun newTestTimeline(state: TestPeriodModeState): NaturalPeriodTimeline {
        val sequence = nextTestSequence
        nextTestSequence = Math.addExact(sequence, 1L)
        return NaturalPeriodTimeline(
            timelineId = "test-$sequence",
            type = NaturalPeriodTimelineType.TEST,
            sequence = sequence,
            startedAtMillis = state.startedAtMillis,
            endedAtMillis = null,
            closed = false,
            testWeekCount = state.weekCount,
            testWeekLengthMillis = state.weekLengthMillis
        )
    }

    private fun replace(timeline: NaturalPeriodTimeline) {
        val index = timelines.indexOfFirst { it.timelineId == timeline.timelineId }
        check(index >= 0)
        timelines[index] = timeline
    }

    private fun productionTimeline(startedAtMillis: Long) = NaturalPeriodTimeline(
        timelineId = PRODUCTION_TIMELINE_ID,
        type = NaturalPeriodTimelineType.PRODUCTION,
        sequence = 0L,
        startedAtMillis = startedAtMillis,
        endedAtMillis = null,
        closed = false,
        testWeekCount = null,
        testWeekLengthMillis = null
    )

    private fun read(path: Path): Pair<Long, List<NaturalPeriodTimeline>> {
        try {
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            require(root.get("formatVersion").asInt == FORMAT_VERSION) { "unsupported timeline format" }
            val nextSequence = root.get("nextTestSequence").asLong
            require(nextSequence > 0L) { "next test timeline sequence must be positive" }
            val loaded = root.getAsJsonArray("timelines").map { element ->
                val obj = element.asJsonObject
                NaturalPeriodTimeline(
                    timelineId = obj.get("timelineId").asString,
                    type = enumValueOf(obj.get("type").asString),
                    sequence = obj.get("sequence").asLong,
                    startedAtMillis = obj.get("startedAtMillis").asLong,
                    endedAtMillis = obj.get("endedAtMillis")?.takeUnless { it.isJsonNull }?.asLong,
                    closed = obj.get("closed").asBoolean,
                    testWeekCount = obj.get("testWeekCount")?.takeUnless { it.isJsonNull }?.asInt,
                    testWeekLengthMillis = obj.get("testWeekLengthMillis")?.takeUnless { it.isJsonNull }?.asLong
                ).also(::validate)
            }
            require(loaded.map { it.timelineId }.toSet().size == loaded.size) { "duplicate timeline id" }
            require(loaded.count { it.type == NaturalPeriodTimelineType.TEST && !it.closed } <= 1) {
                "multiple active test timelines"
            }
            val maxTestSequence = loaded.filter { it.type == NaturalPeriodTimelineType.TEST }
                .maxOfOrNull { it.sequence } ?: 0L
            require(nextSequence > maxTestSequence) { "next test timeline sequence is not ahead of the catalog" }
            return nextSequence to loaded
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid period timeline store", error)
        } catch (error: IllegalStateException) {
            throw IOException("Invalid period timeline store", error)
        } catch (error: NullPointerException) {
            throw IOException("Invalid period timeline store", error)
        }
    }

    private fun write(path: Path, nextSequence: Long, values: List<NaturalPeriodTimeline>) {
        values.forEach(::validate)
        require(values.map { it.timelineId }.toSet().size == values.size) { "duplicate timeline id" }
        require(values.count { it.type == NaturalPeriodTimelineType.TEST && !it.closed } <= 1) {
            "multiple active test timelines"
        }
        val root = JsonObject()
        root.addProperty("formatVersion", FORMAT_VERSION)
        root.addProperty("nextTestSequence", nextSequence)
        root.add("timelines", JsonArray().also { array ->
            values.forEach { timeline ->
                array.add(JsonObject().also { obj ->
                    obj.addProperty("timelineId", timeline.timelineId)
                    obj.addProperty("type", timeline.type.name)
                    obj.addProperty("sequence", timeline.sequence)
                    obj.addProperty("startedAtMillis", timeline.startedAtMillis)
                    timeline.endedAtMillis?.let { obj.addProperty("endedAtMillis", it) }
                    obj.addProperty("closed", timeline.closed)
                    timeline.testWeekCount?.let { obj.addProperty("testWeekCount", it) }
                    timeline.testWeekLengthMillis?.let { obj.addProperty("testWeekLengthMillis", it) }
                })
            }
        })
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use {
                val buffer = ByteBuffer.wrap(root.toString().toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) it.write(buffer)
                it.force(true)
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
            FileChannel.open(path.parent, StandardOpenOption.READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validate(timeline: NaturalPeriodTimeline) {
        require(timeline.timelineId.isNotBlank()) { "timeline id must not be blank" }
        require(timeline.sequence >= 0L) { "timeline sequence must not be negative" }
        require(timeline.startedAtMillis >= 0L) { "timeline start must not be negative" }
        require(timeline.closed == (timeline.endedAtMillis != null)) { "timeline closed state is inconsistent" }
        if (timeline.type == NaturalPeriodTimelineType.PRODUCTION) {
            require(timeline.timelineId == PRODUCTION_TIMELINE_ID && timeline.sequence == 0L && !timeline.closed) {
                "invalid production timeline"
            }
            require(timeline.testWeekCount == null && timeline.testWeekLengthMillis == null) {
                "production timeline has test configuration"
            }
        }
        timeline.endedAtMillis?.let { require(it >= timeline.startedAtMillis) { "timeline ends before it starts" } }
        if (timeline.type == NaturalPeriodTimelineType.TEST) {
            require(timeline.timelineId != PRODUCTION_TIMELINE_ID) { "test timeline uses production id" }
            require(timeline.sequence > 0L) { "test timeline sequence must be positive" }
            require(timeline.testWeekCount != null && timeline.testWeekCount > 0) { "invalid test week count" }
            require(timeline.testWeekLengthMillis != null && timeline.testWeekLengthMillis > 0L) {
                "invalid test week length"
            }
        }
    }

    private fun requireRoot(): Path =
        sessionWorldRoot ?: error("Period timeline store session is not active")
}
