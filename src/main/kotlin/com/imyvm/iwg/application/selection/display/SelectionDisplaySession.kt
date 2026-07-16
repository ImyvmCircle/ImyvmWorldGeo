package com.imyvm.iwg.application.selection.display

internal const val SELECTION_DISPLAY_MAX_UNITS = 2048

internal class SelectionDisplaySession(maxUnits: Int = SELECTION_DISPLAY_MAX_UNITS) {
    private val pillars = LinkedHashSet<Long>()

    var remainingUnits: Int = maxUnits
        private set

    val exhausted: Boolean
        get() = remainingUnits == 0

    val surfaceUnits: Int
        get() = (remainingUnits - pillars.size).coerceAtLeast(0)

    val pillarCount: Int
        get() = pillars.size

    init {
        require(maxUnits > 0) { "display budget must be positive" }
    }

    fun tryUse(units: Int = 1): Boolean {
        require(units >= 0) { "display units must not be negative" }
        if (units > remainingUnits) return false
        remainingUnits -= units
        return true
    }

    fun tryUseSurface(units: Int = 1): Boolean {
        if (units > surfaceUnits) return false
        return tryUse(units)
    }

    fun queuePillar(x: Int, z: Int): Boolean {
        val packed = packXZ(x, z)
        if (packed in pillars) return false
        if (remainingUnits <= pillars.size) return false
        if (!tryUse()) return false
        pillars.add(packed)
        return true
    }

    fun removePillar(x: Int, z: Int) {
        pillars.remove(packXZ(x, z))
    }

    fun forEachPillar(action: (Int, Int) -> Unit) {
        pillars.forEach { packed -> action((packed shr 32).toInt(), packed.toInt()) }
    }

    fun clearPillars() {
        pillars.clear()
    }
}

private fun packXZ(x: Int, z: Int): Long =
    (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
