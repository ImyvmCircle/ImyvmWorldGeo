package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.collections.ArrayList

class RegionNotFoundException(message: String) : RuntimeException(message)

internal data class RegionPlayerStatsLedger(
    val entryCounts: MutableMap<UUID, Long> = mutableMapOf(),
    val stayMillis: MutableMap<UUID, Long> = mutableMapOf(),
    val deathCounts: MutableMap<UUID, Long> = mutableMapOf(),
    val blockPlaceCounts: MutableMap<UUID, Long> = mutableMapOf(),
    val blockBreakCounts: MutableMap<UUID, Long> = mutableMapOf()
) {
    fun detachedCopy(): RegionPlayerStatsLedger = RegionPlayerStatsLedger(
        entryCounts.toMutableMap(),
        stayMillis.toMutableMap(),
        deathCounts.toMutableMap(),
        blockPlaceCounts.toMutableMap(),
        blockBreakCounts.toMutableMap()
    )

    fun isEmpty(): Boolean =
        entryCounts.isEmpty() &&
                stayMillis.isEmpty() &&
                deathCounts.isEmpty() &&
                blockPlaceCounts.isEmpty() &&
                blockBreakCounts.isEmpty()

    fun aggregate(): RegionPlayerStats {
        val trackedPlayers = linkedSetOf<UUID>()
        trackedPlayers.addAll(entryCounts.keys)
        trackedPlayers.addAll(stayMillis.keys)
        trackedPlayers.addAll(deathCounts.keys)
        trackedPlayers.addAll(blockPlaceCounts.keys)
        trackedPlayers.addAll(blockBreakCounts.keys)
        return RegionPlayerStats(
            trackedPlayerCount = trackedPlayers.size,
            entryCount = checkedSum(entryCounts.values),
            stayMillis = checkedSum(stayMillis.values),
            deathCount = checkedSum(deathCounts.values),
            blockPlaceCount = checkedSum(blockPlaceCounts.values),
            blockBreakCount = checkedSum(blockBreakCounts.values)
        )
    }

    fun mergeFrom(other: RegionPlayerStatsLedger) {
        val mergedEntryCounts = mergedMap(entryCounts, other.entryCounts)
        val mergedStayMillis = mergedMap(stayMillis, other.stayMillis)
        val mergedDeathCounts = mergedMap(deathCounts, other.deathCounts)
        val mergedBlockPlaceCounts = mergedMap(blockPlaceCounts, other.blockPlaceCounts)
        val mergedBlockBreakCounts = mergedMap(blockBreakCounts, other.blockBreakCounts)
        entryCounts.replaceWith(mergedEntryCounts)
        stayMillis.replaceWith(mergedStayMillis)
        deathCounts.replaceWith(mergedDeathCounts)
        blockPlaceCounts.replaceWith(mergedBlockPlaceCounts)
        blockBreakCounts.replaceWith(mergedBlockBreakCounts)
    }

    fun mergedWith(other: RegionPlayerStatsLedger): RegionPlayerStatsLedger = detachedCopy().apply {
        mergeFrom(other)
    }

    private fun mergedMap(target: Map<UUID, Long>, source: Map<UUID, Long>): MutableMap<UUID, Long> {
        require(target.values.all { it > 0L }) { "player stats values must be positive" }
        val result = target.toMutableMap()
        source.forEach { (uuid, value) ->
            require(value > 0L) { "player stats values must be positive" }
            result[uuid] = Math.addExact(result[uuid] ?: 0L, value)
        }
        return result
    }

    private fun checkedSum(values: Collection<Long>): Long {
        var result = 0L
        values.forEach {
            require(it > 0L) { "player stats values must be positive" }
            result = Math.addExact(result, it)
        }
        return result
    }

    private fun MutableMap<UUID, Long>.replaceWith(replacement: Map<UUID, Long>) {
        clear()
        putAll(replacement)
    }
}

internal data class DynmapVisibility(
    val showOnDynmap: Boolean,
    val scopes: Map<String, Boolean>
)

object RegionDatabase {

    private var regions: MutableList<Region> = mutableListOf()
    private var sessionWorldRoot: Path? = null
    private const val DATABASE_FILENAME = "iwg_regions.db"
    private const val DYNMAP_CONFIG_FILENAME = "iwg_dynmap.json"
    private const val PLAYER_STATS_FILENAME = "iwg_player_stats.json"
    private const val DB_V1_SENTINEL: Int = -1
    private const val DB_V2_SENTINEL: Int = -2
    private const val MAX_COLLECTION_SIZE = 100_000
    private const val DELETED_RULE_RPG_NATURAL_REGEN = "RPG_NATURAL_REGEN"
    private var dimensionIndex: MutableMap<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, MutableList<Pair<Region, GeoScope>>> = linkedMapOf()
    private val gson = Gson()
    private val regionPlayerStats: MutableMap<Int, RegionPlayerStatsLedger> = mutableMapOf()
    internal var onSave: (() -> Unit)? = null

    @Throws(IOException::class)
    fun save() {
        persistFiles()
        runCatching { onSave?.invoke() }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to update persisted region projections: ${it.message}", it) }
    }

    internal fun saveForShutdown() {
        persistFiles()
    }

    private fun persistFiles() {
        withFileRollback(listOf(getDatabasePath(), getDynmapConfigPath(), getPlayerStatsPath())) {
            writeRegions(getDatabasePath(), regions)
            saveDynmapVisibility()
            savePlayerStats()
        }
    }

    internal fun withFileRollback(paths: List<Path>, save: () -> Unit) {
        val previousContents = paths.associateWith { path ->
            if (Files.exists(path)) Files.readAllBytes(path) else null
        }
        try {
            save()
        } catch (error: Exception) {
            previousContents.forEach { (path, contents) ->
                try {
                    if (contents == null) Files.deleteIfExists(path)
                    else atomicWrite(path) { it.write(contents) }
                } catch (restoreError: Exception) {
                    error.addSuppressed(restoreError)
                }
            }
            throw error
        }
    }

    internal fun writeRegions(path: Path, regions: List<Region>) {
        validateDatabaseIdentities(regions)
        atomicWrite(path) { output ->
            val stream = DataOutputStream(output)
            stream.writeInt(DB_V2_SENTINEL)
            stream.writeInt(checkedCount(regions.size, "regions"))
            for (region in regions) {
                stream.writeUTF(region.name)
                stream.writeInt(region.numberID)
                saveGeoScopes(stream, region.scopes)
                saveSettings(stream, region.settings)
                saveOwnershipHistory(stream, region.ownershipHistoryByScope)
                saveSubSpaces(stream, region.subSpaces)
            }
        }
    }

    internal fun hasActiveSession(): Boolean = sessionWorldRoot != null

    internal fun bindSession(worldRoot: Path) {
        check(!hasActiveSession()) { "Region database session is already active" }
        val normalizedRoot = worldRoot.toAbsolutePath().normalize()
        try {
            val databasePath = normalizedRoot.resolve(DATABASE_FILENAME)
            val dynmapPath = normalizedRoot.resolve(DYNMAP_CONFIG_FILENAME)
            val playerStatsPath = normalizedRoot.resolve(PLAYER_STATS_FILENAME)
            val loadedRegions = if (Files.exists(databasePath)) {
                readRegions(databasePath)
            } else {
                rejectOrphanCompanionFiles(listOf(dynmapPath, playerStatsPath))
                mutableListOf()
            }
            applyDynmapVisibility(loadedRegions, dynmapPath)
            val loadedStats = loadPlayerStats(loadedRegions, playerStatsPath)

            regions = loadedRegions
            regionPlayerStats.clear()
            regionPlayerStats.putAll(loadedStats)
            sessionWorldRoot = normalizedRoot
            rebuildDimensionIndex()
        } catch (error: Throwable) {
            unbindSession()
            throw error
        }
    }

    internal fun unbindSession() {
        regions.clear()
        regionPlayerStats.clear()
        sessionWorldRoot = null
        dimensionIndex.clear()
    }

    @Deprecated("RegionDatabase lifecycle is managed by the Minecraft server")
    fun load(): Unit {
        error("RegionDatabase is loaded automatically for each server session")
    }

    internal fun rejectOrphanCompanionFiles(companionFiles: List<Path>) {
        val orphan = companionFiles.firstOrNull(Files::exists) ?: return
        throw IOException("Region database is missing while companion file exists: ${orphan.fileName}")
    }

    internal fun readRegions(path: Path): MutableList<Region> {
        try {
            return DataInputStream(Files.newInputStream(path)).use { stream ->
                val first = stream.readInt()
                val isV2 = first == DB_V2_SENTINEL
                val isV1 = first == DB_V1_SENTINEL
                val hasScopeIds = isV1 || isV2
                val regionCount = checkedCount(if (hasScopeIds) stream.readInt() else first, "regions")
                val loadedRegions = ArrayList<Region>(regionCount)

                repeat(regionCount) {
                    val name = stream.readUTF()
                    val numberID = stream.readInt()
                    val geometryScopes = loadGeoScopes(stream, hasScopeIds, numberID)
                    val settings = loadSettings(stream)

                    val region = Region(name, numberID, geometryScopes, settings)
                    if (hasScopeIds) {
                        region.ownershipHistoryByScope = loadOwnershipHistory(stream)
                    }
                    if (isV2) {
                        region.replaceSubSpaces(loadSubSpaces(stream))
                    }
                    loadedRegions.add(region)
                }
                validateDatabaseIdentities(loadedRegions)
                loadedRegions
            }
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid region database", e)
        }
    }

    fun addRegion(region: Region) {
        require(regions.none { it.numberID == region.numberID }) { "duplicate region id" }
        require(regions.none { it.name.equals(region.name, ignoreCase = true) }) { "duplicate region name" }
        val existingScopeIds = regions.flatMapTo(hashSetOf()) { existing ->
            existing.scopes.map { it.requireAssignedScopeId() }
        }
        val existingSubSpaceIds = regions.flatMapTo(hashSetOf()) { existing ->
            existing.subSpaces.map { it.subSpaceId }
        }
        require(region.scopes.none { it.requireAssignedScopeId() in existingScopeIds }) { "duplicate scope id" }
        require(region.subSpaces.none { it.subSpaceId in existingSubSpaceIds }) { "duplicate subspace id" }
        regions.add(region)
        dimensionIndex.clear()
    }

    fun removeRegion(regionToDelete: Region) {
        regions.removeIf { it.name == regionToDelete.name && it.numberID == regionToDelete.numberID }
        regionPlayerStats.remove(regionToDelete.numberID)
    }

    internal fun requireCanonicalRegions(
        sourceRegion: Region,
        targetRegion: Region,
        currentRegions: List<Region> = regions
    ) {
        requireCanonicalRegion(sourceRegion, currentRegions)
        requireCanonicalRegion(targetRegion, currentRegions)
    }

    internal fun requireCanonicalRegion(region: Region, currentRegions: List<Region> = regions) {
        require(currentRegions.any { it === region }) { "region must be a canonical database object" }
    }

    internal fun requireCanonicalScope(
        region: Region,
        scope: GeoScope,
        currentRegions: List<Region> = regions
    ) {
        requireCanonicalRegion(region, currentRegions)
        require(region.containsScope(scope)) { "scope must be a canonical child of region" }
    }

    internal fun requireCanonicalSubSpace(
        region: Region,
        scope: GeoScope,
        subSpace: SubSpace,
        currentRegions: List<Region> = regions
    ) {
        requireCanonicalScope(region, scope, currentRegions)
        require(region.containsSubSpace(subSpace)) { "subspace must be a canonical child of region" }
        require(subSpace.parentScopeId == scope.requireAssignedScopeId()) { "subspace must belong to scope" }
    }

    internal fun removeRegionReversibly(region: Region): () -> Unit {
        val index = regions.indexOf(region)
        require(index >= 0) { "region does not belong to database" }
        val stats = regionPlayerStats[region.numberID]?.detachedCopy()
        regions.removeAt(index)
        regionPlayerStats.remove(region.numberID)
        return {
            regions.add(index.coerceIn(0, regions.size), region)
            if (stats != null) regionPlayerStats[region.numberID] = stats
        }
    }

    internal fun mergeAndRemoveRegionReversibly(sourceRegion: Region, targetRegion: Region): () -> Unit {
        requireCanonicalRegions(sourceRegion, targetRegion)
        require(sourceRegion !== targetRegion) { "source and target regions must differ" }
        val sourceStats = regionPlayerStats[sourceRegion.numberID]?.detachedCopy()
        val targetStats = regionPlayerStats[targetRegion.numberID]?.detachedCopy()
        val mergedStats = sourceStats?.let { (targetStats ?: RegionPlayerStatsLedger()).mergedWith(it) }
        val restoreRegion = removeRegionReversibly(sourceRegion)
        if (mergedStats != null) regionPlayerStats[targetRegion.numberID] = mergedStats
        return {
            restoreRegion()
            if (sourceStats != null) regionPlayerStats[sourceRegion.numberID] = sourceStats
            else regionPlayerStats.remove(sourceRegion.numberID)
            if (targetStats != null) regionPlayerStats[targetRegion.numberID] = targetStats
            else regionPlayerStats.remove(targetRegion.numberID)
        }
    }

    fun renameRegion(region: Region, newName: String) {
        val isDuplicate = regions.any { otherRegion ->
            otherRegion.name.equals(newName, ignoreCase = true) && otherRegion.numberID != region.numberID
        }
        if (isDuplicate) {
            throw IllegalArgumentException("A region with the name '$newName' already exists.")
        }
        region.renameTo(newName)
    }

    fun getRegionList(): List<Region> {
        return regions.toList()
    }

    fun getRegionPlayerStats(region: Region): RegionPlayerStats =
        regionPlayerStats[region.numberID]?.aggregate()
            ?: RegionPlayerStats(0, 0, 0, 0, 0, 0)

    fun incrementRegionEntryStat(region: Region, playerUUID: UUID) {
        incrementPlayerStat(region.numberID, playerUUID) { entryCounts }
    }

    fun addRegionStayDuration(region: Region, playerUUID: UUID, millis: Long) {
        if (millis <= 0L) return
        incrementPlayerStat(region.numberID, playerUUID, millis) { stayMillis }
    }

    fun incrementRegionDeathStat(region: Region, playerUUID: UUID) {
        incrementPlayerStat(region.numberID, playerUUID) { deathCounts }
    }

    fun incrementRegionBlockPlaceStat(region: Region, playerUUID: UUID) {
        incrementPlayerStat(region.numberID, playerUUID) { blockPlaceCounts }
    }

    fun incrementRegionBlockBreakStat(region: Region, playerUUID: UUID) {
        incrementPlayerStat(region.numberID, playerUUID) { blockBreakCounts }
    }

    fun mergeRegionPlayerStats(sourceRegion: Region, targetRegion: Region) {
        require(sourceRegion.numberID != targetRegion.numberID) { "source and target regions must differ" }
        val source = regionPlayerStats[sourceRegion.numberID] ?: return
        val merged = (regionPlayerStats[targetRegion.numberID] ?: RegionPlayerStatsLedger()).mergedWith(source)
        regionPlayerStats[targetRegion.numberID] = merged
        regionPlayerStats.remove(sourceRegion.numberID)
    }

    internal fun requireMergeableRegionPlayerStats(sourceRegion: Region, targetRegion: Region) {
        require(sourceRegion !== targetRegion) { "source and target regions must differ" }
        val source = regionPlayerStats[sourceRegion.numberID] ?: return
        (regionPlayerStats[targetRegion.numberID] ?: RegionPlayerStatsLedger()).mergedWith(source)
    }

    fun savePlayerStatsSnapshot() {
        if (!hasActiveSession()) return
        runCatching { savePlayerStats() }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to save player stats: ${it.message}", it) }
    }

    fun getRegionByName(name: String): Region {
        return regions.find { it.name.equals(name, ignoreCase = true) }
            ?: throw RegionNotFoundException("Region with name '$name' not found.")
    }

    fun getRegionByNumberId(id: Int): Region {
        return regions.find { it.numberID == id }
            ?: throw RegionNotFoundException("Region with ID '$id' not found.")
    }

    fun getRegionAndScope(regionId: Int, scopeName: String): Pair<Region?, GeoScope?> {
        val region = try {
            getRegionByNumberId(regionId)
        } catch (e: RegionNotFoundException) {
            return Pair(null, null)
        }
        val scope = region.scopes.find { it.scopeName.equals(scopeName, ignoreCase = true) }
        return Pair(region, scope)
    }

    fun getRegionAndScope(region: Region, scopeName: String): Pair<Region, GeoScope?> {
        val scope = region.scopes.find { it.scopeName.equals(scopeName, ignoreCase = true) }
        return Pair(region, scope)
    }

    fun getRegionAndScopeAt(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? {
        val candidates = dimensionIndex[world.dimension()] ?: return null
        for ((region, scope) in candidates) {
            val geoShape = scope.geoShape
            if (geoShape != null && geoShape.containsPoint(x, z)) {
                return Pair(region, scope)
            }
        }
        return null
    }

    fun getRegionScopeSubSpaceAt(world: Level, x: Int, z: Int): Triple<Region, GeoScope, SubSpace?>? {
        val (region, scope) = getRegionAndScopeAt(world, x, z) ?: return null
        val scopeId = scope.requireAssignedScopeId()
        val subSpace = region.subSpaces.firstOrNull {
            it.parentScopeId == scopeId && it.worldId == scope.worldId && it.geoShape.containsPoint(x, z)
        }
        return Triple(region, scope, subSpace)
    }

    fun getSubSpaceById(subSpaceId: Long): Triple<Region, GeoScope, SubSpace>? {
        for (region in regions) {
            val subSpace = region.subSpaces.firstOrNull { it.subSpaceId == subSpaceId } ?: continue
            val scope = region.scopes.firstOrNull { it.requireAssignedScopeId() == subSpace.parentScopeId } ?: continue
            return Triple(region, scope, subSpace)
        }
        return null
    }

    fun getSubSpaceByName(region: Region, name: String): Pair<GeoScope, SubSpace>? {
        requireCanonicalRegion(region)
        val subSpace = region.subSpaces.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        val scope = region.scopes.firstOrNull { it.requireAssignedScopeId() == subSpace.parentScopeId } ?: return null
        return scope to subSpace
    }

    fun nextSubSpaceId(): Long {
        var max = 0L
        regions.forEach { region ->
            region.subSpaces.forEach { subSpace -> if (subSpace.subSpaceId > max) max = subSpace.subSpaceId }
        }
        return Math.addExact(max, 1L)
    }

    fun getScopeById(scopeId: ScopeId): Pair<Region, GeoScope>? =
        AssignedScopeId.from(scopeId)?.let(::getScopeByAssignedId)

    fun getScopeByAssignedId(scopeId: AssignedScopeId): Pair<Region, GeoScope>? {
        for (region in regions) {
            val match = region.scopes.firstOrNull { it.requireAssignedScopeId() == scopeId }
            if (match != null) return region to match
        }
        return null
    }

    fun nextScopeIdForNewScope(region: Region): AssignedScopeId {
        return nextScopeIdForNewScope(
            region,
            regions,
            kotlin.random.Random.nextInt(64),
            currentScopeCreationHours()
        )
    }

    internal fun nextScopeIdForNewScope(
        region: Region,
        allRegions: List<Region>,
        firstDiscriminator: Int,
        creationHours: Long
    ): AssignedScopeId {
        val regionMark = com.imyvm.iwg.application.region.parseMarkFromRegionId(region.numberID)
        val existingIds = allRegions.asSequence()
            .flatMap { it.scopes.asSequence() }
            .mapTo(hashSetOf()) { it.requireAssignedScopeId().raw }
        region.scopes.mapTo(existingIds) { it.requireAssignedScopeId().raw }
        repeat(64) { offset ->
            val candidate = AssignedScopeId.require(ScopeId(
                generateNewScopeIdRaw(region.numberID, regionMark, (firstDiscriminator + offset) % 64, creationHours)
            ))
            if (candidate.raw !in existingIds) return candidate
        }
        throw ScopeIdCapacityExceededException()
    }

    fun recordScopeOwnership(
        scopeId: ScopeId,
        fromRegion: Region,
        toRegion: Region,
        changedAtMillis: Long
    ) = recordAssignedScopeOwnership(AssignedScopeId.require(scopeId), fromRegion, toRegion, changedAtMillis)

    fun recordAssignedScopeOwnership(
        scopeId: AssignedScopeId,
        fromRegion: Region,
        toRegion: Region,
        changedAtMillis: Long
    ) {
        val entry = ScopeOwnershipEntry(scopeId.raw, fromRegion.numberID, toRegion.numberID, changedAtMillis)
        toRegion.recordScopeOwnershipFromOwner(entry)
    }

    fun getScopeOwnershipHistory(scopeId: ScopeId): List<ScopeOwnershipEntry> {
        val assignedScopeId = AssignedScopeId.from(scopeId) ?: return emptyList()
        return getAssignedScopeOwnershipHistory(assignedScopeId)
    }

    fun getAssignedScopeOwnershipHistory(scopeId: AssignedScopeId): List<ScopeOwnershipEntry> {
        val result = mutableListOf<ScopeOwnershipEntry>()
        for (region in regions) {
            result.addAll(region.ownershipHistory(scopeId))
        }
        return result
    }

    private fun saveGeoScopes(stream: DataOutputStream, scopes: List<GeoScope>) {
        stream.writeInt(checkedCount(scopes.size, "scopes"))
        for (scope in scopes) {
            stream.writeUTF(scope.scopeName)
            stream.writeUTF(scope.worldId.toString())
            saveTeleportPoint(stream, scope.teleportPoint)
            stream.writeBoolean(scope.isTeleportPointPublic)
            saveGeoShape(stream, scope.geoShape)
            saveSettings(stream, scope.settings)
            stream.writeLong(scope.requireAssignedScopeId().raw)
        }
    }

    private fun loadGeoScopes(stream: DataInputStream, isV1: Boolean, regionNumberId: Int): MutableList<GeoScope> {
        val count = checkedCount(stream.readInt(), "scopes")
        val list = ArrayList<GeoScope>(count)

        repeat(count) { indexInRegion ->
            val scopeName = stream.readUTF()
            val worldId = Identifier.parse(stream.readUTF())
            val teleportPoint = loadTeleportPoint(stream)
            val isTeleportPointPublic = stream.readBoolean()
            val geoShape = loadGeoShape(stream)
            val scopeSettings = loadSettings(stream)

            val scopeId = if (isV1) {
                val raw = stream.readLong()
                AssignedScopeId.fromRaw(raw)?.toLegacyScopeId()
                    ?: throw IOException("Invalid or unassigned scope id: $raw")
            } else {
                ScopeId(generateCompatScopeIdRaw(regionNumberId, indexInRegion))
            }

            val scope = GeoScope(scopeName, worldId, teleportPoint, isTeleportPointPublic, geoShape, scopeSettings, scopeId = scopeId)
            list.add(scope)
        }

        return list
    }

    private fun saveSubSpaces(stream: DataOutputStream, subSpaces: List<SubSpace>) {
        stream.writeInt(checkedCount(subSpaces.size, "subspaces"))
        for (subSpace in subSpaces) {
            stream.writeLong(subSpace.subSpaceId)
            stream.writeUTF(subSpace.name)
            stream.writeLong(subSpace.parentScopeId.raw)
            stream.writeUTF(subSpace.worldId.toString())
            saveGeoShape(stream, subSpace.geoShape)
            saveNullableString(stream, subSpace.entryMessage)
            saveSettings(stream, subSpace.settings)
            stream.writeInt(checkedCount(subSpace.stringTags.size, "subspace string tags"))
            subSpace.stringTags.forEach(stream::writeUTF)
            stream.writeInt(checkedCount(subSpace.keyedTags.size, "subspace keyed tags"))
            subSpace.keyedTags.forEach { (key, value) ->
                stream.writeUTF(key)
                stream.writeUTF(value)
            }
        }
    }

    private fun loadSubSpaces(stream: DataInputStream): MutableList<SubSpace> {
        val count = checkedCount(stream.readInt(), "subspaces")
        val list = ArrayList<SubSpace>(count)
        repeat(count) {
            val id = stream.readLong()
            val name = stream.readUTF()
            val parentScopeId = AssignedScopeId.fromRaw(stream.readLong())
                ?: throw IOException("Invalid subspace parent scope id")
            val worldId = Identifier.parse(stream.readUTF())
            val shape = loadGeoShape(stream) ?: throw IOException("Subspace shape is missing")
            val enterMessage = loadNullableString(stream)
            val settings = loadSettings(stream)
            val stringTagCount = checkedCount(stream.readInt(), "subspace string tags")
            val stringTags = linkedSetOf<String>()
            repeat(stringTagCount) { require(stringTags.add(stream.readUTF())) { "duplicate subspace string tag" } }
            val keyedTagCount = checkedCount(stream.readInt(), "subspace keyed tags")
            val keyedTags = linkedMapOf<String, String>()
            repeat(keyedTagCount) {
                val key = stream.readUTF()
                require(!keyedTags.containsKey(key)) { "duplicate subspace keyed tag" }
                keyedTags[key] = stream.readUTF()
            }
            list.add(SubSpace(id, name, parentScopeId, worldId, shape, enterMessage, settings, stringTags, keyedTags))
        }
        return list
    }

    private fun saveNullableString(stream: DataOutputStream, value: String?) {
        stream.writeBoolean(value != null)
        if (value != null) stream.writeUTF(value)
    }

    private fun loadNullableString(stream: DataInputStream): String? =
        if (stream.readBoolean()) stream.readUTF() else null

    private fun validateDatabaseIdentities(regionsToValidate: List<Region>) {
        val regionIds = hashSetOf<Int>()
        val regionNames = TreeSet(String.CASE_INSENSITIVE_ORDER)
        val scopeIds = hashSetOf<AssignedScopeId>()
        val ownershipScopeIds = hashSetOf<AssignedScopeId>()
        val subSpaceIds = hashSetOf<Long>()
        for (region in regionsToValidate) {
            require(isValidGeoName(region.name)) { "invalid region name" }
            require(region.scopes.isNotEmpty()) { "region must contain at least one scope" }
            require(regionIds.add(region.numberID)) { "duplicate region id" }
            require(regionNames.add(region.name)) { "duplicate region name" }
            for (scope in region.scopes) {
                require(isValidGeoName(scope.scopeName)) { "invalid scope name" }
                require(scopeIds.add(scope.requireAssignedScopeId())) { "duplicate scope id" }
            }
            for (subSpace in region.subSpaces) {
                require(subSpaceIds.add(subSpace.subSpaceId)) { "duplicate subspace id" }
            }
            region.ownershipHistorySnapshot().keys.forEach { scopeId ->
                require(ownershipScopeIds.add(scopeId)) { "ownership history is stored by multiple regions" }
            }
        }
    }

    private fun saveOwnershipHistory(
        stream: DataOutputStream,
        history: Map<Long, MutableList<ScopeOwnershipEntry>>
    ) {
        stream.writeInt(checkedCount(history.size, "ownership history"))
        for ((scopeIdRaw, entries) in history) {
            stream.writeLong(scopeIdRaw)
            stream.writeInt(checkedCount(entries.size, "ownership entries"))
            for (entry in entries) {
                stream.writeLong(entry.scopeIdRaw)
                stream.writeInt(entry.fromRegionNumberId)
                stream.writeInt(entry.toRegionNumberId)
                stream.writeLong(entry.changedAtMillis)
            }
        }
    }

    private fun loadOwnershipHistory(stream: DataInputStream): MutableMap<Long, MutableList<ScopeOwnershipEntry>> {
        val mapSize = checkedCount(stream.readInt(), "ownership history")
        val result = mutableMapOf<Long, MutableList<ScopeOwnershipEntry>>()
        repeat(mapSize) {
            val key = stream.readLong()
            require(!result.containsKey(key)) { "duplicate ownership history scope id" }
            val entryCount = checkedCount(stream.readInt(), "ownership entries")
            val entries = ArrayList<ScopeOwnershipEntry>(entryCount)
            repeat(entryCount) {
                val sid = stream.readLong()
                val from = stream.readInt()
                val to = stream.readInt()
                val ts = stream.readLong()
                entries.add(ScopeOwnershipEntry(sid, from, to, ts))
            }
            result[key] = entries
        }
        return result
    }

    private fun saveTeleportPoint(stream: DataOutputStream, teleportPoint: BlockPos?) {
        if (teleportPoint == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeInt(teleportPoint.x)
            stream.writeInt(teleportPoint.y)
            stream.writeInt(teleportPoint.z)
        }
    }

    private fun loadTeleportPoint(stream: DataInputStream): BlockPos? {
        val hasTeleportPoint = stream.readBoolean()
        return if (!hasTeleportPoint) null
        else {
            val x = stream.readInt()
            val y = stream.readInt()
            val z = stream.readInt()
            BlockPos(x,y,z)
        }
    }

    private fun saveGeoShape(stream: DataOutputStream, shape: GeoShape?) {
        if (shape == null) {
            stream.writeBoolean(false)
        } else {
            val parameters = shape.shapeParameter
            validateShapeParameterCount(shape.geoShapeType, parameters.size)
            stream.writeBoolean(true)
            stream.writeInt(shape.geoShapeType.ordinal)
            stream.writeInt(checkedCount(parameters.size, "shape parameters"))
            parameters.forEach { stream.writeInt(it) }
        }
    }

    private fun loadGeoShape(stream: DataInputStream): GeoShape? {
        val hasShape = stream.readBoolean()
        return if (!hasShape) null
        else {
            val geoShapeType = readEnum(stream, GeoShapeType.entries, "shape type")
            val paramCount = checkedCount(stream.readInt(), "shape parameters")
            validateShapeParameterCount(geoShapeType, paramCount)
            val params = MutableList(paramCount) { stream.readInt() }
            try {
                GeoShape(geoShapeType, params)
            } catch (exception: IllegalArgumentException) {
                throw IOException("Invalid geometry for $geoShapeType", exception)
            } catch (exception: ArithmeticException) {
                throw IOException("Geometry coordinates exceed the supported range for $geoShapeType", exception)
            }
        }
    }

    private fun saveSettings(stream: DataOutputStream, settings: List<Setting>) {
        stream.writeInt(-checkedCount(settings.size, "settings"))
        settings.forEach { setting ->
            when (setting) {
                is PermissionSetting -> savePermissionSetting(stream, setting)
                is ExtensionPermissionSetting -> saveExtensionPermissionSetting(stream, setting)
                is EffectSetting -> saveEffectSetting(stream, setting)
                is RuleSetting -> saveRuleSetting(stream, setting)
                is ExtensionRuleSetting -> saveExtensionRuleSetting(stream, setting)
                is EntryExitToggleSetting -> saveEntryExitToggleSetting(stream, setting)
                is EntryExitMessageSetting -> saveEntryExitMessageSetting(stream, setting)
            }
        }
    }

    private fun loadSettings(stream: DataInputStream): MutableList<Setting> {
        val raw = stream.readInt()
        val (isNameBased, count) = if (raw < 0) Pair(true, -raw) else Pair(false, raw)
        checkedCount(count, "settings")
        val list = mutableListOf<Setting>()
        repeat(count) {
            val type = stream.readInt()
            val setting = when (type) {
                0 -> if (isNameBased) loadPermissionSettingName(stream) else loadPermissionSettingOrdinal(stream)
                1 -> if (isNameBased) loadEffectSettingName(stream) else loadEffectSettingOrdinal(stream)
                2 -> if (isNameBased) loadRuleSettingName(stream) else loadRuleSettingOrdinal(stream)
                3 -> if (isNameBased) loadEntryExitToggleSettingName(stream) else loadEntryExitToggleSettingOrdinal(stream)
                4 -> if (isNameBased) loadEntryExitMessageSettingName(stream) else loadEntryExitMessageSettingOrdinal(stream)
                5 -> loadExtensionPermissionSetting(stream)
                6 -> loadExtensionRuleSetting(stream)
                else -> throw IOException("Unknown setting type")
            }
            setting?.let(list::add)
        }
        return list
    }

    private fun savePermissionSetting(stream: DataOutputStream, setting: PermissionSetting) {
        stream.writeInt(0)
        stream.writeUTF(setting.key.name)
        stream.writeBoolean(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadPermissionSettingOrdinal(stream: DataInputStream): PermissionSetting {
        val key = readEnum(stream, PermissionKey.entries, "permission key")
        val value = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return PermissionSetting(key, value, uuid)
    }

    private fun loadPermissionSettingName(stream: DataInputStream): PermissionSetting {
        val key = loadEnumByName(stream, PermissionKey.entries, "permission key")
        val value = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return PermissionSetting(key, value, uuid)
    }

    private fun saveExtensionPermissionSetting(stream: DataOutputStream, setting: ExtensionPermissionSetting) {
        stream.writeInt(5)
        stream.writeUTF(setting.key.id)
        stream.writeBoolean(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadExtensionPermissionSetting(stream: DataInputStream): ExtensionPermissionSetting {
        val key = ExtensionPermissionKey(stream.readUTF())
        val value = stream.readBoolean()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return ExtensionPermissionSetting(key, value, uuid)
    }

    private fun saveEffectSetting(stream: DataOutputStream, setting: EffectSetting) {
        stream.writeInt(1)
        stream.writeUTF(setting.key.name)
        stream.writeInt(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadEffectSettingOrdinal(stream: DataInputStream): EffectSetting {
        val key = readEnum(stream, EffectKey.entries, "effect key")
        val value = stream.readInt()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return EffectSetting(key, value, uuid)
    }

    private fun loadEffectSettingName(stream: DataInputStream): EffectSetting {
        val key = loadEnumByName(stream, EffectKey.entries, "effect key")
        val value = stream.readInt()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return EffectSetting(key, value, uuid)
    }


    private fun saveRuleSetting(stream: DataOutputStream, setting: RuleSetting) {
        stream.writeInt(2)
        stream.writeUTF(setting.key.name)
        stream.writeBoolean(setting.value)
    }

    private fun loadRuleSettingOrdinal(stream: DataInputStream): RuleSetting? {
        val ordinal = stream.readInt()
        val value = stream.readBoolean()
        val key = legacyRuleKeyByOrdinal(ordinal) ?: return null
        return RuleSetting(key, value)
    }

    private fun loadRuleSettingName(stream: DataInputStream): RuleSetting? {
        val name = stream.readUTF()
        val value = stream.readBoolean()
        if (name == DELETED_RULE_RPG_NATURAL_REGEN) return null
        val key = RuleKey.entries.firstOrNull { it.name == name }
            ?: throw IOException("Invalid rule key name: $name")
        return RuleSetting(key, value)
    }

    private fun legacyRuleKeyByOrdinal(ordinal: Int): RuleKey? = when (ordinal) {
        in 0..RuleKey.PISTON.ordinal -> RuleKey.entries[ordinal]
        10 -> null
        11 -> RuleKey.RPG_FIRE_SPREAD
        12 -> RuleKey.RPG_HUNGER
        else -> throw IOException("Invalid rule key ordinal: $ordinal")
    }

    private fun saveExtensionRuleSetting(stream: DataOutputStream, setting: ExtensionRuleSetting) {
        stream.writeInt(6)
        stream.writeUTF(setting.key.id)
        stream.writeBoolean(setting.value)
    }

    private fun loadExtensionRuleSetting(stream: DataInputStream): ExtensionRuleSetting {
        val key = ExtensionRuleKey(stream.readUTF())
        val value = stream.readBoolean()
        return ExtensionRuleSetting(key, value)
    }

    private fun saveEntryExitToggleSetting(stream: DataOutputStream, setting: EntryExitToggleSetting) {
        stream.writeInt(3)
        stream.writeUTF(setting.key.name)
        stream.writeBoolean(setting.value)
    }

    private fun loadEntryExitToggleSettingOrdinal(stream: DataInputStream): EntryExitToggleSetting {
        val key = readEnum(stream, EntryExitToggleKey.entries, "entry/exit toggle key")
        val value = stream.readBoolean()
        return EntryExitToggleSetting(key, value)
    }

    private fun loadEntryExitToggleSettingName(stream: DataInputStream): EntryExitToggleSetting {
        val key = loadEnumByName(stream, EntryExitToggleKey.entries, "entry/exit toggle key")
        val value = stream.readBoolean()
        return EntryExitToggleSetting(key, value)
    }

    private fun saveEntryExitMessageSetting(stream: DataOutputStream, setting: EntryExitMessageSetting) {
        stream.writeInt(4)
        stream.writeUTF(setting.key.name)
        stream.writeUTF(setting.value)
    }

    private fun loadEntryExitMessageSettingOrdinal(stream: DataInputStream): EntryExitMessageSetting {
        val key = readEnum(stream, EntryExitMessageKey.entries, "entry/exit message key")
        val value = stream.readUTF()
        return EntryExitMessageSetting(key, value)
    }

    private fun loadEntryExitMessageSettingName(stream: DataInputStream): EntryExitMessageSetting {
        val key = loadEnumByName(stream, EntryExitMessageKey.entries, "entry/exit message key")
        val value = stream.readUTF()
        return EntryExitMessageSetting(key, value)
    }

    private fun incrementPlayerStat(
        regionId: Int,
        playerUUID: UUID,
        delta: Long = 1L,
        selector: RegionPlayerStatsLedger.() -> MutableMap<UUID, Long>
    ) {
        val ledger = regionPlayerStats.getOrPut(regionId) { RegionPlayerStatsLedger() }
        val target = ledger.selector()
        target[playerUUID] = Math.addExact(target[playerUUID] ?: 0L, delta)
    }

    private fun loadPlayerStats(
        loadedRegions: List<Region>,
        path: Path
    ): Map<Int, RegionPlayerStatsLedger> {
        val loaded = if (Files.exists(path)) readPlayerStats(path) else emptyMap()
        val liveRegionIds = loadedRegions.mapTo(hashSetOf()) { it.numberID }
        return loaded.filterKeys { it in liveRegionIds }
    }

    internal fun readPlayerStats(path: Path): Map<Int, RegionPlayerStatsLedger> {
        val root = readJsonObject(path, "player stats")
        val version = requirePositiveLong(root.get("version"), "player stats version")
        if (version != 1L) throw IOException("Unsupported player stats version: $version")
        val regionsJson = requireJsonObject(root.get("regions"), "player stats regions")

        return buildMap {
            for ((regionKey, element) in regionsJson.entrySet()) {
                val regionId = regionKey.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IOException("Invalid player stats region id: $regionKey")
                if (containsKey(regionId)) throw IOException("Duplicate player stats region id: $regionId")
                val regionJson = requireJsonObject(element, "player stats region $regionKey")
                val ledger = RegionPlayerStatsLedger(
                    entryCounts = readPlayerStatMap(optionalJsonObject(regionJson, "entries", "player stats region $regionKey"), "entries"),
                    stayMillis = readPlayerStatMap(optionalJsonObject(regionJson, "stayMillis", "player stats region $regionKey"), "stayMillis"),
                    deathCounts = readPlayerStatMap(optionalJsonObject(regionJson, "deaths", "player stats region $regionKey"), "deaths"),
                    blockPlaceCounts = readPlayerStatMap(optionalJsonObject(regionJson, "blockPlaces", "player stats region $regionKey"), "blockPlaces"),
                    blockBreakCounts = readPlayerStatMap(optionalJsonObject(regionJson, "blockBreaks", "player stats region $regionKey"), "blockBreaks")
                )
                try {
                    ledger.aggregate()
                } catch (error: ArithmeticException) {
                    throw IOException("Player stats totals exceed the supported range", error)
                }
                if (!ledger.isEmpty()) put(regionId, ledger)
            }
        }
    }

    private fun savePlayerStats() {
        writePlayerStats(getPlayerStatsPath(), regions.map { it.numberID }, regionPlayerStats)
    }

    internal fun writePlayerStats(
        path: Path,
        regionIds: Iterable<Int>,
        ledgers: Map<Int, RegionPlayerStatsLedger>
    ) {
        val root = JsonObject()
        root.addProperty("version", 1)
        val regionsJson = JsonObject()

        regionIds.forEach { regionId ->
            val ledger = ledgers[regionId] ?: return@forEach
            if (ledger.isEmpty()) return@forEach

            val regionJson = JsonObject()
            regionJson.add("entries", writePlayerStatMap(ledger.entryCounts))
            regionJson.add("stayMillis", writePlayerStatMap(ledger.stayMillis))
            regionJson.add("deaths", writePlayerStatMap(ledger.deathCounts))
            regionJson.add("blockPlaces", writePlayerStatMap(ledger.blockPlaceCounts))
            regionJson.add("blockBreaks", writePlayerStatMap(ledger.blockBreakCounts))
            regionsJson.add(regionId.toString(), regionJson)
        }

        root.add("regions", regionsJson)
        atomicWriteText(path, gson.toJson(root))
    }

    private fun readPlayerStatMap(source: JsonObject?, label: String): MutableMap<UUID, Long> {
        val result = mutableMapOf<UUID, Long>()
        source?.entrySet()?.forEach { entry ->
            val uuid = runCatching { UUID.fromString(entry.key) }.getOrNull()
                ?.takeIf { it.toString().equals(entry.key, ignoreCase = true) }
                ?: throw IOException("Invalid UUID in player stats $label: ${entry.key}")
            val value = requirePositiveLong(entry.value, "player stats $label for ${entry.key}")
            result[uuid] = value
        }
        return result
    }

    private fun writePlayerStatMap(source: Map<UUID, Long>): JsonObject {
        val result = JsonObject()
        source.entries
            .sortedBy { it.key.toString() }
            .forEach { (uuid, value) ->
                require(value > 0L) { "player stats values must be positive" }
                result.addProperty(uuid.toString(), value)
            }
        return result
    }

    private fun applyDynmapVisibility(loadedRegions: List<Region>, path: Path) {
        val loaded = if (Files.exists(path)) readDynmapVisibility(path) else emptyMap()
        for (region in loadedRegions) {
            val visibility = loaded[region.numberID]
            region.setDynmapVisibility(visibility?.showOnDynmap ?: true)
            for (scope in region.scopes) {
                scope.setDynmapVisibility(visibility?.scopes?.get(scope.scopeName) ?: true)
            }
        }
    }

    internal fun readDynmapVisibility(path: Path): Map<Int, DynmapVisibility> {
        val root = readJsonObject(path, "Dynmap visibility")
        val regionsJson = requireJsonObject(root.get("regions"), "Dynmap visibility regions")
        return buildMap {
            for ((regionKey, element) in regionsJson.entrySet()) {
                val regionId = regionKey.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IOException("Invalid Dynmap region id: $regionKey")
                if (containsKey(regionId)) throw IOException("Duplicate Dynmap region id: $regionId")
                val regionJson = requireJsonObject(element, "Dynmap region $regionKey")
                val scopesJson = optionalJsonObject(regionJson, "scopes", "Dynmap region $regionKey")
                val scopes = scopesJson?.entrySet()?.associate { (scopeName, value) ->
                    scopeName to requireBoolean(value, "Dynmap scope $scopeName in region $regionKey")
                }.orEmpty()
                put(
                    regionId,
                    DynmapVisibility(
                        showOnDynmap = regionJson.get("showOnDynmap")?.let {
                            requireBoolean(it, "Dynmap region $regionKey visibility")
                        } ?: true,
                        scopes = scopes
                    )
                )
            }
        }
    }

    private fun readJsonObject(path: Path, label: String): JsonObject = try {
        Files.newBufferedReader(path).use { reader ->
            requireJsonObject(JsonParser.parseReader(reader), "$label root")
        }
    } catch (error: IOException) {
        throw error
    } catch (error: RuntimeException) {
        throw IOException("Invalid $label JSON", error)
    }

    private fun requireJsonObject(element: JsonElement?, label: String): JsonObject {
        if (element == null || !element.isJsonObject) throw IOException("$label must be an object")
        return element.asJsonObject
    }

    private fun optionalJsonObject(parent: JsonObject, key: String, label: String): JsonObject? {
        val element = parent.get(key) ?: return null
        return requireJsonObject(element, "$label field '$key'")
    }

    private fun requirePositiveLong(element: JsonElement?, label: String): Long {
        val primitive = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        val value = primitive?.takeIf { it.isNumber }?.asString?.toLongOrNull()
        if (value == null || value <= 0L) throw IOException("$label must be a positive integer")
        return value
    }

    private fun requireBoolean(element: JsonElement, label: String): Boolean {
        val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        if (primitive?.isBoolean != true) throw IOException("$label must be a boolean")
        return primitive.asBoolean
    }

    private fun saveDynmapVisibility() {
        writeDynmapVisibility(getDynmapConfigPath(), regions)
    }

    internal fun writeDynmapVisibility(path: Path, regions: List<Region>) {
        val root = JsonObject()
        val regionsJson = JsonObject()
        for (region in regions) {
            val regionEntry = JsonObject()
            regionEntry.addProperty("showOnDynmap", region.showOnDynmap)
            val scopesEntry = JsonObject()
            for (scope in region.scopes) {
                scopesEntry.addProperty(scope.scopeName, scope.showOnDynmap)
            }
            regionEntry.add("scopes", scopesEntry)
            regionsJson.add(region.numberID.toString(), regionEntry)
        }
        root.add("regions", regionsJson)
        atomicWriteText(path, gson.toJson(root))
    }

    private fun rebuildDimensionIndex() {
        dimensionIndex.clear()
        for (region in regions) {
            for (scope in region.scopes) {
                if (scope.geoShape == null) continue
                val key = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, scope.worldId
                )
                dimensionIndex.getOrPut(key) { mutableListOf() }.add(region to scope)
            }
        }
    }

    private fun checkedCount(value: Int, label: String): Int {
        if (value !in 0..MAX_COLLECTION_SIZE) {
            throw IOException("Invalid $label count: $value")
        }
        return value
    }

    private fun validateShapeParameterCount(type: GeoShapeType, count: Int) {
        val valid = when (type) {
            GeoShapeType.UNKNOWN -> count == 0
            GeoShapeType.CIRCLE -> count == 3
            GeoShapeType.RECTANGLE -> count == 4
            GeoShapeType.POLYGON -> count in 6..(MAX_POLYGON_VERTICES * 2) && count % 2 == 0
        }
        if (!valid) throw IOException("Invalid parameter count $count for $type")
    }

    private fun <T> readEnum(stream: DataInputStream, entries: List<T>, label: String): T {
        val ordinal = stream.readInt()
        return entries.getOrNull(ordinal) ?: throw IOException("Invalid $label ordinal: $ordinal")
    }

    private inline fun <reified T : Enum<T>> loadEnumByName(stream: DataInputStream, entries: List<T>, label: String): T {
        val name = stream.readUTF()
        return entries.firstOrNull { (it as Enum<*>).name == name }
            ?: throw IOException("Invalid $label name: $name")
    }

    private fun atomicWriteText(path: Path, value: String) {
        atomicWrite(path) { it.write(value.toByteArray(Charsets.UTF_8)) }
    }

    internal fun atomicWrite(path: Path, writer: (OutputStream) -> Unit) {
        val parent = path.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            Files.newOutputStream(temporary).use(writer)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun getDynmapConfigPath(): Path {
        return requireSessionWorldRoot().resolve(DYNMAP_CONFIG_FILENAME)
    }

    private fun getPlayerStatsPath(): Path {
        return requireSessionWorldRoot().resolve(PLAYER_STATS_FILENAME)
    }

    private fun getDatabasePath(): Path {
        return requireSessionWorldRoot().resolve(DATABASE_FILENAME)
    }

    private fun requireSessionWorldRoot(): Path =
        checkNotNull(sessionWorldRoot) { "No active region database session" }
}
