package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.application.region.allocateRegionId
import com.imyvm.iwg.application.region.currentRegionCreationHours
import com.imyvm.iwg.application.region.parseMarkFromRegionId
import com.imyvm.iwg.application.region.parseRegionCreationHours
import com.imyvm.iwg.application.region.RegionIdCapacityExceededException
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.AssignedScopeId
import com.imyvm.iwg.domain.component.ScopeId
import com.imyvm.iwg.domain.component.ScopeIdCapacityExceededException
import com.imyvm.iwg.domain.component.currentScopeCreationHours
import com.imyvm.iwg.domain.component.generateNewScopeIdRaw
import com.imyvm.iwg.domain.component.parseScopeCreationHoursOrNull
import com.imyvm.iwg.domain.component.parseScopeMark
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal data class ReservedRegionIdentity(
    val regionId: Int,
    val mainScopeId: AssignedScopeId
)

private data class SpaceIdentityAllocationState(
    val formatVersion: Int,
    val regionHours: Int,
    val regionNextDiscriminators: List<Int>,
    val scopeHours: Long,
    val scopeNextDiscriminators: List<Int>,
    val subSpaceHighWater: Long
)

object SpaceIdentityAllocationStore {
    private const val FORMAT_VERSION = 1
    private const val FILE_NAME = "iwg_space_identity_allocations.json"
    private const val BEHAVIOR_STATS_FILE_NAME = "iwg_behavior_stats.json"
    private const val MARK_COUNT = 10
    private const val REGION_DISCRIMINATOR_COUNT = 128
    private const val SCOPE_DISCRIMINATOR_COUNT = 64
    private const val MAX_REGION_HOURS = 0x1FFFFF
    private const val MAX_SCOPE_HOURS = 0xFFFFF
    private var sessionWorldRoot: Path? = null
    private var state: SpaceIdentityAllocationState? = null

    internal fun bindSession(
        worldRoot: Path,
        regionHours: Int = currentRegionCreationHours(),
        scopeHours: Long = currentScopeCreationHours()
    ) {
        check(sessionWorldRoot == null) { "Space identity allocation store session is already active" }
        val root = worldRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val path = root.resolve(FILE_NAME)
        val evidence = stateFromEvidence(root, RegionDatabase.getRegionList(), regionHours, scopeHours)
        val loaded = if (Files.exists(path)) read(path) else null
        val merged = loaded?.let { merge(it, evidence) } ?: evidence
        if (loaded != merged) write(path, merged)
        sessionWorldRoot = root
        state = merged
    }

    internal fun unbindSession() {
        state = null
        sessionWorldRoot = null
    }

    internal fun reserveRegion(mark: Int): ReservedRegionIdentity =
        reserveRegion(mark, currentRegionCreationHours(), currentScopeCreationHours())

    internal fun reserveRegion(mark: Int, regionHours: Int, scopeHours: Long): ReservedRegionIdentity =
        reserve { current ->
            val (regionId, withRegion) = nextRegion(current, mark, regionHours)
            val (scopeId, next) = nextScope(withRegion, regionId, mark, scopeHours)
            ReservedRegionIdentity(regionId, scopeId) to next
        }

    internal fun reserveScope(region: Region): AssignedScopeId {
        RegionDatabase.requireCanonicalRegion(region)
        return reserveScope(region.numberID, parseMarkFromRegionId(region.numberID), currentScopeCreationHours())
    }

    internal fun reserveScope(foundedInRegionNumberId: Int, mark: Int, scopeHours: Long): AssignedScopeId =
        reserve { current -> nextScope(current, foundedInRegionNumberId, mark, scopeHours) }

    internal fun reserveSubSpaceId(): Long = reserve { current ->
        val nextId = Math.addExact(current.subSpaceHighWater, 1L)
        nextId to current.copy(subSpaceHighWater = nextId)
    }

    private fun <T> reserve(operation: (SpaceIdentityAllocationState) -> Pair<T, SpaceIdentityAllocationState>): T {
        val current = state ?: error("Space identity allocation store session is not active")
        val (result, next) = operation(current)
        write(requireSessionWorldRoot().resolve(FILE_NAME), next)
        state = next
        return result
    }

    private fun nextRegion(
        current: SpaceIdentityAllocationState,
        mark: Int,
        currentHours: Int
    ): Pair<Int, SpaceIdentityAllocationState> {
        require(mark in 0 until MARK_COUNT) { "region mark must be between 0 and 9" }
        require(currentHours in 0..MAX_REGION_HOURS) { "region creation time is out of range" }
        var hours = maxOf(current.regionHours, currentHours)
        var counters = if (hours == current.regionHours) current.regionNextDiscriminators else zeroCounters()
        var discriminator = counters[mark]
        if (discriminator >= REGION_DISCRIMINATOR_COUNT) {
            if (hours >= MAX_REGION_HOURS) throw RegionIdCapacityExceededException()
            hours++
            counters = zeroCounters()
            discriminator = 0
        }
        val regionId = allocateRegionId(mark, hours, emptySet(), discriminator)
        val nextCounters = counters.toMutableList()
        nextCounters[mark] = discriminator + 1
        return regionId to current.copy(regionHours = hours, regionNextDiscriminators = nextCounters)
    }

    private fun nextScope(
        current: SpaceIdentityAllocationState,
        foundedInRegionNumberId: Int,
        mark: Int,
        currentHours: Long
    ): Pair<AssignedScopeId, SpaceIdentityAllocationState> {
        require(foundedInRegionNumberId > 0) { "founding region id must be positive" }
        require(mark in 0 until MARK_COUNT) { "scope mark must be between 0 and 9" }
        require(currentHours in 0..MAX_SCOPE_HOURS) { "scope creation time is out of range" }
        var hours = maxOf(current.scopeHours, currentHours)
        var counters = if (hours == current.scopeHours) current.scopeNextDiscriminators else zeroCounters()
        var discriminator = counters[mark]
        if (discriminator >= SCOPE_DISCRIMINATOR_COUNT) {
            if (hours >= MAX_SCOPE_HOURS) throw ScopeIdCapacityExceededException()
            hours++
            counters = zeroCounters()
            discriminator = 0
        }
        val scopeId = AssignedScopeId.require(ScopeId(
            generateNewScopeIdRaw(foundedInRegionNumberId, mark, discriminator, hours)
        ))
        val nextCounters = counters.toMutableList()
        nextCounters[mark] = discriminator + 1
        return scopeId to current.copy(scopeHours = hours, scopeNextDiscriminators = nextCounters)
    }

    private fun stateFromEvidence(
        root: Path,
        regions: List<Region>,
        currentRegionHours: Int,
        currentScopeHours: Long
    ): SpaceIdentityAllocationState {
        require(currentRegionHours in 0..MAX_REGION_HOURS) { "region creation time is out of range" }
        require(currentScopeHours in 0..MAX_SCOPE_HOURS) { "scope creation time is out of range" }
        val regionIds = linkedSetOf<Int>()
        val scopeIds = linkedSetOf<Long>()
        val subSpaceIds = linkedSetOf<Long>()
        regions.forEach { region ->
            regionIds.add(region.numberID)
            region.scopes.forEach { scopeIds.add(it.requireAssignedScopeId().raw) }
            region.subSpaces.forEach { subSpaceIds.add(it.subSpaceId) }
            region.ownershipHistorySnapshot().forEach { (scopeId, entries) ->
                scopeIds.add(scopeId.raw)
                entries.forEach {
                    regionIds.add(it.fromRegionNumberId)
                    regionIds.add(it.toRegionNumberId)
                }
            }
        }
        val behaviorStatsPath = root.resolve(BEHAVIOR_STATS_FILE_NAME)
        if (Files.exists(behaviorStatsPath)) {
            BehaviorStatsStore.readStats(behaviorStatsPath).keys.forEach {
                regionIds.add(it.regionId)
                it.scopeId?.let(scopeIds::add)
                it.subSpaceId?.let(subSpaceIds::add)
            }
        }
        val regionHours = maxOf(currentRegionHours, regionIds.maxOfOrNull(::parseRegionCreationHours) ?: 0)
        val regionCounters = zeroCounters().toMutableList()
        regionIds.forEach { regionId ->
            if (parseRegionCreationHours(regionId) != regionHours) return@forEach
            val mark = parseMarkFromRegionId(regionId)
            if (mark in 0 until MARK_COUNT) {
                regionCounters[mark] = maxOf(regionCounters[mark], (regionId and 0x7F) + 1)
            }
        }
        val scopeHours = maxOf(
            currentScopeHours,
            scopeIds.mapNotNull(::parseScopeCreationHoursOrNull).maxOrNull() ?: 0L
        )
        val scopeCounters = zeroCounters().toMutableList()
        scopeIds.forEach { raw ->
            if (parseScopeCreationHoursOrNull(raw) != scopeHours) return@forEach
            val mark = parseScopeMark(raw)
            if (mark in 0 until MARK_COUNT) {
                scopeCounters[mark] = maxOf(scopeCounters[mark], ((raw ushr 32) and 0x3F).toInt() + 1)
            }
        }
        return SpaceIdentityAllocationState(
            FORMAT_VERSION,
            regionHours,
            regionCounters,
            scopeHours,
            scopeCounters,
            subSpaceIds.maxOrNull() ?: 0L
        )
    }

    private fun merge(
        persisted: SpaceIdentityAllocationState,
        evidence: SpaceIdentityAllocationState
    ): SpaceIdentityAllocationState {
        val regionHours = maxOf(persisted.regionHours, evidence.regionHours)
        val regionCounters = when {
            persisted.regionHours > evidence.regionHours -> persisted.regionNextDiscriminators
            persisted.regionHours < evidence.regionHours -> evidence.regionNextDiscriminators
            else -> persisted.regionNextDiscriminators.zip(evidence.regionNextDiscriminators, ::maxOf)
        }
        val scopeHours = maxOf(persisted.scopeHours, evidence.scopeHours)
        val scopeCounters = when {
            persisted.scopeHours > evidence.scopeHours -> persisted.scopeNextDiscriminators
            persisted.scopeHours < evidence.scopeHours -> evidence.scopeNextDiscriminators
            else -> persisted.scopeNextDiscriminators.zip(evidence.scopeNextDiscriminators, ::maxOf)
        }
        return SpaceIdentityAllocationState(
            FORMAT_VERSION,
            regionHours,
            regionCounters,
            scopeHours,
            scopeCounters,
            maxOf(persisted.subSpaceHighWater, evidence.subSpaceHighWater)
        )
    }

    private fun read(path: Path): SpaceIdentityAllocationState {
        try {
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            val loaded = SpaceIdentityAllocationState(
                root.get("formatVersion").asInt,
                root.get("regionHours").asInt,
                readCounters(root, "regionNextDiscriminators"),
                root.get("scopeHours").asLong,
                readCounters(root, "scopeNextDiscriminators"),
                root.get("subSpaceHighWater").asLong
            )
            validate(loaded)
            return loaded
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid space identity allocation store", error)
        } catch (error: IllegalStateException) {
            throw IOException("Invalid space identity allocation store", error)
        } catch (error: NullPointerException) {
            throw IOException("Invalid space identity allocation store", error)
        }
    }

    private fun write(path: Path, next: SpaceIdentityAllocationState) {
        validate(next)
        Files.createDirectories(path.parent)
        val root = JsonObject()
        root.addProperty("formatVersion", next.formatVersion)
        root.addProperty("regionHours", next.regionHours)
        root.add("regionNextDiscriminators", countersJson(next.regionNextDiscriminators))
        root.addProperty("scopeHours", next.scopeHours)
        root.add("scopeNextDiscriminators", countersJson(next.scopeNextDiscriminators))
        root.addProperty("subSpaceHighWater", next.subSpaceHighWater)
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

    private fun validate(value: SpaceIdentityAllocationState) {
        require(value.formatVersion == FORMAT_VERSION) { "unsupported space identity allocation format" }
        require(value.regionHours in 0..MAX_REGION_HOURS) { "region allocation hours are out of range" }
        require(value.scopeHours in 0..MAX_SCOPE_HOURS) { "scope allocation hours are out of range" }
        require(value.regionNextDiscriminators.size == MARK_COUNT) { "invalid region allocation window" }
        require(value.scopeNextDiscriminators.size == MARK_COUNT) { "invalid scope allocation window" }
        require(value.regionNextDiscriminators.all { it in 0..REGION_DISCRIMINATOR_COUNT }) {
            "invalid region discriminator"
        }
        require(value.scopeNextDiscriminators.all { it in 0..SCOPE_DISCRIMINATOR_COUNT }) {
            "invalid scope discriminator"
        }
        require(value.subSpaceHighWater >= 0L) { "subspace high water must not be negative" }
    }

    private fun readCounters(root: JsonObject, name: String): List<Int> =
        root.getAsJsonArray(name).map { it.asInt }

    private fun countersJson(values: List<Int>): JsonArray = JsonArray().also { array ->
        values.forEach(array::add)
    }

    private fun zeroCounters(): List<Int> = List(MARK_COUNT) { 0 }

    private fun requireSessionWorldRoot(): Path =
        sessionWorldRoot ?: error("Space identity allocation store session is not active")
}
