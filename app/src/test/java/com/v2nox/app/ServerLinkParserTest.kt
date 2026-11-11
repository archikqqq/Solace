package com.v2nox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerLinkParserTest {
    @Test
    fun parsesVlessProfileAndScheme() {
        val profile = ServerLinkParser.parse("vless://9f3a@edge.example:443?security=tls#Primary")

        assertEquals("VLESS", profile.protocol)
        assertEquals("edge.example", profile.address)
        assertEquals(443, profile.port)
        assertEquals("Primary", profile.name)
    }

    @Test
    fun parsesTrojanProfileAndPassword() {
        val profile = ServerLinkParser.parse("trojan://secret@edge.example:8443#Secure")
        val parts = ServerLinkParser.parseRawLink(profile.rawUrl!!)

        assertEquals("TROJAN", profile.protocol)
        assertEquals("secret", parts.username)
        assertEquals(8443, profile.port)
    }

    @Test
    fun parsesShadowsocksMethodAndPassword() {
        val profile = ServerLinkParser.parse("ss://aes-128-gcm:secret@edge.example:8388#Fast")
        val parts = ServerLinkParser.parseRawLink(profile.rawUrl!!)

        assertEquals("SHADOWSOCKS", profile.protocol)
        assertEquals("aes-128-gcm", parts.values["method"])
        assertEquals("secret", parts.password)
    }
}
