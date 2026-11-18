// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import java.util.Base64

object SubscriptionParser {
    internal data class PreparedBody(val text: String, val format: String, val decodedFormat: String, val bodyBytes: Int)

    data class Analysis(
        val servers: List<ServerConfig>,
        val format: String,
        val decodedFormat: String,
        val lineCount: Int,
        val schemes: Map<String, Int>,
        val bodyBytes: Int,
    ) {
        fun failureMessage(): String = when {
            bodyBytes == 0 -> "Пустой ответ"
            decodedFormat == "json" -> "JSON без поддерживаемых прокси-серверов (vless/vmess/trojan/ss)"
            schemes.isNotEmpty() && decodedFormat == "links" ->
                "Ссылок разобрано 0 из $lineCount строк (${schemes.entries.joinToString { "${it.key}×${it.value}" }})"
            else -> "В ответе нет ссылок (формат: $decodedFormat, $bodyBytes байт)"
        }
    }

    fun parse(body: String): List<ServerConfig> = analyze(body).servers

    /** Only a validated scheme and exception type reach the callback, never link contents. */
    fun analyze(body: String, onRejected: (String, String) -> Unit = { _, _ -> }): Analysis =
        analyze(prepareBody(body), onRejected)

    internal fun analyze(body: PreparedBody, onRejected: (String, String) -> Unit = { _, _ -> }): Analysis {
        val (text, format, decodedFormat, bodyBytes) = body
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val schemes = linkedMapOf<String, Int>()
        val rawServers = if (decodedFormat == "json") RawConfigParser.parse(text) else null
        val servers = rawServers ?: if (decodedFormat == "links") lines.mapNotNull { line ->
            val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]{0,31})://").find(line)
                ?.groupValues?.get(1)?.lowercase()
            if (scheme != null) schemes[scheme] = (schemes[scheme] ?: 0) + 1
            try {
                ServerLinkParser.parse(line)
            } catch (e: Exception) {
                onRejected(scheme ?: "unknown", e.javaClass.simpleName)
                null
            }
        }.distinctBy { it.rawUrl } else emptyList()
        return Analysis(servers, format, decodedFormat, lines.size, schemes, bodyBytes)
    }

    fun isNestedSubscriptionUrl(body: String): String? = isNestedSubscriptionUrl(prepareBody(body))

    internal fun isNestedSubscriptionUrl(body: PreparedBody): String? {
        val lines = body.text.lineSequence().map(String::trim)
            .filter(String::isNotBlank).toList()
        val url = lines.singleOrNull() ?: return null
        return url.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
    }

    internal fun prepareBody(body: String): PreparedBody {
        val cleanBody = clean(body)
        val text = decodeBody(cleanBody)
        val decodedFormat = detectFormat(text)
        val format = if (text != cleanBody) "base64" else decodedFormat
        return PreparedBody(text, format, decodedFormat, body.toByteArray(Charsets.UTF_8).size)
    }

    private fun detectFormat(text: String): String {
        val trimmed = clean(text)
        return when {
            trimmed.startsWith("<") -> "html"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
            Regex("(?m)^\\s*(?:proxies|proxy-groups|port|mixed-port|outbounds|rules|dns):").containsMatchIn(trimmed) -> "yaml"
            Regex("(?m)^\\s*[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) -> "links"
            else -> "unknown"
        }
    }

    private fun clean(text: String) = text.trim().removePrefix("\uFEFF").trim()

    private fun decodeBody(text: String): String {
        if (detectFormat(text) != "unknown" || "://" in text) return text
        val encoded = text.filterNot { it.isWhitespace() }
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        for (decoder in listOf(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                val decoded = clean(String(decoder.decode(padded), Charsets.UTF_8))
                if (decoded.isNotBlank()) return decoded
            } catch (_: IllegalArgumentException) {
                // Panels use both alphabets, with or without padding.
            }
        }
        return text
    }
}
