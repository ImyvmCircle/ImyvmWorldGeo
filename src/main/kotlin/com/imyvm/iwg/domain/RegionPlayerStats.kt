package com.imyvm.iwg.domain

data class RegionPlayerStats(
    val trackedPlayerCount: Int,
    val entryCount: Long,
    val stayMillis: Long,
    val deathCount: Long,
    val blockPlaceCount: Long,
    val blockBreakCount: Long
) {
    val isEmpty: Boolean
        get() = entryCount == 0L &&
                stayMillis == 0L &&
                deathCount == 0L &&
                blockPlaceCount == 0L &&
                blockBreakCount == 0L
}
