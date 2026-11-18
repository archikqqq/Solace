package com.v2nox.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTunnelConfigTest {
    private val profile = ServerConfig("id", "JSON", "example.com", 443, outboundJson = """
        {
          "outbound": {"tag":"proxy","protocol":"freedom","streamSettings":{"network":"grpc"}},
          "api": {"tag":"api","services":["HandlerService"]},
          "inbounds": [{"protocol":"socks","port":1080,"settings":{"auth":"noauth"}}]
        }
    """.trimIndent())

    @Test fun tunnelOwnsIngressAndDoesNotImportTheManagementApi() {
        val session = VpnTunnelConfig.create()
        val xray = JSONObject(session.xrayConfig(profile))
        val inbounds = xray.getJSONArray("inbounds")
        val inbound = inbounds.getJSONObject(0)
        val settings = inbound.getJSONObject("settings")
        val account = settings.getJSONArray("accounts").getJSONObject(0)
        val hev = session.hevConfig()

        assertEquals(1, inbounds.length())
        assertEquals("socks", inbound.getString("protocol"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertTrue(inbound.getInt("port") in 1..65535)
        assertEquals("password", settings.getString("auth"))
        assertTrue(settings.getBoolean("udp"))
        assertFalse(xray.has("api"))
        assertEquals("grpc", xray.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getString("network"))
        assertTrue(hev.contains("  port: ${inbound.getInt("port")}\n"))
        assertTrue(hev.contains("  username: '${account.getString("user")}'\n"))
        assertTrue(hev.contains("  password: '${account.getString("pass")}'\n"))
    }

    @Test fun credentialsRotateEachSession() {
        fun account() = JSONObject(VpnTunnelConfig.create().xrayConfig(profile))
            .getJSONArray("inbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("accounts").getJSONObject(0)

        val first = account()
        val second = account()
        for (key in listOf("user", "pass")) {
            assertTrue(first.getString(key).matches(Regex("[0-9a-f]{32}")))
            assertNotEquals(first.getString(key), second.getString(key))
        }
    }

    @Test fun fullSubscriptionConfigChangesOnlySocksPort() {
        val json = """{
          "remarks":"Balancer",
          "inbounds":[{"protocol":"http","listen":"127.0.0.1","port":8080},
            {"protocol":"socks","listen":"127.0.0.1","port":1080,"tag":"in","settings":{
              "auth":"password","accounts":[{"user":"client's","pass":"quoted"}],"udp":true}}],
          "dns":{"servers":["8.8.8.8"]},
          "routing":{"balancers":[{"tag":"auto","selector":["node-"]}],
            "rules":[{"inboundTag":["in"],"balancerTag":"auto"}]},
          "outbounds":[{"protocol":"trojan","tag":"node-a",
            "settings":{"address":"a.example","port":443,"password":"secret"}},
            {"protocol":"blackhole","tag":"blocked"}]
        }"""
        val profile = SubscriptionParser.parse(json).single()
        val session = VpnTunnelConfig.create(profile)
        val expected = JSONObject(json)
        expected.getJSONArray("inbounds").getJSONObject(1).put("port", session.socks.port)
        assertEquals(normalize(expected), normalize(JSONObject(session.xrayConfig(profile))))
        assertEquals(1080, JSONObject(profile.fullConfigJson!!).getJSONArray("inbounds").getJSONObject(1).getInt("port"))
        assertTrue(session.hevConfig().contains("username: 'client''s'"))
    }

    @Test fun rawNoAuthDoesNotGenerateCredentials() {
        val raw = profile.copy(fullConfigJson = """{"inbounds":[{
            "protocol":"socks","listen":"127.0.0.1","port":1080,"settings":{"udp":true}}]}""")
        val session = VpnTunnelConfig.create(raw)
        assertFalse(session.hevConfig().contains("username:"))
        assertFalse(session.hevConfig().contains("password:"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWildcardListenerInRawConfig() {
        VpnTunnelConfig.create(profile.copy(fullConfigJson = """{"inbounds":[{
            "protocol":"socks","listen":"0.0.0.0","port":1080}]}"""))
    }

    @Test fun generatedConfigInterceptsDnsAndSeparatesResolvers() {
        val link = ServerLinkParser.parse("trojan://secret@example.org:443")
        val xray = JSONObject(VpnTunnelConfig.create(link).xrayConfig(link))
        val sniffing = xray.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing")
        assertTrue(sniffing.getBoolean("enabled"))
        assertEquals("[\"http\",\"tls\",\"quic\"]", sniffing.getJSONArray("destOverride").toString())
        val dns = xray.getJSONObject("dns").getJSONArray("servers")
        assertEquals("https://1.1.1.1/dns-query", dns.getJSONObject(0).getString("address"))
        assertEquals("8.8.8.8", dns.getJSONObject(1).getString("address"))
        assertTrue(dns.getJSONObject(1).getBoolean("skipFallback"))
        val routing = xray.getJSONObject("routing")
        assertEquals("IPIfNonMatch", routing.getString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals(53, rules.getJSONObject(0).getInt("port"))
        assertEquals("direct", rules.getJSONObject(1).getString("outboundTag"))
        assertEquals("proxy", rules.getJSONObject(2).getString("outboundTag"))
        assertEquals("proxy", xray.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { normalize(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { normalize(value.get(it)) }
        else -> value
    }
}
