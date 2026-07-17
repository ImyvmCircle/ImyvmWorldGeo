package com.imyvm.iwg.inter.api

/** Result of a typed setting insertion through [PlayerInteractionApi]. */
enum class SettingAddResult {
    /** The new setting was persisted. */
    SUCCESS,

    /** The same family, key, and subject already had an explicit value; persistence was not attempted. */
    ALREADY_EXISTS,

    /** Persistence failed and the inserted setting was removed from memory. */
    PERSISTENCE_FAILED
}

/** Result of a typed setting removal through [PlayerInteractionApi]. */
enum class SettingRemoveResult {
    /** The setting was removed and the change was persisted. */
    SUCCESS,

    /** No setting matched the requested family, key, and subject; persistence was not attempted. */
    NOT_FOUND,

    /** Persistence failed and the exact removed value was restored in memory. */
    PERSISTENCE_FAILED
}
