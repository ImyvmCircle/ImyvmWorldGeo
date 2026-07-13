package com.imyvm.iwg.infra

import com.imyvm.iwg.domain.*
import com.imyvm.iwg.domain.component.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.ImyvmWorldGeo
import net.fabricmc.loader.api.FabricLoader
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

private data class RegionPlayerStatsLedger(
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
            entryCount = entryCounts.values.sum(),
            stayMillis = stayMillis.values.sum(),
            deathCount = deathCounts.values.sum(),
            blockPlaceCount = blockPlaceCounts.values.sum(),
            blockBreakCount = blockBreakCounts.values.sum()
        )
    }

    fun mergeFrom(other: RegionPlayerStatsLedger) {
        mergeMap(entryCounts, other.entryCounts)
        mergeMap(stayMillis, other.stayMillis)
        mergeMap(deathCounts, other.deathCounts)
        mergeMap(blockPlaceCounts, other.blockPlaceCounts)
        mergeMap(blockBreakCounts, other.blockBreakCounts)
    }

    private fun mergeMap(target: MutableMap<UUID, Long>, source: Map<UUID, Long>) {
        source.forEach { (uuid, value) ->
            target[uuid] = (target[uuid] ?: 0L) + value
        }
    }
}

object RegionDatabase {

    private lateinit var regions: MutableList<Region>
    private const val DATABASE_FILENAME = "iwg_regions.db"
    private const val DYNMAP_CONFIG_FILENAME = "iwg_dynmap.json"
    private const val PLAYER_STATS_FILENAME = "iwg_player_stats.json"
    private const val DB_V1_SENTINEL: Int = -1
    private const val MAX_COLLECTION_SIZE = 100_000
    private val gson = Gson()
    private val regionPlayerStats: MutableMap<Int, RegionPlayerStatsLedger> = mutableMapOf()
    internal var onSave: (() -> Unit)? = null

    @Throws(IOException::class)
    fun save() {
        withFileRollback(listOf(getDatabasePath(), getDynmapConfigPath(), getPlayerStatsPath())) {
            writeRegions(getDatabasePath(), regions)
            saveDynmapVisibility()
            savePlayerStats()
            onSave?.invoke()
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
            stream.writeInt(DB_V1_SENTINEL)
            stream.writeInt(checkedCount(regions.size, "regions"))
            for (region in regions) {
                stream.writeUTF(region.name)
                stream.writeInt(region.numberID)
                saveGeoScopes(stream, region.scopes)
                saveSettings(stream, region.settings)
                saveOwnershipHistory(stream, region.ownershipHistoryByScope)
            }
        }
    }

    @Throws(IOException::class)
    fun load() {
        val file = getDatabasePath()
        if (!file.toFile().exists()) {
            regions = mutableListOf()
            regionPlayerStats.clear()
            return
        }

        regions = readRegions(file)
        loadDynmapVisibility()
        loadPlayerStats()
    }

    internal fun readRegions(path: Path): MutableList<Region> {
        try {
            return DataInputStream(Files.newInputStream(path)).use { stream ->
                val first = stream.readInt()
                val isV1 = first == DB_V1_SENTINEL
                val regionCount = checkedCount(if (isV1) stream.readInt() else first, "regions")
                val loadedRegions = ArrayList<Region>(regionCount)

                repeat(regionCount) {
                    val name = stream.readUTF()
                    val numberID = stream.readInt()
                    val geometryScopes = loadGeoScopes(stream, isV1, numberID)
                    val settings = loadSettings(stream)

                    val region = Region(name, numberID, geometryScopes, settings)
                    if (isV1) {
                        region.ownershipHistoryByScope = loadOwnershipHistory(stream)
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
        val existingScopeIds = regions.flatMapTo(hashSetOf()) { existing ->
            existing.scopes.map { it.requireAssignedScopeId() }
        }
        require(region.scopes.none { it.requireAssignedScopeId() in existingScopeIds }) { "duplicate scope id" }
        regions.add(region)
    }

    fun removeRegion(regionToDelete: Region) {
        regions.removeIf { it.name == regionToDelete.name && it.numberID == regionToDelete.numberID }
        regionPlayerStats.remove(regionToDelete.numberID)
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
        val sourceStats = regionPlayerStats[sourceRegion.numberID]?.detachedCopy()
        val targetStats = regionPlayerStats[targetRegion.numberID]?.detachedCopy()
        val restoreRegion = removeRegionReversibly(sourceRegion)
        sourceStats?.let {
            regionPlayerStats.getOrPut(targetRegion.numberID) { RegionPlayerStatsLedger() }.mergeFrom(it)
        }
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
        region.name = newName
    }

    fun getRegionList(): List<Region> {
        return regions
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
        val source = regionPlayerStats[sourceRegion.numberID] ?: return
        val target = regionPlayerStats.getOrPut(targetRegion.numberID) { RegionPlayerStatsLedger() }
        target.mergeFrom(source)
        regionPlayerStats.remove(sourceRegion.numberID)
    }

    fun savePlayerStatsSnapshot() {
        runCatching { savePlayerStats() }
            .onFailure { ImyvmWorldGeo.logger.error("Failed to save player stats: ${it.message}", it) }
    }

    fun getRegionByName(name: String): Region {
        return regions.find { it.name == name }
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
        val scope = region.scopes.find { it.scopeName == scopeName }
        return Pair(region, scope)
    }

    fun getRegionAndScope(region: Region, scopeName: String): Pair<Region, GeoScope?> {
        val scope = region.scopes.find { it.scopeName == scopeName }
        return Pair(region, scope)
    }

    fun getRegionAndScopeAt(world: Level, x: Int, z: Int): Pair<Region, GeoScope>? {
        val server = world.server
        for (region in regions) {
            for (scope in region.scopes) {
                if (server?.let { scope.getWorld(it) } == world) {
                    val geoShape = scope.geoShape
                    if (geoShape != null) {
                        if (geoShape.containsPoint(x, z)) {
                            return Pair(region, scope)
                        }
                    }
                }
            }
        }
        return null
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
        toRegion.recordScopeOwnership(entry)
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
        result.sortBy { it.changedAtMillis }
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

    private fun validateDatabaseIdentities(regionsToValidate: List<Region>) {
        val regionIds = hashSetOf<Int>()
        val scopeIds = hashSetOf<AssignedScopeId>()
        for (region in regionsToValidate) {
            require(regionIds.add(region.numberID)) { "duplicate region id" }
            for (scope in region.scopes) {
                require(scopeIds.add(scope.requireAssignedScopeId())) { "duplicate scope id" }
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
            validateShapeParameterCount(shape.geoShapeType, shape.shapeParameter.size)
            stream.writeBoolean(true)
            stream.writeInt(shape.geoShapeType.ordinal)
            stream.writeInt(checkedCount(shape.shapeParameter.size, "shape parameters"))
            shape.shapeParameter.forEach { stream.writeInt(it) }
        }
    }

    private fun loadGeoShape(stream: DataInputStream): GeoShape? {
        val hasShape = stream.readBoolean()
        return if (!hasShape) null
        else {
            val geoShapeType = readEnum(stream, GeoShapeType.entries, "shape type")
            val paramCount = checkedCount(stream.readInt(), "shape parameters")
            val params = MutableList(paramCount) { stream.readInt() }
            validateShapeParameterCount(geoShapeType, paramCount)
            GeoShape(geoShapeType, params)
        }
    }

    private fun saveSettings(stream: DataOutputStream, settings: List<Setting>) {
        stream.writeInt(checkedCount(settings.size, "settings"))
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
        val count = checkedCount(stream.readInt(), "settings")
        val list = mutableListOf<Setting>()
        repeat(count) {
            val type = stream.readInt()
            val setting = when (type) {
                0 -> loadPermissionSetting(stream)
                1 -> loadEffectSetting(stream)
                2 -> loadRuleSetting(stream)
                3 -> loadEntryExitToggleSetting(stream)
                4 -> loadEntryExitMessageSetting(stream)
                5 -> loadExtensionPermissionSetting(stream)
                6 -> loadExtensionRuleSetting(stream)
                else -> throw IOException("Unknown setting type")
            }
            list.add(setting)
        }
        return list
    }

    private fun savePermissionSetting(stream: DataOutputStream, setting: PermissionSetting) {
        stream.writeInt(0)
        stream.writeInt(setting.key.ordinal)
        stream.writeBoolean(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadPermissionSetting(stream: DataInputStream): PermissionSetting {
        val key = readEnum(stream, PermissionKey.entries, "permission key")
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
        stream.writeInt(setting.key.ordinal)
        stream.writeInt(setting.value)
        stream.writeUTF(setting.playerUUID?.toString() ?: "")
    }

    private fun loadEffectSetting(stream: DataInputStream): EffectSetting {
        val key = readEnum(stream, EffectKey.entries, "effect key")
        val value = stream.readInt()
        val uuidStr = stream.readUTF()
        val uuid = if (uuidStr.isNotEmpty()) UUID.fromString(uuidStr) else null
        return EffectSetting(key, value, uuid)
    }


    private fun saveRuleSetting(stream: DataOutputStream, setting: RuleSetting) {
        stream.writeInt(2)
        stream.writeInt(setting.key.ordinal)
        stream.writeBoolean(setting.value)
    }

    private fun loadRuleSetting(stream: DataInputStream): RuleSetting {
        val key = readEnum(stream, RuleKey.entries, "rule key")
        val value = stream.readBoolean()
        return RuleSetting(key, value)
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
        stream.writeInt(setting.key.ordinal)
        stream.writeBoolean(setting.value)
    }

    private fun loadEntryExitToggleSetting(stream: DataInputStream): EntryExitToggleSetting {
        val key = readEnum(stream, EntryExitToggleKey.entries, "entry/exit toggle key")
        val value = stream.readBoolean()
        return EntryExitToggleSetting(key, value)
    }

    private fun saveEntryExitMessageSetting(stream: DataOutputStream, setting: EntryExitMessageSetting) {
        stream.writeInt(4)
        stream.writeInt(setting.key.ordinal)
        stream.writeUTF(setting.value)
    }

    private fun loadEntryExitMessageSetting(stream: DataInputStream): EntryExitMessageSetting {
        val key = readEnum(stream, EntryExitMessageKey.entries, "entry/exit message key")
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
        target[playerUUID] = (target[playerUUID] ?: 0L) + delta
    }

    private fun loadPlayerStats() {
        regionPlayerStats.clear()
        val file = getPlayerStatsPath()
        if (!file.toFile().exists()) return

        val root = runCatching { JsonParser.parseReader(file.toFile().reader()).asJsonObject }.getOrElse { return }
        val regionsJson = root.getAsJsonObject("regions") ?: return

        regions.forEach { region ->
            val regionJson = regionsJson.getAsJsonObject(region.numberID.toString()) ?: return@forEach
            val ledger = RegionPlayerStatsLedger()
            readPlayerStatMap(regionJson.getAsJsonObject("entries"), ledger.entryCounts)
            readPlayerStatMap(regionJson.getAsJsonObject("stayMillis"), ledger.stayMillis)
            readPlayerStatMap(regionJson.getAsJsonObject("deaths"), ledger.deathCounts)
            readPlayerStatMap(regionJson.getAsJsonObject("blockPlaces"), ledger.blockPlaceCounts)
            readPlayerStatMap(regionJson.getAsJsonObject("blockBreaks"), ledger.blockBreakCounts)
            if (!ledger.isEmpty()) {
                regionPlayerStats[region.numberID] = ledger
            }
        }
    }

    private fun savePlayerStats() {
        val root = JsonObject()
        root.addProperty("version", 1)
        val regionsJson = JsonObject()

        regions.forEach { region ->
            val ledger = regionPlayerStats[region.numberID] ?: return@forEach
            if (ledger.isEmpty()) return@forEach

            val regionJson = JsonObject()
            regionJson.add("entries", writePlayerStatMap(ledger.entryCounts))
            regionJson.add("stayMillis", writePlayerStatMap(ledger.stayMillis))
            regionJson.add("deaths", writePlayerStatMap(ledger.deathCounts))
            regionJson.add("blockPlaces", writePlayerStatMap(ledger.blockPlaceCounts))
            regionJson.add("blockBreaks", writePlayerStatMap(ledger.blockBreakCounts))
            regionsJson.add(region.numberID.toString(), regionJson)
        }

        root.add("regions", regionsJson)
        val file = getPlayerStatsPath()
        atomicWriteText(file, gson.toJson(root))
    }

    private fun readPlayerStatMap(source: JsonObject?, target: MutableMap<UUID, Long>) {
        source?.entrySet()?.forEach { entry ->
            val uuid = runCatching { UUID.fromString(entry.key) }.getOrNull() ?: return@forEach
            val value = readLong(entry.value) ?: return@forEach
            if (value > 0L) {
                target[uuid] = value
            }
        }
    }

    private fun writePlayerStatMap(source: Map<UUID, Long>): JsonObject {
        val result = JsonObject()
        source.entries
            .filter { it.value > 0L }
            .sortedBy { it.key.toString() }
            .forEach { (uuid, value) -> result.addProperty(uuid.toString(), value) }
        return result
    }

    private fun readLong(element: JsonElement?): Long? =
        runCatching { element?.asLong }.getOrNull()

    private fun loadDynmapVisibility() {
        val file = getDynmapConfigPath()
        val root = if (file.toFile().exists()) {
            runCatching { JsonParser.parseReader(file.toFile().reader()).asJsonObject }.getOrElse { JsonObject() }
        } else {
            JsonObject()
        }
        val regionsJson = root.getAsJsonObject("regions") ?: JsonObject()
        var modified = false

        for (region in regions) {
            val regionKey = region.numberID.toString()
            val regionEntry = regionsJson.getAsJsonObject(regionKey) ?: JsonObject().also {
                regionsJson.add(regionKey, it)
                modified = true
            }
            if (regionEntry.has("showOnDynmap")) {
                region.showOnDynmap = regionEntry.get("showOnDynmap").asBoolean
            } else {
                regionEntry.addProperty("showOnDynmap", true)
                modified = true
            }
            val scopesEntry = regionEntry.getAsJsonObject("scopes") ?: JsonObject().also {
                regionEntry.add("scopes", it)
                modified = true
            }
            for (scope in region.scopes) {
                if (scopesEntry.has(scope.scopeName)) {
                    scope.showOnDynmap = scopesEntry.get(scope.scopeName).asBoolean
                } else {
                    scopesEntry.addProperty(scope.scopeName, true)
                    modified = true
                }
            }
        }

        if (modified) {
            root.add("regions", regionsJson)
            writeDynmapConfig(root)
        }
    }

    private fun saveDynmapVisibility() {
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
        writeDynmapConfig(root)
    }

    private fun writeDynmapConfig(root: JsonObject) {
        atomicWriteText(getDynmapConfigPath(), gson.toJson(root))
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
            GeoShapeType.POLYGON -> count >= 6 && count % 2 == 0
        }
        if (!valid) throw IOException("Invalid parameter count $count for $type")
    }

    private fun <T> readEnum(stream: DataInputStream, entries: List<T>, label: String): T {
        val ordinal = stream.readInt()
        return entries.getOrNull(ordinal) ?: throw IOException("Invalid $label ordinal: $ordinal")
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
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DYNMAP_CONFIG_FILENAME)
    }

    private fun getPlayerStatsPath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(PLAYER_STATS_FILENAME)
    }

    private fun getDatabasePath(): Path {
        return FabricLoader.getInstance().gameDir.resolve("world").resolve(DATABASE_FILENAME)
    }
}
