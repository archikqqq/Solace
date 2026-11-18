package com.v2nox.app

/** Escape URI-illegal characters without changing delimiters or existing percent escapes. */
internal object LinkUtils {
    fun fixIllegalUrl(value: String): String = buildString {
        value.forEachIndexed { index, char ->
            val validEscape = char == '%' && index + 2 < value.length &&
                value[index + 1].digitToIntOrNull(16) != null &&
                value[index + 2].digitToIntOrNull(16) != null
            when {
                char == '%' && !validEscape -> append("%25")
                char.isWhitespace() || char.isISOControl() || char in "|\\<>\"{}^`" -> {
                    char.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                        append("%%%02X".format(byte.toInt() and 0xff))
                    }
                }
                char in "[]#" && value.indexOf('#').let { it >= 0 && index > it } ->
                    append("%%%02X".format(char.code))
                else -> append(char)
            }
        }
    }
}
