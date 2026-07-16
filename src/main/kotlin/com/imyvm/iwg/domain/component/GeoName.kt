package com.imyvm.iwg.domain.component

private val SUPPORTED_LETTER = Regex(
    "[A-Za-z\\u00C0-\\u024F\\u0400-\\u04FF\\u0600-\\u06FF" +
        "\\u4E00-\\u9FFF\\u3400-\\u4DBF\\u3040-\\u309F\\u30A0-\\u30FF" +
        "\\uAC00-\\uD7AF\\u1100-\\u11FF\\u0370-\\u03FF]"
)
private const val NAME_SEPARATORS = " _-'"

internal fun isValidGeoName(name: String): Boolean {
    if (name.isEmpty() || !SUPPORTED_LETTER.matches(name.first().toString())) return false
    var previousWasSeparator = false
    for (character in name.drop(1)) {
        when {
            SUPPORTED_LETTER.matches(character.toString()) || character in '0'..'9' -> {
                previousWasSeparator = false
            }
            character in NAME_SEPARATORS && !previousWasSeparator -> previousWasSeparator = true
            else -> return false
        }
    }
    return !previousWasSeparator
}
