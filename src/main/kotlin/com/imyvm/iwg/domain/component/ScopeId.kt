package com.imyvm.iwg.domain.component

/**
 * Stable identifier for a [GeoScope], encoded as a single [Long].
 *
 * Bit layout (MSB to LSB):
 *  - bit 63       : type flag, always 1 -> any [ScopeId.raw] is negative when read as Long,
 *                   so it cannot collide with [com.imyvm.iwg.domain.Region.numberID] (positive Int)
 *                   in any shared parsing context.
 *  - bit 62..57   : reserved, written as 0.
 *  - bit 56..36   : 21 bits of hours-since-epoch, following the convention of RegionIdHandler.
 *                   Compatibility ids generated for legacy saves write this field as 0;
 *                   [parseScopeCreationTimeMillisOrNull] returns null for such ids.
 *  - bit 35..32   : 4 bits mark, reserved for future categorization (currently 0).
 *  - bit 31..6    : not used directly here, see compat note below.
 *  - bit  5..0    : 6 bits random for new scopes, or local index for compatibility ids.
 *  - bit 31..0    : 32 bits storing the [foundedInRegionNumberId] (the numberID of the
 *                   region the scope was first created in).
 *
 * The two ranges (random/local-index and foundedInRegionNumberId) actually overlap
 * because foundedInRegionNumberId already uses the full low 32 bits. The "scope local
 * discriminator" (hours + mark + random) is packed into bits 62..32 (31 bits total)
 * as `(hours[21] << 10) | (mark[4] << 6) | random[6]`, and the low 32 bits stay
 * pure foundedInRegionNumberId.
 */
@JvmInline
value class ScopeId(val raw: Long) {

    val isCompatibility: Boolean
        get() = scopeCreationHoursPart(raw) == 0L

    fun foundedInRegionNumberId(): Int = parseScopeFoundedInRegionNumberId(raw)

    fun creationTimeMillisOrNull(): Long? = parseScopeCreationTimeMillisOrNull(raw)

    fun mark(): Int = parseScopeMark(raw)

    fun toIdString(): String = "s" + java.lang.Long.toHexString(raw)

    override fun toString(): String = toIdString()

    companion object {
        const val UNASSIGNED_RAW: Long = 0L

        fun parse(s: String): ScopeId? {
            if (s.length < 2 || s[0] != 's') return null
            val hex = s.substring(1)
            return try {
                ScopeId(java.lang.Long.parseUnsignedLong(hex, 16))
            } catch (e: NumberFormatException) {
                null
            }
        }

        fun isScopeIdRaw(raw: Long): Boolean = raw < 0L
    }
}

private const val EPOCH_MILLIS: Long = 1704067200000L
private const val SIGN_BIT: Long = 1L shl 63
private const val MAX_COMPAT_INDEX: Int = (1 shl 10) - 1 // 4 bits mark + 6 bits random

private fun scopeHighPart(raw: Long): Long = (raw ushr 32) and 0x7FFFFFFFL
private fun scopeCreationHoursPart(raw: Long): Long = (scopeHighPart(raw) ushr 10) and 0x1FFFFFL

fun parseScopeCreationTimeMillisOrNull(raw: Long): Long? {
    val hours = scopeCreationHoursPart(raw)
    return if (hours == 0L) null else EPOCH_MILLIS + hours * 3600000L
}

fun parseScopeMark(raw: Long): Int = ((scopeHighPart(raw) ushr 6) and 0xFL).toInt()

fun parseScopeFoundedInRegionNumberId(raw: Long): Int = (raw and 0xFFFFFFFFL).toInt()

fun generateNewScopeIdRaw(foundedInRegionNumberId: Int, mark: Int = 0): Long {
    val markValue = (if (mark in 0..15) mark else 0).toLong()
    val hoursFromEpoch = ((System.currentTimeMillis() - EPOCH_MILLIS) / 3600000L) and 0x1FFFFFL
    val randomPart = kotlin.random.Random.nextInt(64).toLong()
    val high = (hoursFromEpoch shl 10) or (markValue shl 6) or randomPart
    val low = foundedInRegionNumberId.toLong() and 0xFFFFFFFFL
    return SIGN_BIT or (high shl 32) or low
}

fun generateCompatScopeIdRaw(foundedInRegionNumberId: Int, indexInRegion: Int): Long {
    val clamped = indexInRegion.coerceIn(0, MAX_COMPAT_INDEX).toLong()
    val high = clamped // hours = 0; mark = clamped >> 6; random = clamped & 0x3F
    val low = foundedInRegionNumberId.toLong() and 0xFFFFFFFFL
    return SIGN_BIT or (high shl 32) or low
}
