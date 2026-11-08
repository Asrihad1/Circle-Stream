package com.circleftp.stream.player

object UrlNormalizer {

    /**
     * Normalizes BDIX HTTP media URLs.
     * Prevents OkHttp / URI syntax crashes caused by raw spaces, brackets, or invalid chars
     * in Circle FTP database records without double-encoding existing percent escapes.
     */
    fun normalize(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        val trimmed = rawUrl.trim()

        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd == -1) return trimmed

        val scheme = trimmed.substring(0, schemeEnd + 3)
        val afterScheme = trimmed.substring(schemeEnd + 3)

        val slashIdx = afterScheme.indexOf('/')
        if (slashIdx == -1) return trimmed

        val host = afterScheme.substring(0, slashIdx)
        val rawPath = afterScheme.substring(slashIdx)

        // Split query parameters if any
        val queryIdx = rawPath.indexOf('?')
        val pathOnly = if (queryIdx != -1) rawPath.substring(0, queryIdx) else rawPath
        val queryPart = if (queryIdx != -1) rawPath.substring(queryIdx) else ""

        // Escape path segments without double-encoding %
        val sanitizedPath = buildString {
            var i = 0
            val len = pathOnly.length
            while (i < len) {
                val c = pathOnly[i]
                when {
                    c == '%' && i + 2 < len && isHex(pathOnly[i + 1]) && isHex(pathOnly[i + 2]) -> {
                        // Already valid percent-encoded sequence (%20, %5B, etc.) -> keep verbatim
                        append(c)
                        append(pathOnly[i + 1])
                        append(pathOnly[i + 2])
                        i += 2
                    }
                    c == ' ' -> append("%20")
                    c == '[' -> append("%5B")
                    c == ']' -> append("%5D")
                    c == '{' -> append("%7B")
                    c == '}' -> append("%7D")
                    c == '|' -> append("%7C")
                    c == '^' -> append("%5E")
                    c == '`' -> append("%60")
                    c == '\\' -> append("%5C")
                    else -> append(c)
                }
                i++
            }
        }

        return "$scheme$host$sanitizedPath$queryPart"
    }

    private fun isHex(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }
}

