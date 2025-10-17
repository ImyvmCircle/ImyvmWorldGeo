package com.imyvm.iwg.application.region

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.infra.RegionDatabase


/**
 * ID Structure (32 bits):
 * - High 19 bits: Time information (hours since epoch)
 * - 1 bit: Special mark (0-9)
 * - Low 12 bits: Random number (0-4095)
 * Can represent approximately 120 years, with 4096 unique IDs per hour and up to 10 distinct marks
 */
private const val EPOCH_MILLIS = 1704067200000L

fun generateNewRegionId(mark: Int): Int {
    val markDigit = if (mark < 0 || mark > 9) 0 else mark

    val existingIds = RegionDatabase.getRegionList().map { it.numberID }.toSet()

    var newId: Int
    var attempts = 0
    val maxAttempts = 100

    do {
        newId = generateRegionIdFromCurrentTimeMillis(markDigit)
        attempts++

        if (attempts >= maxAttempts) {
            val hoursPart = getHoursFromEpoch() shl 13
            val markPart = markDigit shl 12
            for (randomPart in 0..4095) {
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
    val hoursFromEpoch = (regionId ushr 13) and 0xFFFFF
    return EPOCH_MILLIS + (hoursFromEpoch.toLong() * 3600000L)
}

fun parseMarkFromRegionId(regionId: Int): Int {
    return (regionId ushr 12) and 0x1
}

fun filterRegionsByMark(mark: Int): List<Region> {
    return RegionDatabase.getRegionList().filter { parseMarkFromRegionId(it.numberID) == mark }
}

private fun generateRegionIdFromCurrentTimeMillis(mark: Int): Int {
    val hoursFromEpoch = getHoursFromEpoch()
    val timePart = (hoursFromEpoch and 0xFFFFF) shl 13
    val markPart = mark shl 12
    val randomPart = kotlin.random.Random.nextInt(4096)
    return timePart or markPart or randomPart
}

private fun getHoursFromEpoch(): Int {
    val millisFromEpoch = System.currentTimeMillis() - EPOCH_MILLIS
    return (millisFromEpoch / 3600000L).toInt()
}
