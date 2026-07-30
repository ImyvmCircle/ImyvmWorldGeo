package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.NaturalPeriodKey
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointPage
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointRequest
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointResult
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsCheckpointStatus
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsEntry
import com.imyvm.iwg.domain.WorldGeoBehaviorStatsPageQuery
import com.imyvm.iwg.domain.WorldGeoBehaviorType
import com.imyvm.iwg.domain.WorldGeoPeriodCompleteness
import com.imyvm.iwg.domain.WorldGeoPeriodDataStatus
import net.minecraft.server.MinecraftServer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal data class BehaviorStatsCheckpointBatch(
    val stats: Map<BehaviorStatsKey, Long>,
    val sequenceStart: Long?,
    val sequenceEnd: Long,
    val cutoffSequence: Long,
    @Volatile
    var published: Boolean = false
)

private class ActiveCheckpoint(
    val checkpointId: UUID,
    val batch: BehaviorStatsCheckpointBatch,
    val result: CompletableFuture<WorldGeoBehaviorStatsCheckpointResult>,
    @Volatile var value: WorldGeoBehaviorStatsCheckpointResult? = null,
    @Volatile var error: Throwable? = null,
    var settled: Boolean = false
)

internal object BehaviorStatsCheckpointService {
    private const val FORMAT_VERSION = 1
    private const val ROOT_DIRECTORY = "iwg_behavior_checkpoints"
    private const val MANIFEST_FILE = "manifest.json"
    private var root: Path? = null
    private val active = mutableSetOf<UUID>()
    private val inFlight = mutableSetOf<ActiveCheckpoint>()
    private var accepting = false
    internal var failureInjector: ((String) -> Unit)? = null

    fun bindSession(worldRoot: Path) {
        check(root == null) { "Behavior stats checkpoint session is already active" }
        val target = worldRoot.toAbsolutePath().normalize().resolve(ROOT_DIRECTORY)
        Files.createDirectories(target)
        root = target
        synchronized(this) { accepting = true }
    }

    fun unbindSession() {
        quiesceForShutdown()
        synchronized(this) {
            active.clear()
            inFlight.clear()
        }
        failureInjector = null
        root = null
    }

    internal fun quiesceForShutdown() {
        if (root == null) return
        synchronized(this) { accepting = false }
        try {
            SegmentedBehaviorStatsStore.awaitIdle()
            val pending = synchronized(this) { inFlight.toList() }
            pending.forEach(::settle)
        } catch (error: Throwable) {
            BehaviorStatsStore.markSessionUnclean()
            throw error
        }
    }

    fun request(
        server: MinecraftServer,
        request: WorldGeoBehaviorStatsCheckpointRequest
    ): CompletableFuture<WorldGeoBehaviorStatsCheckpointResult> =
        request(request) { operation -> server.execute(operation) }

    internal fun request(
        request: WorldGeoBehaviorStatsCheckpointRequest,
        dispatch: ((() -> Unit) -> Unit)
    ): CompletableFuture<WorldGeoBehaviorStatsCheckpointResult> {
        val fixed = request.copy(
            query = request.query.copy(
                objectIds = request.query.objectIds?.toSet(),
                playerUuids = request.query.playerUuids?.toSet()
            )
        )
        validate(fixed)
        val result = CompletableFuture<WorldGeoBehaviorStatsCheckpointResult>()
        if (!synchronized(this) { accepting }) {
            result.completeExceptionally(IllegalStateException("Checkpoint service is stopping"))
            return result
        }
        dispatch {
            try {
                if (!synchronized(this) { accepting }) {
                    result.completeExceptionally(IllegalStateException("Checkpoint service is stopping"))
                    return@dispatch
                }
                val completeness = BehaviorStatsStore.queryCompleteness(fixed.query.periodKey)
                if (completeness.status != WorldGeoPeriodDataStatus.COMPLETE) {
                    val status = if (completeness.status == WorldGeoPeriodDataStatus.INCOMPLETE) {
                        WorldGeoBehaviorStatsCheckpointStatus.INCOMPLETE
                    } else {
                        WorldGeoBehaviorStatsCheckpointStatus.UNAVAILABLE
                    }
                    result.complete(
                        WorldGeoBehaviorStatsCheckpointResult(
                            status,
                            fixed.checkpointId,
                            null,
                            null,
                            null,
                            completeness
                        )
                    )
                    return@dispatch
                }
                val reserved = synchronized(this) { active.add(fixed.checkpointId) }
                if (!reserved) {
                    result.completeExceptionally(IllegalStateException("Checkpoint request is already active"))
                    return@dispatch
                }
                failureInjector?.invoke("checkpoint:exchange")
                val batch = BehaviorStatsStore.exchangePendingForCheckpoint()
                val checkpoint = ActiveCheckpoint(fixed.checkpointId, batch, result)
                synchronized(this) { inFlight.add(checkpoint) }
                publish(fixed, completeness, batch).whenComplete { value, error ->
                    checkpoint.value = value
                    checkpoint.error = error
                    dispatch { settle(checkpoint) }
                }
            } catch (error: Throwable) {
                synchronized(this) { active.remove(fixed.checkpointId) }
                result.completeExceptionally(error)
            }
        }
        return result
    }

    private fun settle(checkpoint: ActiveCheckpoint) {
        val claimed = synchronized(this) {
            if (checkpoint.settled) {
                false
            } else {
                checkpoint.settled = true
                active.remove(checkpoint.checkpointId)
                inFlight.remove(checkpoint)
                true
            }
        }
        if (!claimed) return
        try {
            if (checkpoint.batch.published) {
                BehaviorStatsStore.completeCheckpointBatch()
            } else {
                BehaviorStatsStore.restoreCheckpointBatch(checkpoint.batch)
            }
            val error = checkpoint.error
            if (error != null) {
                BehaviorStatsStore.markSessionUnclean()
                checkpoint.result.completeExceptionally(error)
            } else {
                checkpoint.result.complete(requireNotNull(checkpoint.value))
            }
        } catch (error: Throwable) {
            BehaviorStatsStore.markSessionUnclean()
            checkpoint.result.completeExceptionally(error)
            throw error
        }
    }

    internal fun publishedCheckpointSequence(checkpointId: UUID): Long? =
        readManifest(checkpointId)?.takeIf { it.complete }?.cutoffSequence

    fun readPage(
        checkpointId: UUID,
        pageIndex: Int
    ): CompletableFuture<WorldGeoBehaviorStatsCheckpointPage?> {
        require(pageIndex >= 0) { "page index must not be negative" }
        return SegmentedBehaviorStatsStore.submit {
            val manifest = readManifest(checkpointId) ?: return@submit null
            if (!manifest.complete || pageIndex >= manifest.pages.size) return@submit null
            val descriptor = manifest.pages[pageIndex]
            val entries = readPageFile(checkpointDirectory(checkpointId).resolve(descriptor.relativePath), descriptor)
            WorldGeoBehaviorStatsCheckpointPage(
                checkpointId,
                pageIndex,
                manifest.manifestVersion,
                entries,
                pageIndex + 1 < manifest.pages.size
            )
        }
    }

    internal fun deleteUnavailable(unavailable: (com.imyvm.iwg.domain.NaturalPeriodKey) -> Boolean) {
        val target = root ?: return
        SegmentedBehaviorStatsStore.submit {
            if (!Files.exists(target)) return@submit
            Files.list(target).use { paths ->
                paths.filter(Files::isDirectory).forEach { directory ->
                    val checkpointId = runCatching { UUID.fromString(directory.fileName.toString()) }.getOrNull()
                        ?: return@forEach
                    val manifest = readManifest(checkpointId) ?: return@forEach
                    if (unavailable(manifest.query.periodKey)) deleteDirectory(directory)
                }
            }
        }.join()
    }

    private fun publish(
        request: WorldGeoBehaviorStatsCheckpointRequest,
        completeness: WorldGeoPeriodCompleteness,
        batch: BehaviorStatsCheckpointBatch
    ): CompletableFuture<WorldGeoBehaviorStatsCheckpointResult> =
        SegmentedBehaviorStatsStore.submit {
            val existing = readManifest(request.checkpointId)
            if (existing != null) {
                if (existing.query != request.query || existing.pageSize != request.pageSize) {
                    return@submit result(
                        WorldGeoBehaviorStatsCheckpointStatus.VERSION_CONFLICT,
                        request.checkpointId,
                        existing,
                        completeness
                    )
                }
                if (existing.complete) {
                    val status = if (baselineStillAvailable(request.checkpointId, existing)) {
                        WorldGeoBehaviorStatsCheckpointStatus.ALREADY_PUBLISHED
                    } else {
                        WorldGeoBehaviorStatsCheckpointStatus.VERSION_CONFLICT
                    }
                    return@submit result(status, request.checkpointId, existing, completeness)
                }
            }
            val cutoff = existing?.cutoffSequence ?: batch.cutoffSequence
            val pending = existing ?: CheckpointManifest(
                false,
                request.query,
                request.pageSize,
                cutoff,
                -1L,
                emptyList()
            )
            if (existing == null) writeManifest(request.checkpointId, pending, "checkpoint:request")
            if (SegmentedBehaviorStatsStore.publishedSequence() < cutoff) {
                if (batch.sequenceStart == null) {
                    return@submit result(
                        WorldGeoBehaviorStatsCheckpointStatus.VERSION_CONFLICT,
                        request.checkpointId,
                        pending,
                        completeness
                    )
                }
                SegmentedBehaviorStatsStore.publishCheckpointBatch(
                    batch.stats,
                    batch.sequenceStart,
                    batch.sequenceEnd
                )
                batch.published = true
            }
            val snapshot = SegmentedBehaviorStatsStore.openSnapshot(request.query, cutoff)
            try {
                val pages = writePages(request.checkpointId, snapshot, request.query, request.pageSize)
                val completed = pending.copy(
                    complete = true,
                    manifestVersion = snapshot.manifestVersion,
                    pages = pages
                )
                writeManifest(request.checkpointId, completed, "checkpoint:manifest")
                result(WorldGeoBehaviorStatsCheckpointStatus.PUBLISHED, request.checkpointId, completed, completeness)
            } finally {
                SegmentedBehaviorStatsStore.closeSnapshot(snapshot)
            }
        }

    private fun writePages(
        checkpointId: UUID,
        snapshot: BehaviorStatsReadSnapshot,
        query: WorldGeoBehaviorStatsPageQuery,
        pageSize: Int
    ): List<CheckpointPageDescriptor> {
        val target = checkpointDirectory(checkpointId)
        Files.createDirectories(target)
        val pages = mutableListOf<CheckpointPageDescriptor>()
        var cursor: BehaviorStatsKey? = null
        do {
            failureInjector?.invoke("checkpoint:page")
            val slice = SegmentedBehaviorStatsStore.readPage(snapshot, query, cursor, pageSize)
            if (slice.entries.isEmpty() && pages.isEmpty()) break
            val bytes = pageJson(slice.entries).toString().toByteArray(Charsets.UTF_8)
            val relative = "page-${pages.size}.json"
            val path = target.resolve(relative)
            RegionDatabase.atomicWrite(path) { it.write(bytes) }
            val descriptor = CheckpointPageDescriptor(relative, slice.entries.size, bytes.size.toLong(), sha256(bytes))
            failureInjector?.invoke("checkpoint:page-validation")
            readPageFile(path, descriptor)
            pages.add(descriptor)
            cursor = slice.nextCursor
        } while (slice.hasMore)
        return pages
    }

    private fun baselineStillAvailable(checkpointId: UUID, manifest: CheckpointManifest): Boolean {
        val snapshot = SegmentedBehaviorStatsStore.openSnapshot(manifest.query)
        try {
            manifest.pages.forEach { descriptor ->
                val baseline = readPageFile(checkpointDirectory(checkpointId).resolve(descriptor.relativePath), descriptor)
                val keys = baseline.associate { entry ->
                    entry.toKey(manifest.query.periodKey.timelineId) to entry.count
                }
                val current = SegmentedBehaviorStatsStore.readCounts(snapshot, manifest.query, keys.keys)
                if (keys.any { (key, count) -> (current[key] ?: 0L) < count }) return false
            }
            return true
        } finally {
            SegmentedBehaviorStatsStore.closeSnapshot(snapshot)
        }
    }

    private fun result(
        status: WorldGeoBehaviorStatsCheckpointStatus,
        checkpointId: UUID,
        manifest: CheckpointManifest,
        completeness: WorldGeoPeriodCompleteness
    ) = WorldGeoBehaviorStatsCheckpointResult(
        status,
        checkpointId,
        manifest.cutoffSequence,
        manifest.manifestVersion.takeIf { manifest.complete },
        manifest.pages.size.takeIf { manifest.complete },
        completeness
    )

    private fun validate(request: WorldGeoBehaviorStatsCheckpointRequest) {
        require(request.pageSize in 1..BehaviorStatsPageStreamService.MAX_PAGE_SIZE) {
            "page size must be between 1 and " + BehaviorStatsPageStreamService.MAX_PAGE_SIZE
        }
        validateQuery(request.query)
    }

    private fun validateQuery(query: WorldGeoBehaviorStatsPageQuery) {
        require(query.periodKey.timelineId.isNotBlank()) { "timeline id must not be blank" }
        require(query.periodKey.periodId.isNotBlank()) { "period id must not be blank" }
        require(query.regionId > 0) { "region id must be positive" }
        require(query.scopeId == null || query.scopeId != 0L) { "scope id must not be zero" }
        require(query.subSpaceId == null || query.subSpaceId > 0L) { "subspace id must be positive" }
        require(query.objectIds == null || query.objectIds.all(String::isNotBlank)) {
            "object ids must not contain blank values"
        }
    }

    private fun checkpointDirectory(checkpointId: UUID): Path =
        requireNotNull(root) { "Behavior stats checkpoint session is not active" }.resolve(checkpointId.toString())

    private fun readManifest(checkpointId: UUID): CheckpointManifest? {
        val path = checkpointDirectory(checkpointId).resolve(MANIFEST_FILE)
        if (!Files.exists(path)) return null
        try {
            val obj = JsonParser.parseString(Files.readString(path)).asJsonObject
            require(obj.get("formatVersion").asInt == FORMAT_VERSION)
            val query = readQuery(obj.getAsJsonObject("query"))
            val pages = obj.getAsJsonArray("pages")?.map { element ->
                val page = element.asJsonObject
                CheckpointPageDescriptor(
                    page.get("relativePath").asString,
                    page.get("entryCount").asInt,
                    page.get("byteLength").asLong,
                    page.get("checksum").asString
                )
            } ?: emptyList()
            return CheckpointManifest(
                obj.get("complete").asBoolean,
                query,
                obj.get("pageSize").asInt,
                obj.get("cutoffSequence").asLong,
                obj.get("manifestVersion").asLong,
                pages
            ).also(::validateManifest)
        } catch (error: RuntimeException) {
            throw IOException("Invalid behavior stats checkpoint manifest", error)
        }
    }

    private fun writeManifest(checkpointId: UUID, value: CheckpointManifest, phase: String) {
        validateManifest(value)
        val target = checkpointDirectory(checkpointId)
        Files.createDirectories(target)
        val obj = JsonObject()
        obj.addProperty("formatVersion", FORMAT_VERSION)
        obj.addProperty("complete", value.complete)
        obj.add("query", queryJson(value.query))
        obj.addProperty("pageSize", value.pageSize)
        obj.addProperty("cutoffSequence", value.cutoffSequence)
        obj.addProperty("manifestVersion", value.manifestVersion)
        obj.add("pages", JsonArray().also { array ->
            value.pages.forEach { page ->
                array.add(JsonObject().also {
                    it.addProperty("relativePath", page.relativePath)
                    it.addProperty("entryCount", page.entryCount)
                    it.addProperty("byteLength", page.byteLength)
                    it.addProperty("checksum", page.checksum)
                })
            }
        })
        failureInjector?.invoke(phase)
        RegionDatabase.atomicWrite(target.resolve(MANIFEST_FILE)) {
            it.write(obj.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun validateManifest(value: CheckpointManifest) {
        validateQuery(value.query)
        require(value.pageSize in 1..BehaviorStatsPageStreamService.MAX_PAGE_SIZE)
        require(value.cutoffSequence >= 0L)
        require(!value.complete || value.manifestVersion >= 0L)
        require(value.complete || value.pages.isEmpty())
        value.pages.forEachIndexed { index, page ->
            require(page.relativePath == "page-$index.json")
            require(page.entryCount in 1..value.pageSize && page.byteLength > 0L)
            require(page.checksum.matches(Regex("[0-9a-f]{64}")))
        }
    }

    private fun queryJson(query: WorldGeoBehaviorStatsPageQuery) = JsonObject().also { obj ->
        obj.addProperty("timelineId", query.periodKey.timelineId)
        obj.addProperty("periodKind", query.periodKey.kind.name)
        obj.addProperty("periodId", query.periodKey.periodId)
        obj.addProperty("regionId", query.regionId)
        query.scopeId?.let { obj.addProperty("scopeId", it) }
        query.subSpaceId?.let { obj.addProperty("subSpaceId", it) }
        query.behaviorType?.let { obj.addProperty("behaviorType", it.name) }
        query.objectIds?.let { values ->
            obj.add("objectIds", JsonArray().also { array -> values.sorted().forEach(array::add) })
        }
        query.playerUuids?.let { values ->
            obj.add("playerUuids", JsonArray().also { array -> values.sorted().forEach { array.add(it.toString()) } })
        }
    }

    private fun readQuery(obj: JsonObject): WorldGeoBehaviorStatsPageQuery =
        WorldGeoBehaviorStatsPageQuery(
            NaturalPeriodKey(
                obj.get("timelineId").asString,
                enumValueOf<NaturalPeriodKind>(obj.get("periodKind").asString),
                obj.get("periodId").asString
            ),
            obj.get("regionId").asInt,
            obj.optionalLong("scopeId"),
            obj.optionalLong("subSpaceId"),
            obj.get("behaviorType")?.let { enumValueOf<WorldGeoBehaviorType>(it.asString) },
            obj.getAsJsonArray("objectIds")?.mapTo(linkedSetOf()) { it.asString },
            obj.getAsJsonArray("playerUuids")?.mapTo(linkedSetOf()) { UUID.fromString(it.asString) }
        )

    private fun pageJson(entries: List<WorldGeoBehaviorStatsEntry>) = JsonArray().also { array ->
        entries.forEach { entry ->
            array.add(JsonObject().also { obj ->
                obj.addProperty("periodKind", entry.periodKind.name)
                obj.addProperty("periodId", entry.periodId)
                obj.addProperty("behaviorType", entry.behaviorType.name)
                obj.addProperty("regionId", entry.regionId)
                entry.scopeId?.let { obj.addProperty("scopeId", it) }
                entry.subSpaceId?.let { obj.addProperty("subSpaceId", it) }
                obj.addProperty("playerUuid", entry.playerUuid.toString())
                entry.objectId?.let { obj.addProperty("objectId", it) }
                entry.targetId?.let { obj.addProperty("targetId", it) }
                obj.addProperty("count", entry.count)
            })
        }
    }

    private fun readPageFile(path: Path, descriptor: CheckpointPageDescriptor): List<WorldGeoBehaviorStatsEntry> {
        if (Files.size(path) != descriptor.byteLength || sha256(path) != descriptor.checksum) {
            throw IOException("Invalid behavior stats checkpoint page")
        }
        try {
            val array = JsonParser.parseString(Files.readString(path)).asJsonArray
            require(array.size() == descriptor.entryCount)
            return array.map { element ->
                val obj = element.asJsonObject
                WorldGeoBehaviorStatsEntry(
                    enumValueOf(obj.get("periodKind").asString),
                    obj.get("periodId").asString,
                    enumValueOf(obj.get("behaviorType").asString),
                    obj.get("regionId").asInt,
                    obj.optionalLong("scopeId"),
                    obj.optionalLong("subSpaceId"),
                    UUID.fromString(obj.get("playerUuid").asString),
                    obj.optionalString("objectId"),
                    obj.optionalString("targetId"),
                    obj.get("count").asLong
                ).also(::validateCheckpointEntry)
            }
        } catch (error: RuntimeException) {
            throw IOException("Invalid behavior stats checkpoint page", error)
        }
    }

    private fun validateCheckpointEntry(entry: WorldGeoBehaviorStatsEntry) {
        require(entry.periodId.isNotBlank())
        require(entry.regionId > 0)
        require(entry.scopeId == null || entry.scopeId != 0L)
        require(entry.subSpaceId == null || entry.subSpaceId > 0L)
        require(entry.objectId == null || entry.objectId.isNotBlank())
        require(entry.targetId == null || entry.targetId.isNotBlank())
        require(entry.count > 0L)
    }

    private fun WorldGeoBehaviorStatsEntry.toKey(timelineId: String) = BehaviorStatsKey(
        periodKind,
        periodId,
        behaviorType,
        regionId,
        scopeId,
        subSpaceId,
        playerUuid,
        objectId,
        targetId,
        timelineId
    )

    private fun JsonObject.optionalLong(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun deleteDirectory(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class CheckpointManifest(
        val complete: Boolean,
        val query: WorldGeoBehaviorStatsPageQuery,
        val pageSize: Int,
        val cutoffSequence: Long,
        val manifestVersion: Long,
        val pages: List<CheckpointPageDescriptor>
    )

    private data class CheckpointPageDescriptor(
        val relativePath: String,
        val entryCount: Int,
        val byteLength: Long,
        val checksum: String
    )
}
