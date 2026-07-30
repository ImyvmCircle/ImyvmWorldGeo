package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors

private data class BehaviorSegment(
    val id: Long,
    val relativePath: String,
    val timelineId: String,
    val periodKind: NaturalPeriodKind,
    val periodId: String,
    val regionId: Int,
    val sequenceStart: Long,
    val sequenceEnd: Long,
    val entryCount: Int,
    val byteLength: Long,
    val checksum: String
)

private data class BehaviorManifest(
    val generation: Long,
    val nextSegmentId: Long,
    val publishedSequence: Long,
    val segments: List<BehaviorSegment>
)

internal object SegmentedBehaviorStatsStore {
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_FILE = "manifest.json"
    private const val ROOT_DIRECTORY = "iwg_behavior_stats"
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "worldgeo-stats-io").apply { isDaemon = true }
    }
    private var root: Path? = null
    private var manifest = BehaviorManifest(0L, 1L, 0L, emptyList())
    internal var failureInjector: ((String) -> Unit)? = null
    private var lastHistoricalReadThreadName: String? = null

    fun bindSession(worldRoot: Path, legacyPath: Path) {
        check(root == null) { "Segmented behavior stats session is already active" }
        val target = worldRoot.toAbsolutePath().normalize().resolve(ROOT_DIRECTORY)
        val loaded = io {
            Files.createDirectories(target)
            val path = target.resolve(MANIFEST_FILE)
            if (Files.exists(path)) readManifest(path) else {
                val legacy = BehaviorStatsStore.readStats(legacyPath)
                val initial = BehaviorManifest(0L, 1L, 0L, emptyList())
                if (legacy.isEmpty()) {
                    writeManifest(path, initial)
                    initial
                } else {
                    append(target, initial, legacy, 0L, 0L, "migration")
                }
            }
        }
        root = target
        manifest = loaded
    }

    fun unbindSession() {
        root = null
        manifest = BehaviorManifest(0L, 1L, 0L, emptyList())
        failureInjector = null
        lastHistoricalReadThreadName = null
    }

    fun publishedSequence(): Long = manifest.publishedSequence

    internal fun historicalEntryCountInMemory(): Int = 0

    internal fun lastHistoricalReadThread(): String? = lastHistoricalReadThreadName

    fun append(stats: Map<BehaviorStatsKey, Long>, sequenceStart: Long, sequenceEnd: Long) {
        if (stats.isEmpty()) return
        val target = requireRoot()
        val next = io { append(target, manifest, stats, sequenceStart, sequenceEnd, "append") }
        manifest = next
    }

    internal fun compact(
        timelineId: String,
        periodKind: NaturalPeriodKind,
        periodId: String,
        regionId: Int
    ) {
        val selected = manifest.segments.filter {
            it.timelineId == timelineId && it.periodKind == periodKind &&
                it.periodId == periodId && it.regionId == regionId
        }
        if (selected.size < 2) return
        val target = requireRoot()
        val current = manifest
        val next = io {
            val aggregate = linkedMapOf<BehaviorStatsKey, Long>()
            selected.forEach { segment ->
                readSegment(target, segment).forEach { (key, count) ->
                    aggregate[key] = Math.addExact(aggregate[key] ?: 0L, count)
                }
            }
            val base = current.copy(segments = current.segments - selected.toSet())
            append(
                target,
                base,
                aggregate,
                selected.minOf { it.sequenceStart },
                selected.maxOf { it.sequenceEnd },
                "compression"
            )
        }
        manifest = next
        io { selected.forEach { Files.deleteIfExists(target.resolve(it.relativePath)) } }
    }

    fun readAll(): Map<BehaviorStatsKey, Long> {
        val target = requireRoot()
        val snapshot = manifest
        return io {
            val result = linkedMapOf<BehaviorStatsKey, Long>()
            snapshot.segments.forEach { segment ->
                readSegment(target, segment).forEach { (key, count) ->
                    result[key] = Math.addExact(result[key] ?: 0L, count)
                }
            }
            result
        }
    }

    private fun append(
        target: Path,
        current: BehaviorManifest,
        stats: Map<BehaviorStatsKey, Long>,
        sequenceStart: Long,
        sequenceEnd: Long,
        phase: String
    ): BehaviorManifest {
        require(sequenceStart >= 0L && sequenceEnd >= sequenceStart) { "invalid behavior segment sequence" }
        if (phase != "migration" && phase != "compression") {
            require(sequenceStart > current.publishedSequence) { "duplicate behavior segment sequence" }
        }
        var nextId = current.nextSegmentId
        val written = mutableListOf<BehaviorSegment>()
        stats.entries.groupBy {
            listOf(it.key.timelineId, it.key.periodKind.name, it.key.periodId, it.key.regionId.toString())
        }.values.forEach { entries ->
            val first = entries.first().key
            val id = nextId
            nextId = Math.addExact(id, 1L)
            val relative = segmentPath(first, id)
            val bytes = segmentJson(entries).toString().toByteArray(Charsets.UTF_8)
            val path = target.resolve(relative)
            Files.createDirectories(path.parent)
            failureInjector?.invoke("$phase:segment")
            RegionDatabase.atomicWrite(path) { it.write(bytes) }
            val descriptor = BehaviorSegment(
                id, relative, first.timelineId, first.periodKind, first.periodId, first.regionId,
                sequenceStart, sequenceEnd, entries.size, bytes.size.toLong(), sha256(bytes)
            )
            readSegment(target, descriptor)
            written.add(descriptor)
        }
        val next = BehaviorManifest(
            current.generation + 1L,
            nextId,
            maxOf(current.publishedSequence, sequenceEnd),
            current.segments + written
        )
        validateManifest(next)
        failureInjector?.invoke("$phase:manifest")
        writeManifest(target.resolve(MANIFEST_FILE), next)
        return next
    }

    private fun readSegment(target: Path, descriptor: BehaviorSegment): Map<BehaviorStatsKey, Long> {
        lastHistoricalReadThreadName = Thread.currentThread().name
        val path = target.resolve(descriptor.relativePath)
        val bytes = Files.readAllBytes(path)
        if (bytes.size.toLong() != descriptor.byteLength || sha256(bytes) != descriptor.checksum) {
            throw IOException("Behavior stats segment checksum mismatch: ${descriptor.id}")
        }
        try {
            val array = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonArray
            require(array.size() == descriptor.entryCount) { "segment entry count mismatch" }
            return array.associate { element ->
                val obj = element.asJsonObject
                BehaviorStatsKey(
                    enumValueOf(obj.get("periodKind").asString),
                    obj.get("periodId").asString,
                    enumValueOf<WorldGeoBehaviorType>(obj.get("behaviorType").asString),
                    obj.get("regionId").asInt,
                    obj.get("scopeId")?.takeUnless { it.isJsonNull }?.asLong,
                    obj.get("subSpaceId")?.takeUnless { it.isJsonNull }?.asLong,
                    UUID.fromString(obj.get("playerUuid").asString),
                    obj.get("objectId")?.takeUnless { it.isJsonNull }?.asString,
                    obj.get("targetId")?.takeUnless { it.isJsonNull }?.asString,
                    obj.get("timelineId").asString
                ) to obj.get("count").asLong
            }
        } catch (error: RuntimeException) {
            throw IOException("Invalid behavior stats segment: ${descriptor.id}", error)
        }
    }

    private fun segmentJson(entries: List<Map.Entry<BehaviorStatsKey, Long>>) = JsonArray().also { array ->
        entries.forEach { (key, count) ->
            array.add(JsonObject().also { obj ->
                obj.addProperty("timelineId", key.timelineId)
                obj.addProperty("periodKind", key.periodKind.name)
                obj.addProperty("periodId", key.periodId)
                obj.addProperty("behaviorType", key.behaviorType.name)
                obj.addProperty("regionId", key.regionId)
                key.scopeId?.let { obj.addProperty("scopeId", it) }
                key.subSpaceId?.let { obj.addProperty("subSpaceId", it) }
                obj.addProperty("playerUuid", key.playerUuid.toString())
                key.objectId?.let { obj.addProperty("objectId", it) }
                key.targetId?.let { obj.addProperty("targetId", it) }
                obj.addProperty("count", count)
            })
        }
    }

    private fun segmentPath(key: BehaviorStatsKey, id: Long): String {
        val period = Base64.getUrlEncoder().withoutPadding().encodeToString(key.periodId.toByteArray())
        return "${key.timelineId}/${key.periodKind.name.lowercase()}/$period/${key.regionId}/segment-$id.json"
    }

    private fun readManifest(path: Path): BehaviorManifest {
        try {
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            require(root.get("formatVersion").asInt == FORMAT_VERSION)
            val loaded = BehaviorManifest(
                root.get("generation").asLong,
                root.get("nextSegmentId").asLong,
                root.get("publishedSequence").asLong,
                root.getAsJsonArray("segments").map { element ->
                    val obj = element.asJsonObject
                    BehaviorSegment(
                        obj.get("id").asLong,
                        obj.get("relativePath").asString,
                        obj.get("timelineId").asString,
                        enumValueOf(obj.get("periodKind").asString),
                        obj.get("periodId").asString,
                        obj.get("regionId").asInt,
                        obj.get("sequenceStart").asLong,
                        obj.get("sequenceEnd").asLong,
                        obj.get("entryCount").asInt,
                        obj.get("byteLength").asLong,
                        obj.get("checksum").asString
                    )
                }
            )
            validateManifest(loaded)
            return loaded
        } catch (error: RuntimeException) {
            throw IOException("Invalid behavior stats manifest", error)
        }
    }

    private fun writeManifest(path: Path, value: BehaviorManifest) {
        val obj = JsonObject()
        obj.addProperty("formatVersion", FORMAT_VERSION)
        obj.addProperty("generation", value.generation)
        obj.addProperty("nextSegmentId", value.nextSegmentId)
        obj.addProperty("publishedSequence", value.publishedSequence)
        obj.add("segments", JsonArray().also { array ->
            value.segments.forEach { segment ->
                array.add(JsonObject().also {
                    it.addProperty("id", segment.id)
                    it.addProperty("relativePath", segment.relativePath)
                    it.addProperty("timelineId", segment.timelineId)
                    it.addProperty("periodKind", segment.periodKind.name)
                    it.addProperty("periodId", segment.periodId)
                    it.addProperty("regionId", segment.regionId)
                    it.addProperty("sequenceStart", segment.sequenceStart)
                    it.addProperty("sequenceEnd", segment.sequenceEnd)
                    it.addProperty("entryCount", segment.entryCount)
                    it.addProperty("byteLength", segment.byteLength)
                    it.addProperty("checksum", segment.checksum)
                })
            }
        })
        RegionDatabase.atomicWrite(path) { it.write(obj.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun validateManifest(value: BehaviorManifest) {
        require(value.generation >= 0L && value.nextSegmentId > 0L && value.publishedSequence >= 0L)
        require(value.segments.map { it.id }.toSet().size == value.segments.size) { "duplicate segment id" }
        val maxId = value.segments.maxOfOrNull { it.id } ?: 0L
        require(value.nextSegmentId > maxId) { "next segment id is not ahead of the manifest" }
        require(value.publishedSequence >= (value.segments.maxOfOrNull { it.sequenceEnd } ?: 0L)) {
            "published sequence is behind a segment"
        }
        value.segments.forEach { segment ->
            val relative = Path.of(segment.relativePath)
            require(!relative.isAbsolute && relative.normalize() == relative && !relative.startsWith("..")) {
                "invalid segment path"
            }
            require(segment.id > 0L && segment.timelineId.isNotBlank() && segment.periodId.isNotBlank())
            require(segment.regionId > 0 && segment.sequenceStart >= 0L && segment.sequenceEnd >= segment.sequenceStart)
            require(segment.entryCount > 0 && segment.byteLength > 0L && segment.checksum.matches(Regex("[0-9a-f]{64}")))
        }
        value.segments.groupBy {
            listOf(it.timelineId, it.periodKind.name, it.periodId, it.regionId.toString())
        }.values.forEach { segments ->
            val sorted = segments.sortedBy { it.sequenceStart }
            sorted.zipWithNext().forEach { (left, right) ->
                require(left.sequenceEnd < right.sequenceStart) { "duplicate behavior segment sequence" }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun requireRoot(): Path = root ?: error("Segmented behavior stats session is not active")

    private fun <T> io(operation: () -> T): T = try {
        executor.submit<T> { operation() }.get()
    } catch (error: java.util.concurrent.ExecutionException) {
        throw error.cause ?: error
    }
}
