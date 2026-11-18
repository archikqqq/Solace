package com.v2nox.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayOutboundsTest {
    @Test fun linkBuildsTheSameVlessRealityOutbound() {
        val config = ServerLinkParser.parse("vless://uuid@example.com:443?type=grpc&security=reality&encryption=none&flow=xtls-rprx-vision&sni=sni.example&fp=chrome&pbk=key&sid=short&spx=%2F&serviceName=svc&authority=host")
        val result = buildXrayOutbounds(config)
        val expected = JSONObject("""{
            "tag":"proxy","protocol":"vless",
            "settings":{"vnext":[{"address":"example.com","port":443,"users":[{
                "id":"uuid","encryption":"none","flow":"xtls-rprx-vision"
            }]}]},
            "streamSettings":{"network":"grpc","security":"reality",
                "realitySettings":{"serverName":"sni.example","fingerprint":"chrome","publicKey":"key","shortId":"short","spiderX":"/"},
                "grpcSettings":{"serviceName":"svc","authority":"host"}}
        }""")
        assertTrue(expected.similar(result.getJSONObject(0)))
        assertDirect(result.getJSONObject(1))
        assertEquals(2, result.length())
    }

    @Test fun otherLinkProtocolsKeepSettingsAndTransport() {
        val vmess = java.util.Base64.getEncoder().encodeToString("""{"add":"example.com","port":443,"id":"uuid","aid":7,"scy":"auto","net":"ws","type":"ws","tls":"tls","host":"cdn.example","path":"/ws"}""".toByteArray())
        val cases = listOf(
            "vmess://$vmess" to "vmess",
            "trojan://password@example.com:443?sni=sni.example" to "trojan",
            "ss://YWVzLTEyOC1nY206cGFzcw==@example.com:8388" to "shadowsocks",
            "wireguard://secret@example.com:51820?address=10.0.0.2%2F32&publickey=pub" to "wireguard",
            "hy2://password@example.com:443" to "hysteria",
        )
        cases.forEach { (link, protocol) ->
            val outbounds = buildXrayOutbounds(ServerLinkParser.parse(link))
            val outbound = outbounds.getJSONObject(0)
            assertEquals(protocol, outbound.getString("protocol"))
            assertEquals("proxy", outbound.getString("tag"))
            assertDirect(outbounds.getJSONObject(1))
            val settings = outbound.getJSONObject("settings")
            when (protocol) {
                "vmess" -> {
                    val user = settings.getJSONArray("vnext").getJSONObject(0).getJSONArray("users").getJSONObject(0)
                    assertEquals(7, user.getInt("alterId"))
                    val stream = outbound.getJSONObject("streamSettings")
                    assertEquals("tls", stream.getString("security"))
                    assertEquals("/ws", stream.getJSONObject("wsSettings").getString("path"))
                }
                "trojan" -> assertEquals("password", settings.getJSONArray("servers").getJSONObject(0).getString("password"))
                "shadowsocks" -> assertEquals("aes-128-gcm", settings.getJSONArray("servers").getJSONObject(0).getString("method"))
                "wireguard" -> assertEquals("example.com:51820", settings.getJSONArray("peers").getJSONObject(0).getString("endpoint"))
                "hysteria" -> assertEquals("password", outbound.getJSONObject("streamSettings").getJSONObject("hysteriaSettings").getString("auth"))
            }
        }
    }

    @Test fun jsonOutboundsArePreservedIncludingUnknownFieldsAndExtra() {
        val bundle = JSONObject("""{
            "outbound":{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443,"users":[{"id":"uuid"}]}]},
                "streamSettings":{"network":"xhttp","security":"reality","xhttpSettings":{"path":"/raw","extra":{"scMaxEachPostBytes":1000000}},"sockopt":{"dialerProxy":"hop"}},"mux":{"enabled":true}},
            "extra":[{"tag":"hop","protocol":"socks","settings":{"servers":[{"address":"hop.example","port":1080}]},"custom":[1,true,"keep"]}]
        }""")
        val config = ServerConfig("id", "JSON", "example.com", 443, rawUrl = "invalid link must not be parsed", outboundJson = bundle.toString())
        val result = buildXrayOutbounds(config)
        assertTrue(bundle.getJSONObject("outbound").similar(result.getJSONObject(0)))
        assertTrue(bundle.getJSONArray("extra").getJSONObject(0).similar(result.getJSONObject(1)))
        assertDirect(result.getJSONObject(2))
        assertEquals(3, result.length())
        assertEquals(bundle.toString(), config.outboundJson)
    }

    @Test fun subscriptionVlessRealityXhttpBuildsNativeMasks() {
        val noises = """[{"type":"rand","packet":"5-10","delay":"2"},{"type":"base64","packet":"_w"}]"""
        fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        val server = SubscriptionParser.parse("vless://uuid@example.org:443?security=reality&pbk=key" +
            "&type=xhttp&path=%2Ftest&host=cdn.example&mode=stream-one&extra=${encode("""{"xPaddingBytes":"10-20"}""")}" +
            "&fragment=tlshello&length=50-100&interval=1-3&noises=${encode(noises)}").single()
        val stream = buildXrayOutbounds(server).getJSONObject(0).getJSONObject("streamSettings")
        val xhttp = stream.getJSONObject("xhttpSettings")
        assertEquals("/test", xhttp.getString("path"))
        assertEquals("stream-one", xhttp.getString("mode"))
        assertEquals("10-20", xhttp.getJSONObject("extra").getString("xPaddingBytes"))
        val mask = stream.getJSONObject("finalmask")
        val fragment = mask.getJSONArray("tcp").getJSONObject(0).getJSONObject("settings")
        assertEquals("tlshello", fragment.getString("packets"))
        assertEquals("50-100", fragment.getString("length"))
        assertEquals("1-3", fragment.getString("delay"))
        val noise = mask.getJSONArray("udp").getJSONObject(0).getJSONObject("settings").getJSONArray("noise")
        assertEquals("5-10", noise.getJSONObject(0).getString("rand"))
        assertEquals("/w==", noise.getJSONObject(1).getString("packet"))
    }

    @Test fun reservedTagsAndDialerReferencesAreRenamedWithoutNewCollisions() {
        val bundle = JSONObject().put("outbound", hop("original", "direct"))
            .put("extra", JSONArray()
                .put(hop("direct", "proxy"))
                .put(hop("proxy", "tun-in"))
                .put(hop("tun-in", "original"))
                .put(hop("extra-0", null)))
        val config = ServerConfig("id", "JSON", "example.com", 443, outboundJson = bundle.toString())
        val result = buildXrayOutbounds(config)
        val tags = (0 until result.length()).map { result.getJSONObject(it).getString("tag") }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals("proxy", tags[0])
        assertEquals("extra-0", tags[4])
        assertEquals("direct", tags.last())
        for (index in 0..3) {
            val expected = if (index == 3) "proxy" else tags[index + 1]
            assertEquals(expected, result.getJSONObject(index).getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        }
        assertTrue(buildXrayOutbounds(config).similar(result))
    }

    private fun hop(tag: String, target: String?): JSONObject = JSONObject()
        .put("tag", tag).put("protocol", "freedom").apply {
            target?.let { put("streamSettings", JSONObject().put("sockopt", JSONObject().put("dialerProxy", it))) }
        }

    private fun assertDirect(outbound: JSONObject) {
        assertTrue(JSONObject("""{"tag":"direct","protocol":"freedom"}""").similar(outbound))
    }

    // Android's compile-time org.json API does not expose similar().
    private fun JSONObject.similar(other: JSONObject): Boolean = normalized(this) == normalized(other)
    private fun JSONArray.similar(other: JSONArray): Boolean = normalized(this) == normalized(other)

    private fun normalized(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { normalized(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { normalized(value.get(it)) }
        else -> value
    }
}
