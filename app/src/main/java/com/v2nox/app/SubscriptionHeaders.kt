// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import java.net.URLDecoder
import java.util.Base64

data class SubscriptionMetadata(
    val title: String? = null,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expireEpochSeconds: Long = 0,
    val updateIntervalHours: Int? = null,
)

object SubscriptionHeaders {
    fun parse(headers: Map<String, List<String>>): SubscriptionMetadata {
        fun first(name: String): String? = headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.firstOrNull { it.isNotBlank() }

        val counters = first("subscription-userinfo")?.let(::parseUserinfo) ?: SubscriptionMetadata()
        return counters.copy(
            title = first("profile-title")?.let(::parseTitle)
                ?: first("content-disposition")?.let(::parseFilename),
            updateIntervalHours = first("profile-update-interval")?.let(::parseUpdateInterval),
        )
    }

    fun parseUserinfo(value: String): SubscriptionMetadata {
        val counters = value.split(';').mapNotNull { part ->
            val key = part.substringBefore('=').trim().lowercase()
            val number = part.substringAfter('=', "").trim().toLongOrNull()
            number?.takeIf { it >= 0 }?.let { key to it }
        }.toMap()
        return SubscriptionMetadata(
            uploadBytes = counters["upload"] ?: 0,
            downloadBytes = counters["download"] ?: 0,
            totalBytes = counters["total"] ?: 0,
            expireEpochSeconds = counters["expire"] ?: 0,
        )
    }

    fun parseTitle(value: String): String? {
        val title = value.trim()
        if (!title.startsWith("base64:", ignoreCase = true)) {
            return decodeUrl(title).takeIf { it.isNotBlank() }
        }
        val encoded = title.substringAfter(':').filterNot { it.isWhitespace() }
        for (decoder in listOf(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                return String(decoder.decode(encoded), Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
            } catch (_: IllegalArgumentException) {
                // Panels use both the standard and URL-safe alphabets, often without padding.
            }
        }
        return null
    }

    fun parseFilename(value: String): String? {
        val matches = Regex("""(?:^|;)\s*filename(\*)?\s*=\s*(?:"([^"]*)"|([^;]*))""", RegexOption.IGNORE_CASE)
            .findAll(value).toList()
        val match = matches.firstOrNull { it.groupValues[1] == "*" } ?: matches.firstOrNull() ?: return null
        var name = match.groupValues[2].ifEmpty { match.groupValues[3] }.trim()
        if (match.groupValues[1] == "*") {
            // RFC 5987: charset'language'percent-encoded-filename.
            name = name.split('\'', limit = 3).let { if (it.size == 3) it[2] else name }
        }
        return decodeUrl(name).removeSuffix(".txt").removeSuffix(".list").takeIf { it.isNotBlank() }
    }

    fun parseUpdateInterval(value: String): Int? =
        value.trim().substringBefore(';').toDoubleOrNull()?.let { hours ->
            if (hours.isFinite() && hours > 0) hours.toInt().coerceAtLeast(1) else null
        }

    private fun decodeUrl(value: String): String = try {
        // Unlike form encoding, a literal '+' in a profile name is not a space.
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }
}
