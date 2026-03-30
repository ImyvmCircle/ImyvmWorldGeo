package com.imyvm.iwg.util.text

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.ChatFormatting

object TextParser {

    fun parse(raw: String): Component {
        val lines = raw.split("\n")
        val result = Component.empty()

        for ((index, line) in lines.withIndex()) {
            val parsedLine = when {
                line.contains('&') -> parseAmpersandColors(line)
                line.contains('§') -> parseSectionColors(line)
                else -> Component.literal(line)
            }
            result.append(parsedLine)
            if (index < lines.lastIndex) result.append(Component.literal("\n"))
        }

        return result
    }

    private fun parseAmpersandColors(raw: String): Component {
        val sectioned = raw.replace("&([0-9a-fk-or])".toRegex(RegexOption.IGNORE_CASE), "§$1")
        return parseSectionColors(sectioned)
    }

    private fun parseSectionColors(sectioned: String): Component {
        val result = Component.empty()
        var currentStyle = Style.EMPTY
        var i = 0
        while (i < sectioned.length) {
            if (sectioned[i] == '§' && i + 1 < sectioned.length) {
                val code = sectioned[i + 1].lowercaseChar()
                val formatting = ChatFormatting.getByCode(code)
                currentStyle = when {
                    formatting != null && formatting.isColor() -> Style.EMPTY.withColor(TextColor.fromLegacyFormat(formatting))
                    formatting != null -> currentStyle.applyFormat(formatting)
                    code == 'r' -> Style.EMPTY
                    else -> currentStyle
                }
                i += 2
                continue
            }

            val start = i
            while (i < sectioned.length && sectioned[i] != '§') {
                i++
            }
            val segment = sectioned.substring(start, i)
            result.append(Component.literal(segment).setStyle(currentStyle))
        }

        return result
    }
}