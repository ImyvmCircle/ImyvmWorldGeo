package com.imyvm.iwg.infra

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.imyvm.iwg.domain.WorldGeoCanonicalSpaceId
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationResult
import com.imyvm.iwg.domain.WorldGeoStructuredSpaceMutationStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class StructuredSpaceMutationEvidence(
    val fingerprint: String,
    val result: WorldGeoStructuredSpaceMutationResult
)

internal object StructuredSpaceMutationStore {
    private const val FORMAT_VERSION = 1
    private const val FILE_NAME = "iwg_structured_space_mutations.json"
    private var path: Path? = null
    private val records = linkedMapOf<String, StructuredSpaceMutationEvidence>()
    internal var failureInjector: (() -> Unit)? = null

    fun bindSession(worldRoot: Path) {
        check(path == null) { "Structured space mutation session is already active" }
        val target = worldRoot.toAbsolutePath().normalize().resolve(FILE_NAME)
        val loaded = if (Files.exists(target)) read(target) else emptyMap()
        records.clear()
        records.putAll(loaded)
        path = target
    }

    fun unbindSession() {
        records.clear()
        failureInjector = null
        path = null
    }

    fun evidence(callerNamespace: String, externalKey: String): StructuredSpaceMutationEvidence? =
        records[recordKey(callerNamespace, externalKey)]

    fun saveEvidence(
        callerNamespace: String,
        externalKey: String,
        evidence: StructuredSpaceMutationEvidence
    ) {
        val target = requireNotNull(path) { "Structured space mutation session is not active" }
        val key = recordKey(callerNamespace, externalKey)
        val previous = records.put(key, evidence)
        try {
            RegionDatabase.saveWithCompanion(target) { write(target, records) }
        } catch (error: Exception) {
            if (previous == null) records.remove(key) else records[key] = previous
            throw error
        }
    }

    private fun recordKey(callerNamespace: String, externalKey: String): String =
        "$callerNamespace\u0000$externalKey"

    private fun read(target: Path): Map<String, StructuredSpaceMutationEvidence> {
        try {
            val root = JsonParser.parseString(Files.readString(target)).asJsonObject
            require(root.get("formatVersion").asInt == FORMAT_VERSION)
            return buildMap {
                root.getAsJsonArray("records").forEach { element ->
                    val obj = element.asJsonObject
                    val namespace = obj.get("callerNamespace").asString
                    val externalKey = obj.get("externalKey").asString
                    val fingerprint = obj.get("fingerprint").asString
                    require(namespace.isNotEmpty() && namespace.all { it in 'a'..'z' || it in '0'..'9' || it in "_.-" })
                    require(externalKey.isNotBlank())
                    require(fingerprint.matches(Regex("[0-9a-f]{64}")))
                    val result = readResult(obj.getAsJsonObject("result"))
                    require(put(recordKey(namespace, externalKey), StructuredSpaceMutationEvidence(fingerprint, result)) == null) {
                        "duplicate structured mutation key"
                    }
                }
            }
        } catch (error: RuntimeException) {
            throw IOException("Invalid structured space mutation store", error)
        }
    }

    private fun write(target: Path, values: Map<String, StructuredSpaceMutationEvidence>) {
        failureInjector?.invoke()
        val root = JsonObject()
        root.addProperty("formatVersion", FORMAT_VERSION)
        root.add("records", JsonArray().also { array ->
            values.forEach { (key, evidence) ->
                val separator = key.indexOf('\u0000')
                array.add(JsonObject().also { obj ->
                    obj.addProperty("callerNamespace", key.substring(0, separator))
                    obj.addProperty("externalKey", key.substring(separator + 1))
                    obj.addProperty("fingerprint", evidence.fingerprint)
                    obj.add("result", resultJson(evidence.result))
                })
            }
        })
        RegionDatabase.atomicWrite(target) { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun resultJson(result: WorldGeoStructuredSpaceMutationResult) = JsonObject().also { obj ->
        obj.addProperty("status", result.status.name)
        result.canonicalSpaceId?.let { id ->
            obj.add("canonicalSpaceId", JsonObject().also {
                it.addProperty("regionId", id.regionId)
                id.scopeId?.let { value -> it.addProperty("scopeId", value) }
                id.subSpaceId?.let { value -> it.addProperty("subSpaceId", value) }
            })
        }
        result.beforeSpaceVersion?.let { obj.addProperty("beforeSpaceVersion", it) }
        result.afterSpaceVersion?.let { obj.addProperty("afterSpaceVersion", it) }
        obj.addProperty("reasonKey", result.reasonKey)
    }

    private fun readResult(obj: JsonObject): WorldGeoStructuredSpaceMutationResult {
        val id = obj.getAsJsonObject("canonicalSpaceId")?.let {
            WorldGeoCanonicalSpaceId(
                it.get("regionId").asInt,
                it.optionalLong("scopeId"),
                it.optionalLong("subSpaceId")
            )
        }
        val result = WorldGeoStructuredSpaceMutationResult(
            enumValueOf<WorldGeoStructuredSpaceMutationStatus>(obj.get("status").asString),
            id,
            obj.optionalString("beforeSpaceVersion"),
            obj.optionalString("afterSpaceVersion"),
            obj.get("reasonKey").asString
        )
        require(result.reasonKey.isNotBlank())
        require(result.status == WorldGeoStructuredSpaceMutationStatus.APPLIED ||
            result.status == WorldGeoStructuredSpaceMutationStatus.ALREADY_MATCHED)
        require(result.beforeSpaceVersion == null || result.beforeSpaceVersion.matches(Regex("[0-9a-f]{64}")))
        require(result.afterSpaceVersion == null || result.afterSpaceVersion.matches(Regex("[0-9a-f]{64}")))
        require(id == null || id.regionId > 0)
        require(id?.scopeId == null || id.scopeId != 0L)
        require(id?.subSpaceId == null || id.subSpaceId > 0L)
        return result
    }

    private fun JsonObject.optionalLong(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString
}
