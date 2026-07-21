package com.imyvm.iwg.domain.component

import net.minecraft.resources.Identifier

class SubSpace(
    val subSpaceId: Long,
    name: String,
    val parentScopeId: AssignedScopeId,
    val worldId: Identifier,
    shape: GeoShape,
    enterMessage: String? = null,
    settings: MutableList<Setting> = mutableListOf(),
    stringTags: Set<String> = emptySet(),
    keyedTags: Map<String, String> = emptyMap()
) {
    private var currentName: String = name
    private var currentShape: GeoShape = shape
    private var currentEnterMessage: String? = enterMessage
    private val mutableStringTags = linkedSetOf<String>()
    private val mutableKeyedTags = linkedMapOf<String, String>()
    internal val settingStore = SettingStore(settings)

    init {
        require(subSpaceId > 0L) { "subspace id must be positive" }
        require(isValidGeoName(name)) { "invalid subspace name" }
        validateTags(stringTags, keyedTags)
        mutableStringTags.addAll(stringTags)
        mutableKeyedTags.putAll(keyedTags)
    }

    var name: String
        get() = currentName
        set(value) {
            require(value == currentName) { "subspace name must be changed through its owning region" }
        }

    var geoShape: GeoShape
        get() = currentShape
        set(value) {
            require(value === currentShape) { "subspace geometry must be changed through the application boundary" }
        }

    var entryMessage: String?
        get() = currentEnterMessage
        set(value) {
            require(value == currentEnterMessage) { "subspace entry message must be changed through the application boundary" }
        }

    var settings: MutableList<Setting>
        get() = settingStore.toLegacyList().toMutableList()
        set(value) = settingStore.replaceAll(value)

    val stringTags: Set<String>
        get() = mutableStringTags.toSet()

    val keyedTags: Map<String, String>
        get() = mutableKeyedTags.toMap()

    internal fun renameTo(newName: String) {
        require(isValidGeoName(newName)) { "invalid subspace name" }
        currentName = newName
    }

    internal fun replaceGeometry(shape: GeoShape) {
        currentShape = shape
    }

    internal fun replaceEntryMessage(message: String?) {
        currentEnterMessage = message
    }

    internal fun addStringTag(tag: String): Boolean {
        requireValidTagPart(tag, "tag")
        return mutableStringTags.add(tag)
    }

    internal fun removeStringTag(tag: String): Boolean = mutableStringTags.remove(tag)

    internal fun putKeyedTag(key: String, value: String): String? {
        requireValidTagPart(key, "tag key")
        requireValidTagPart(value, "tag value")
        return mutableKeyedTags.put(key, value)
    }

    internal fun removeKeyedTag(key: String): String? = mutableKeyedTags.remove(key)

    private fun validateTags(stringTags: Set<String>, keyedTags: Map<String, String>) {
        stringTags.forEach { requireValidTagPart(it, "tag") }
        keyedTags.forEach { (key, value) ->
            requireValidTagPart(key, "tag key")
            requireValidTagPart(value, "tag value")
        }
    }
}

internal fun requireValidTagPart(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank" }
}
