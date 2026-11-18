// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SubscriptionHeadersTest {
    @Test
    fun userinfoParsesCountersCaseInsensitively() {
        val metadata = SubscriptionHeaders.parse(mapOf(
            "Subscription-Userinfo" to listOf("upload=123; DOWNLOAD=456; total=9876543210; expire=1700000000"),
        ))
        assertEquals(123L, metadata.uploadBytes)
        assertEquals(456L, metadata.downloadBytes)
        assertEquals(9876543210L, metadata.totalBytes)
        assertEquals(1700000000L, metadata.expireEpochSeconds)
    }

    @Test
    fun invalidCountersAreIgnored() {
        val metadata = SubscriptionHeaders.parseUserinfo("upload=no; download=-1; total=99999999999999999999; unknown=4")
        assertEquals(SubscriptionMetadata(), metadata)
    }

    @Test
    fun base64TitlesSupportBothAlphabetsWithAndWithoutPadding() {
        val title = "Подписка \uFFFF"
        for (encoder in listOf(Base64.getEncoder(), Base64.getUrlEncoder())) {
            for (variant in listOf(encoder, encoder.withoutPadding())) {
                val encoded = variant.encodeToString(title.toByteArray(Charsets.UTF_8))
                val metadata = SubscriptionHeaders.parse(mapOf("profile-title" to listOf("base64:$encoded")))
                assertEquals(title, metadata.title)
            }
        }
        assertNull(SubscriptionHeaders.parseTitle("base64:%%%"))
    }

    @Test
    fun plainAndUrlEncodedTitles() {
        assertEquals("My VPN+", SubscriptionHeaders.parseTitle(" My VPN+ "))
        assertEquals("Мой VPN", SubscriptionHeaders.parseTitle("%D0%9C%D0%BE%D0%B9%20VPN"))
        assertEquals("100% VPN", SubscriptionHeaders.parseTitle("100% VPN"))
    }

    @Test
    fun filenameIsFallbackForTitle() {
        assertEquals("My VPN", SubscriptionHeaders.parse(mapOf(
            "Content-Disposition" to listOf("attachment; filename=\"My VPN.txt\""),
        )).title)
        assertEquals("Servers", SubscriptionHeaders.parseFilename("attachment; filename=Servers.list"))
        assertEquals("My VPN", SubscriptionHeaders.parseFilename("attachment; filename*=UTF-8''My%20VPN.txt"))
        assertEquals("Preferred", SubscriptionHeaders.parse(mapOf(
            "profile-title" to listOf("Preferred"),
            "content-disposition" to listOf("attachment; filename=Other.txt"),
        )).title)
        assertEquals("Fallback", SubscriptionHeaders.parse(mapOf(
            "profile-title" to listOf("base64:%%%"),
            "content-disposition" to listOf("attachment; filename=Fallback.txt"),
        )).title)
    }

    @Test
    fun intervalIsPositiveHours() {
        assertEquals(12, SubscriptionHeaders.parse(mapOf("Profile-Update-Interval" to listOf("12"))).updateIntervalHours)
        assertEquals(1, SubscriptionHeaders.parseUpdateInterval("0.5"))
        assertEquals(2, SubscriptionHeaders.parseUpdateInterval("2.5; extra"))
        for (invalid in listOf("0", "-2", "bad", "NaN", "Infinity")) {
            assertNull(SubscriptionHeaders.parseUpdateInterval(invalid))
        }
    }
}
