package com.v2nox.app

import org.junit.Assert.*
import org.junit.Test

class ProfileImportTest {
    @Test fun importsMultipleJsonServersWithDifferentIdentitiesAndNoLinks() {
        val configs = parseProfileImport("""{"outbounds":[
            {"tag":"a","protocol":"trojan","settings":{"servers":[{"address":"a.example","port":443,"password":"one"}]}},
            {"tag":"b","protocol":"trojan","settings":{"servers":[{"address":"b.example","port":443,"password":"two"}]}}
        ]}""")
        assertEquals(2, configs.size)
        assertTrue(configs.all { it.rawUrl == null })
        assertEquals(2, configs.map { it.identityKey }.toSet().size)
        val renamed = configs.first().copy(name = "Renamed")
        assertEquals(configs.first().identityKey, renamed.identityKey)
        assertEquals(configs.first().outboundJson, renamed.outboundJson)
    }

    @Test fun linkImportKeepsIdentity() {
        val link = "vless://uuid@example.com:443"
        assertEquals(link, parseProfileImport(link).single().identityKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyJsonDoesNotImportAnEmptyProfile() {
        parseProfileImport("""{"outbounds":[]}""")
    }

    @Test fun plainHttpAndHttpsUrlsAreSubscriptions() {
        assertTrue(isSubscriptionUrl("https://example.com/sub"))
        assertTrue(isSubscriptionUrl("http://example.com/sub?token=abc"))
        assertTrue(isSubscriptionUrl("  https://example.com/sub \n"))
        assertTrue(isSubscriptionUrl("HTTPS://example.com/sub"))
    }

    @Test fun textWithInnerWhitespaceIsNotSubscriptionUrl() {
        assertFalse(isSubscriptionUrl("https://example.com/sub\nvless://uuid@example.com:443"))
        assertFalse(isSubscriptionUrl("https://example.com/sub extra"))
    }

    @Test fun otherSchemesAreNotSubscriptionUrls() {
        assertFalse(isSubscriptionUrl("vless://uuid@example.com:443"))
        assertFalse(isSubscriptionUrl("ftp://example.com/sub"))
        assertFalse(isSubscriptionUrl("example.com/sub"))
        assertFalse(isSubscriptionUrl(""))
    }
}
