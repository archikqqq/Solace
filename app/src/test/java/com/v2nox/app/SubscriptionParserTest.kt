// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {
    private val vless = "vless://user@example.com:443?security=tls#First"
    private val trojan = "trojan://password@example.org:8443#Second"

    @Test
    fun illegalFragmentIsDecodedAndRawUrlPreservedIncludingVpnPath() {
        val link = "vless://user@example.com:443?security=tls#🇩🇪 Germany | Fast"
        val parsed = ServerLinkParser.parse(link)
        assertEquals("🇩🇪 Germany | Fast", parsed.name)
        assertEquals(link, parsed.rawUrl)
        assertEquals(parsed.name, ServerLinkParser.parseRawLink(link).name)
        assertEquals(listOf(link), SubscriptionParser.parse(link).map { it.rawUrl })
    }

    @Test
    fun alreadyEncodedFragmentIsNotDoubleEncoded() {
        val link = "vless://user@example.com:443#%F0%9F%87%A9%F0%9F%87%AA%20Germany%20%7C%20Fast"
        assertEquals("🇩🇪 Germany | Fast", ServerLinkParser.parse(link).name)
        assertEquals(link, ServerLinkParser.parse(link).rawUrl)
        assertEquals("🇩🇪 Germany | Fast", ServerLinkParser.parseRawLink(link).name)
    }

    @Test
    fun otherIllegalCharactersAndLiteralPercentAreEscaped() {
        val name = "Name | <>\\\"{}^` [fast] #100%"
        assertEquals(name, ServerLinkParser.parse("vless://user@example.com:443#$name").name)
    }

    @Test
    fun shadowsocksUsesSameSanitizer() {
        val link = "ss://YWVzLTEyOC1nY206cGFzcw@example.com:443?plugin=x|y#🇩🇪 Germany | Fast"
        assertEquals("🇩🇪 Germany | Fast", ServerLinkParser.parse(link).name)
        assertEquals("example.com", ServerLinkParser.parseRawLink(link).host)
    }

    @Test
    fun decodedBomAndWrappedUnpaddedBase64() {
        val body = "\uFEFF$vless\n$trojan"
        for (encoder in listOf(Base64.getEncoder(), Base64.getUrlEncoder())) {
            val wrapped = encoder.withoutPadding().encodeToString(body.toByteArray())
                .chunked(17).joinToString("\r\n")
            assertEquals(listOf(vless, trojan), SubscriptionParser.parse("\uFEFF$wrapped").map { it.rawUrl })
            assertEquals("base64", SubscriptionParser.analyze(wrapped).format)
        }
    }

    @Test
    fun diagnosticsDistinguishNonLinkFormatsAndBrokenLinks() {
        for ((format, body) in listOf(
            "json" to "{\"dns\":\"https://example.com\"}",
            "yaml" to "proxies:\n  - name: test",
            "html" to "<!DOCTYPE html><html>Sign in</html>",
            "unknown" to "not a subscription!",
        )) {
            val analysis = SubscriptionParser.analyze(body)
            assertEquals(format, analysis.decodedFormat)
            assertTrue(analysis.servers.isEmpty())
            if (format == "json") {
                assertEquals("JSON без поддерживаемых прокси-серверов (vless/vmess/trojan/ss)", analysis.failureMessage())
            } else {
                assertTrue(analysis.failureMessage().contains("формат: $format,"))
            }
        }
        val rejected = mutableListOf<Pair<String, String>>()
        val analysis = SubscriptionParser.analyze("vless://missing-port\nvless://other") { scheme, type ->
            rejected += scheme to type
        }
        assertEquals("Ссылок разобрано 0 из 2 строк (vless×2)", analysis.failureMessage())
        assertEquals(listOf("vless", "vless"), rejected.map { it.first })
        assertTrue(rejected.all { it.second == "IllegalArgumentException" })
    }

    @Test
    fun plainList() {
        val servers = SubscriptionParser.parse("  $vless\r\n\n$trojan  ")
        assertEquals(listOf(vless, trojan), servers.map { it.rawUrl })
        assertEquals(listOf("VLESS", "TROJAN"), servers.map { it.protocol })
    }

    @Test
    fun preparedPlainAndBase64ListsParseIdentically() {
        val encoder = Base64.getEncoder()
        val vmessJson = """{"add":"example.com","port":443,"id":"uuid","net":"ws"}"""
        val vmess = "vmess://${encoder.encodeToString(vmessJson.toByteArray())}"
        val plain = "  $vless\r\n\t \r\n$vmess\n\n  $trojan  "
        val encoded = encoder.encodeToString(plain.toByteArray())
        for ((format, body) in listOf("links" to plain, "base64" to encoded)) {
            val prepared = SubscriptionParser.prepareBody(body)
            assertNull(SubscriptionParser.isNestedSubscriptionUrl(prepared))
            val analysis = SubscriptionParser.analyze(prepared)
            assertEquals(format, analysis.format)
            assertEquals(3, analysis.lineCount)
            assertEquals(listOf(vless, vmess, trojan), analysis.servers.map { it.rawUrl })
        }
        assertTrue(SubscriptionParser.parse(encoder.encodeToString(encoded.toByteArray())).isEmpty())
    }

    @Test
    fun base64BodyBothAlphabets() {
        // The fragment makes the standard and URL-safe encodings differ (+ versus -).
        val body = "$vless\n$trojan~"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val standard = Base64.getEncoder().encodeToString(bytes)
        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(standard.contains('+') || standard.contains('/'))
        for (encoded in listOf(standard, urlSafe)) {
            val wrapped = encoded.chunked(20).joinToString("\n")
            assertEquals(listOf(vless, "$trojan~"), SubscriptionParser.parse(wrapped).map { it.rawUrl })
        }
    }

    @Test
    fun duplicatesKeepFirstOccurrence() {
        assertEquals(listOf(vless, trojan),
            SubscriptionParser.parse("$vless\n$trojan\n $vless \n$trojan").map { it.rawUrl })
    }

    @Test
    fun garbageLinesAreIgnored() {
        val body = "garbage\nvless://missing-port\n$trojan\nhttps://example.com/list\n# comment"
        assertEquals(listOf(trojan), SubscriptionParser.parse(body).map { it.rawUrl })
        assertTrue(SubscriptionParser.parse("not valid base64!").isEmpty())
        assertTrue(SubscriptionParser.parse("").isEmpty())
    }

    @Test
    fun nestedSubscriptionMustBeSingleHttpLine() {
        val url = "https://example.com/subscription"
        assertEquals(url, SubscriptionParser.isNestedSubscriptionUrl("\uFEFF $url\n"))
        assertEquals(url, SubscriptionParser.isNestedSubscriptionUrl(
            Base64.getUrlEncoder().encodeToString(url.toByteArray(Charsets.UTF_8)),
        ))
        assertNull(SubscriptionParser.isNestedSubscriptionUrl("$url\n$vless"))
        assertNull(SubscriptionParser.isNestedSubscriptionUrl(vless))
    }
}
