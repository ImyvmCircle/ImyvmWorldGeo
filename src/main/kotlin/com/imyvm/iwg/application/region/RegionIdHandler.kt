package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase


/**
 * ID Structure (32 bits):
 * - High 21 bits: Time information (hours since epoch)
 * - Next 4 bits: Special mark (0-15, using 0-9)
 * - Low 7 bits: Random number (0-127)
 * Can represent approximately 239 years, with 128 unique IDs per hour and up to 10 distinct marks
 */
private const val EPOCH_MILLIS = 1704067200000L
private const val DISCRIMINATOR_COUNT = 128

class RegionIdCapacityExceededException : IllegalStateException()

fun generateNewRegionId(mark: Int): Int {
    require(mark in 0..9) { "region mark must be between 0 and 9" }
    val existingIds = RegionDatabase.getRegionList().map { it.numberID }.toSet()
    return allocateRegionId(
        mark = mark,
        hoursFromEpoch = getHoursFromEpoch(),
        existingIds = existingIds,
        initialDiscriminator = kotlin.random.Random.nextInt(DISCRIMINATOR_COUNT)
    )
}

internal fun allocateRegionId(
    mark: Int,
    hoursFromEpoch: Int,
    existingIds: Set<Int>,
    initialDiscriminator: Int
): Int {
    require(mark in 0..9) { "region mark must be between 0 and 9" }
    require(initialDiscriminator in 0 until DISCRIMINATOR_COUNT)
    val baseId = ((hoursFromEpoch and 0x1FFFFF) shl 11) or (mark shl 7)
    repeat(DISCRIMINATOR_COUNT) { offset ->
        val discriminator = (initialDiscriminator + offset) and 0x7F
        val candidateId = baseId or discriminator
        if (candidateId !in existingIds) return candidateId
    }
    throw RegionIdCapacityExceededException()
}

fun parseFoundingTimeFromRegionId(regionId: Int): Long {
    val hoursFromEpoch = (regionId ushr 11) and 0x1FFFFF
    return EPOCH_MILLIS + (hoursFromEpoch.toLong() * 3600000L)
}

fun parseMarkFromRegionId(regionId: Int): Int {
    return (regionId ushr 7) and 0xF
}

fun filterRegionsByMark(mark: Int): List<Region> {
    require(mark in 0..9) { "region mark must be between 0 and 9" }
    return RegionDatabase.getRegionList().filter { parseMarkFromRegionId(it.numberID) == mark }
}

private fun getHoursFromEpoch(): Int {
    val millisFromEpoch = System.currentTimeMillis() - EPOCH_MILLIS
    return (millisFromEpoch / 3600000L).toInt()
}
