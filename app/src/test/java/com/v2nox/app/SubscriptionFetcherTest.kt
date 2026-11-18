package com.v2nox.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {
    @Test
    fun selevkRequestUsesExactHappUserAgent() = runBlocking {
        val fingerprint = HappSpoof.Fingerprint("3.21.1", "Android", "16", "Pixel 7 Pro", "en", "123", "abcdef")
        val fetcher = SubscriptionFetcher { fingerprint }
        assertEquals("Happ/1.0", fetcher.buildRequest("https://selevk.com/a/test").header("User-Agent"))
        assertEquals("Happ/3.21.1/Android/123", fetcher.buildRequest("https://example.com/sub").header("User-Agent"))
    }

    @Test
    fun gzipBodyIsUnpackedBeforeParsing() {
        val body = "vless://user@example.com:443#🇩🇪 Germany | Fast"
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val decoded = SubscriptionFetcher.readBody(ByteArrayInputStream(compressed), "GZip")
            .toString(Charsets.UTF_8)
        assertEquals(body, decoded)
        assertEquals("🇩🇪 Germany | Fast", SubscriptionParser.parse(decoded).single().name)
        assertEquals(body, SubscriptionFetcher.readBody(ByteArrayInputStream(body.toByteArray()), null).toString(Charsets.UTF_8))
    }

    @Test
    fun urlsAndTimeouts() {
        val fetcher = SubscriptionFetcher { null }
        assertEquals("https://example.com/sub?token=secret", fetcher.normalizeUrl(" example.com/sub?token=secret "))
        assertEquals("https://example.com/sub", fetcher.normalizeUrl("//example.com/sub"))
        assertEquals("http://example.com/sub", fetcher.normalizeUrl("HTTP://example.com/sub"))
        assertEquals("example.com", fetcher.hostNameOf("https://example.com/sub?token=secret"))
        assertEquals("unknown", fetcher.hostNameOf("http://"))
        assertEquals("Таймаут соединения", SubscriptionFetcher.networkErrorMessage(SocketTimeoutException()))
    }

    @Test
    fun spoofHeadersMatchChumpy() {
        val fingerprint = HappSpoof.Fingerprint("3.21.1", "Android", "16", "Pixel 7 Pro", "en", "123", "abcdef")
        assertEquals(mapOf(
            "User-Agent" to "Happ/3.21.1/Android/123",
            "x-device-locale" to "en",
            "x-hwid" to "abcdef",
            "x-device-os" to "Android",
            "x-ver-os" to "16",
            "x-device-model" to "Pixel 7 Pro",
            "accept-encoding" to "gzip",
        ), HappSpoof.headers(fingerprint))
        assertTrue(HappSpoof.headers(null).containsKey("Accept"))
    }
}
