package com.v2nox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileBadgesTest {
    private fun config(raw: String? = null, security: String? = null) =
        ServerConfig("test", "Profile", "example.com", 443, rawUrl = raw, security = security)

    @Test fun readsTransportAndSecurityFromExistingParser() {
        assertEquals(listOf("WS", "TLS"), profileBadges(config("vless://id@example.com:443?type=ws&security=tls")))
    }

    @Test fun storedSecurityTakesPrecedence() {
        assertEquals(listOf("GRPC", "REALITY"), profileBadges(config("vless://id@example.com:443?type=grpc&security=tls", "reality")))
    }

    @Test fun missingMetadataDoesNotInventTransport() {
        assertEquals(emptyList<String>(), profileBadges(config()))
    }

    @Test fun malformedLinkDoesNotCrashCard() {
        assertEquals(listOf("TLS"), profileBadges(config("not a server link", "tls")))
    }

    @Test fun noneIsNotShownAsSecurityBadge() {
        assertEquals(listOf("TCP"), profileBadges(config("vless://id@example.com:443?type=tcp&security=none")))
    }

    @Test fun jsonMetadataDoesNotNeedAShareLink() {
        val server = config().copy(outboundJson = """{"outbound":{"streamSettings":{"network":"xhttp","security":"reality"}}}""")
        assertEquals(listOf("XHTTP", "REALITY"), profileBadges(server))
    }

    @Test fun malformedJsonDoesNotCrashCard() {
        assertEquals(listOf("TLS"), profileBadges(config(security = "tls").copy(outboundJson = "broken")))
    }
}
