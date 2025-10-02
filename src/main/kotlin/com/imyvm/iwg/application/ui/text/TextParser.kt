package com.imyvm.iwg.application.ui.text

import net.minecraft.text.Text
import net.minecraft.text.Style
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting

object TextParser {

    fun parse(raw: String): Text {
        val lines = raw.split("\n")
        val result = Text.empty()

        for ((index, line) in lines.withIndex()) {
            val parsedLine = when {
                line.contains('&') -> parseAmpersandColors(line)
                line.contains('§') -> parseSectionColors(line)
                else -> Text.literal(line)
            }
            result.append(parsedLine)
            if (index < lines.lastIndex) result.append(Text.literal("\n"))
        }

        return result
    }

    fun validate(raw: String): Boolean {
        return try {
            parse(raw)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun preview(raw: String): String {
        return when {
            raw.contains('&') -> raw.replace("&([0-9a-fk-or])".toRegex(RegexOption.IGNORE_CASE), "§$1")
            else -> raw
        }
    }

    private fun parseAmpersandColors(raw: String): Text {
        val sectioned = raw.replace("&([0-9a-fk-or])".toRegex(RegexOption.IGNORE_CASE), "§$1")
        return parseSectionColors(sectioned)
    }

    private fun parseSectionColors(sectioned: String): Text {
        val result = Text.empty()
        var currentStyle = Style.EMPTY
        var i = 0
        while (i < sectioned.length) {
            if (sectioned[i] == '§' && i + 1 < sectioned.length) {
                val code = sectioned[i + 1].lowercaseChar()
                val formatting = Formatting.byCode(code)
                currentStyle = when {
                    formatting != null && formatting.isColor -> Style.EMPTY.withColor(TextColor.fromFormatting(formatting))
                    formatting != null -> currentStyle.withFormatting(formatting)
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
            result.append(Text.literal(segment).setStyle(currentStyle))
        }

        return result
    }
}