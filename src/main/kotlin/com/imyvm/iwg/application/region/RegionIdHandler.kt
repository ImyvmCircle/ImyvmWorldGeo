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

fun generateNewRegionId(mark: Int): Int {
    val markValue = if (mark < 0 || mark > 9) 0 else mark
    val existingIds = RegionDatabase.getRegionList().map { it.numberID }.toSet()
    var newId: Int
    var attempts = 0
    val maxAttempts = 100

    do {
        newId = generateRegionIdFromCurrentTimeMillis(markValue)
        attempts++

        if (attempts >= maxAttempts) {
            val hoursPart = getHoursFromEpoch() shl 11
            val markPart = markValue shl 7
            for (randomPart in 0..127) {
                val candidateId = hoursPart or markPart or randomPart
                if (candidateId !in existingIds) {
                    newId = candidateId
                    break
                }
            }
            break
        }
    } while (newId in existingIds)

    return newId
}

fun parseFoundingTimeFromRegionId(regionId: Int): Long {
    val hoursFromEpoch = (regionId ushr 11) and 0x1FFFFF
    return EPOCH_MILLIS + (hoursFromEpoch.toLong() * 3600000L)
}

fun parseMarkFromRegionId(regionId: Int): Int {
    return (regionId ushr 7) and 0xF
}

fun filterRegionsByMark(mark: Int): List<Region> {
    val markValue = if (mark < 0 || mark > 9) 0 else mark
    return RegionDatabase.getRegionList().filter { parseMarkFromRegionId(it.numberID) == markValue }
}

private fun generateRegionIdFromCurrentTimeMillis(mark: Int): Int {
    val hoursFromEpoch = getHoursFromEpoch()
    val timePart = (hoursFromEpoch and 0x1FFFFF) shl 11
    val markPart = (mark and 0xF) shl 7
    val randomPart = kotlin.random.Random.nextInt(128)
    return timePart or markPart or randomPart
}

private fun getHoursFromEpoch(): Int {
    val millisFromEpoch = System.currentTimeMillis() - EPOCH_MILLIS
    return (millisFromEpoch / 3600000L).toInt()
}
