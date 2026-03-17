package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.Region
import kotlin.math.abs

object DynmapColorResolver {

    private val COLOR_WORDS = linkedMapOf(
        "红" to 0xFF4444,
        "橙" to 0xFF8800,
        "黄" to 0xFFFF00,
        "绿" to 0x44BB44,
        "蓝" to 0x4444FF,
        "紫" to 0x8800FF,
        "白" to 0xFFFFFF,
        "黑" to 0x111111,
        "灰" to 0x808080,
        "粉" to 0xFF69B4,
        "青" to 0x00BFFF,
        "棕" to 0x8B4513,
        "金" to 0xFFD700,
        "银" to 0xC0C0C0,
        "red" to 0xFF4444,
        "orange" to 0xFF8800,
        "yellow" to 0xFFFF00,
        "green" to 0x44BB44,
        "blue" to 0x4444FF,
        "purple" to 0x8800FF,
        "white" to 0xFFFFFF,
        "black" to 0x111111,
        "gray" to 0x808080,
        "grey" to 0x808080,
        "pink" to 0xFF69B4,
        "cyan" to 0x00BFFF,
        "brown" to 0x8B4513,
        "gold" to 0xFFD700,
        "silver" to 0xC0C0C0,
        "violet" to 0x8A2BE2,
        "indigo" to 0x4B0082,
        "magenta" to 0xFF00FF,
        "crimson" to 0xDC143C,
        "scarlet" to 0xFF2400,
        "amber" to 0xFFBF00,
        "lime" to 0x00CC00,
        "teal" to 0x008080,
        "navy" to 0x000080,
        "maroon" to 0x800000,
        "coral" to 0xFF7F50
    )

    private val ID_PALETTE = intArrayOf(
        0xE74C3C,
        0x2ECC71,
        0x3498DB,
        0xF39C12,
        0x9B59B6,
        0x1ABC9C,
        0xE91E63,
        0x00BCD4,
        0x8BC34A,
        0xFF5722,
        0x673AB7,
        0x009688,
        0xF44336,
        0x4CAF50,
        0x2196F3,
        0xFF9800,
        0x9C27B0,
        0x795548,
        0x607D8B,
        0x00ACC1
    )

    fun resolveColor(region: Region): Int {
        val nameLower = region.name.lowercase()
        var earliestIndex = Int.MAX_VALUE
        var earliestColor = -1
        for ((word, color) in COLOR_WORDS) {
            val idx = nameLower.indexOf(word.lowercase())
            if (idx >= 0 && idx < earliestIndex) {
                earliestIndex = idx
                earliestColor = color
            }
        }
        return if (earliestColor >= 0) earliestColor
        else ID_PALETTE[abs(region.numberID) % ID_PALETTE.size]
    }
}
