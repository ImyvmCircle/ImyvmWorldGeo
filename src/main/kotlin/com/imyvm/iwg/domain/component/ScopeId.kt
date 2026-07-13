package com.imyvm.iwg.domain.component

class ScopeIdCapacityExceededException : IllegalStateException()

/**
 * Stable identifier for a [GeoScope], encoded as a single [Long].
 *
 * Bit layout (MSB to LSB):
 *  - bit 63       : type flag, always 1 -> any [ScopeId.raw] is negative when read as Long,
 *                   so it cannot collide with [com.imyvm.iwg.domain.Region.numberID] (positive Int)
 *                   in any shared parsing context.
 *  - bit 62       : compatibility-id marker. Older compatibility ids leave it 0.
 *  - bit 61..42   : 20 bits of hours-since-epoch, following the convention of RegionIdHandler.
 *                   Compatibility ids generated for legacy saves write this field as 0;
 *                   [parseScopeCreationTimeMillisOrNull] returns null for such ids.
 *  - bit 41..38   : 4 bits mark, reserved for categorization.
 *  - bit 37..32   : 6 bits discriminator for new scopes.
 *  - bit 31..0    : 32 bits storing the [foundedInRegionNumberId] (the numberID of the
 *                   region the scope was first created in).
 *
 * The two ranges (random/local-index and foundedInRegionNumberId) actually overlap
 * because foundedInRegionNumberId already uses the full low 32 bits. The "scope local
 * discriminator" (hours + mark + random) is packed into bits 61..32 (30 bits total)
 * as `(hours[20] << 10) | (mark[4] << 6) | random[6]`, and the low 32 bits stay
 * pure foundedInRegionNumberId.
 */
@JvmInline
value class ScopeId(val raw: Long) {

    val isCompatibility: Boolean
        get() = isCompatibilityScopeIdRaw(raw)

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
private const val COMPATIBILITY_BIT: Long = 1L shl 62
private const val MAX_COMPAT_INDEX: Int = (1 shl 30) - 1

private fun scopeHighPart(raw: Long): Long = (raw ushr 32) and 0x7FFFFFFFL
private fun scopeCreationHoursPart(raw: Long): Long = (scopeHighPart(raw) ushr 10) and 0xFFFFFL
private fun isCompatibilityScopeIdRaw(raw: Long): Boolean =
    raw and COMPATIBILITY_BIT != 0L || scopeCreationHoursPart(raw) == 0L

fun parseScopeCreationTimeMillisOrNull(raw: Long): Long? {
    if (isCompatibilityScopeIdRaw(raw)) return null
    val hours = scopeCreationHoursPart(raw)
    return EPOCH_MILLIS + hours * 3600000L
}

fun parseScopeMark(raw: Long): Int =
    if (isCompatibilityScopeIdRaw(raw)) 0 else ((scopeHighPart(raw) ushr 6) and 0xFL).toInt()

fun parseScopeFoundedInRegionNumberId(raw: Long): Int = (raw and 0xFFFFFFFFL).toInt()

fun generateNewScopeIdRaw(foundedInRegionNumberId: Int, mark: Int = 0): Long {
    return generateNewScopeIdRaw(foundedInRegionNumberId, mark, kotlin.random.Random.nextInt(64))
}

internal fun generateNewScopeIdRaw(foundedInRegionNumberId: Int, mark: Int, discriminator: Int): Long {
    return generateNewScopeIdRaw(foundedInRegionNumberId, mark, discriminator, currentScopeCreationHours())
}

internal fun generateNewScopeIdRaw(
    foundedInRegionNumberId: Int,
    mark: Int,
    discriminator: Int,
    creationHours: Long
): Long {
    require(discriminator in 0..63) { "scope discriminator is out of range" }
    val markValue = (if (mark in 0..15) mark else 0).toLong()
    val hoursFromEpoch = creationHours and 0xFFFFFL
    val randomPart = discriminator.toLong()
    val high = (hoursFromEpoch shl 10) or (markValue shl 6) or randomPart
    val low = foundedInRegionNumberId.toLong() and 0xFFFFFFFFL
    return SIGN_BIT or (high shl 32) or low
}

fun generateCompatScopeIdRaw(foundedInRegionNumberId: Int, indexInRegion: Int): Long {
    require(indexInRegion in 0..MAX_COMPAT_INDEX) { "compatibility scope index is out of range" }
    val high = indexInRegion.toLong()
    val low = foundedInRegionNumberId.toLong() and 0xFFFFFFFFL
    return SIGN_BIT or COMPATIBILITY_BIT or (high shl 32) or low
}

internal fun currentScopeCreationHours(): Long =
    (System.currentTimeMillis() - EPOCH_MILLIS) / 3600000L
