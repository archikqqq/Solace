package com.v2nox.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RawConfigParserTest {
    private fun assertJsonEquals(expected: Any, actual: Any) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                val keys = expected.keys().asSequence().toSet()
                assertEquals(keys, actual.keys().asSequence().toSet())
                keys.forEach { assertJsonEquals(expected.get(it), actual.get(it)) }
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals(expected.length(), actual.length())
                (0 until expected.length()).forEach { assertJsonEquals(expected.get(it), actual.get(it)) }
            }
            else -> assertEquals(expected, actual)
        }
    }

    private fun vless(tag: String = "original", host: String = "example.com") = JSONObject("""
        {
          "tag":"$tag", "protocol":"vless",
          "settings":{"vnext":[{"address":"$host","port":443,
            "users":[{"id":"uuid-1","encryption":"none","flow":"xtls-rprx-vision"}]}]},
          "streamSettings":{"network":"xhttp","security":"reality",
            "realitySettings":{"serverName":"example.org","publicKey":"key","shortId":"ab","fingerprint":"chrome"},
            "xhttpSettings":{"path":"/test","extra":{"mode":"auto","custom":[1,true]}},
            "sockopt":{"tcpFastOpen":true}},
          "mux":{"enabled":true,"concurrency":8}, "customField":{"keep":true}
        }
    """.trimIndent())

    private fun config(name: String, vararg outbounds: JSONObject) = JSONObject()
        .put("remarks", name).put("outbounds", JSONArray(outbounds.toList()))

    @Test
    fun arrayKeepsAggregatorSeparateFromStandaloneEndpoints() {
        val first = vless()
        val second = vless(host = "second.example")
        val aggregator = config("Auto", vless("auto-1"), vless("auto-2", "second.example"))
            .put("routing", JSONObject("""{"balancers":[{"tag":"auto","selector":["auto-"]}]}"""))
        val body = JSONArray().put(config("First", first)).put(config("Second", second))
            .put(aggregator).toString()
        val servers = RawConfigParser.parse(body)!!
        assertEquals(listOf("First", "Second", "Auto"), servers.map { it.name })
        assertEquals(listOf("example.com", "second.example", "example.com"), servers.map { it.address })
        assertEquals("vless|example.com|443|uuid-1", servers.first().identityKey)
        assertTrue(servers.all { it.rawUrl == null })
        assertJsonEquals(aggregator, JSONObject(servers.last().fullConfigJson!!))
        assertEquals(servers.map { it.identityKey }, SubscriptionParser.parse(body).map { it.identityKey })
    }

    @Test
    fun realityOutboundIsPreservedExceptMainTag() {
        val original = vless()
        val server = RawConfigParser.parse(config("Reality server", original).toString())!!.single()
        val stored = JSONObject(server.outboundJson!!)
        val expected = JSONObject(original.toString()).put("tag", "proxy")
        assertJsonEquals(expected, stored.getJSONObject("outbound"))
        assertEquals("original", original.getString("tag"))
        assertEquals(0, stored.getJSONArray("extra").length())
        assertEquals("Reality server", server.name)
        assertEquals("VLESS", server.protocol)
        assertEquals("reality", server.security)
        assertEquals(443, server.port)
    }

    @Test
    fun multipleProxiesUseRemarksAndOriginalTag() {
        val servers = RawConfigParser.parse(config("Group", vless("a"), vless("b", "other.example")).toString())!!
        assertEquals(listOf("Group · a", "Group · b"), servers.map { it.name })
    }

    @Test
    fun dialerProxyCarriesRelatedOutboundsButNotUnrelatedPlumbing() {
        val proxy = vless()
        proxy.getJSONObject("streamSettings").getJSONObject("sockopt").put("dialerProxy", "fragment")
        val fragment = JSONObject("""{"tag":"fragment","protocol":"freedom",
            "settings":{"fragment":{"packets":"tlshello","length":"100-200","interval":"10-20"}}}""")
        val body = config("Fragmented", proxy, fragment,
            JSONObject("""{"tag":"block","protocol":"blackhole"}""")).toString()
        val server = RawConfigParser.parse(body)!!.single()
        val stored = JSONObject(server.outboundJson!!)
        assertEquals(1, stored.getJSONArray("extra").length())
        assertJsonEquals(fragment, stored.getJSONArray("extra").getJSONObject(0))
        assertEquals("fragment", stored.getJSONObject("outbound").getJSONObject("streamSettings")
            .getJSONObject("sockopt").getString("dialerProxy"))
    }

    @Test
    fun dependencyCyclesTerminate() {
        val proxy = vless()
        proxy.getJSONObject("streamSettings").getJSONObject("sockopt").put("dialerProxy", "fragment")
        val fragment = JSONObject("""{"tag":"fragment","protocol":"freedom",
            "streamSettings":{"sockopt":{"dialerProxy":"fragment"}}}""")
        val server = RawConfigParser.parse(config("Cycle", proxy, fragment).toString())!!.single()
        assertEquals(1, JSONObject(server.outboundJson!!).getJSONArray("extra").length())
    }

    @Test
    fun flatAndNestedSettingsSupportAllFourProtocols() {
        val vmess = JSONObject("""{"protocol":"vmess","settings":{"address":"vm.example","port":80,"id":"vm-id"}}""")
        val trojan = JSONObject("""{"protocol":"trojan","settings":{"servers":[{"address":"tr.example","port":443,"password":"secret"}]}}""")
        val ss = JSONObject("""{"protocol":"shadowsocks","settings":{"address":"ss.example","port":8388,"password":"ss-pass","method":"aes-128-gcm"}}""")
        val servers = RawConfigParser.parse(config("All", vless(), vmess, trojan, ss).toString())!!
        assertEquals(listOf("VLESS", "VMESS", "TROJAN", "SHADOWSOCKS"), servers.map { it.protocol })
        assertEquals(listOf("vless|example.com|443|uuid-1", "vmess|vm.example|80|vm-id",
            "trojan|tr.example|443|secret", "shadowsocks|ss.example|8388|ss-pass"), servers.map { it.identityKey })
        assertNotEquals(servers[0].identityKey, servers[1].identityKey)
    }

    @Test
    fun garbageJsonIsNotAConfig() {
        for (body in listOf("garbage", "{broken", "[", "{}", "[]", "[1,null,{}]", "{\"remarks\":\"test\"}")) {
            assertNull(body, RawConfigParser.parse(body))
        }
    }

    @Test
    fun unsupportedAndInvalidEndpointsProduceEmptyList() {
        val body = """{"outbounds":[{"protocol":"freedom"},{"protocol":"blackhole"},
            {"protocol":"dns"},{"protocol":"selector"},{"protocol":"vless","settings":{"address":"","port":443}}]}"""
        assertEquals(emptyList<ServerConfig>(), RawConfigParser.parse(body))
        assertEquals("JSON без поддерживаемых прокси-серверов (vless/vmess/trojan/ss)",
            SubscriptionParser.analyze(body).failureMessage())
    }

    @Test
    fun base64ConfigIsDecodedBeforeParsing() {
        val body = config("Encoded", vless()).toString()
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray())
        assertEquals("Encoded", SubscriptionParser.parse(encoded).single().name)
        assertEquals("base64", SubscriptionParser.analyze(encoded).format)
    }

    @Test
    fun rawUrlStillHasIdentityPriority() {
        val server = RawConfigParser.parse(config("Test", vless()).toString())!!.single()
        assertEquals("vless://original", server.copy(rawUrl = "vless://original").identityKey)
        assertEquals(server.identityKey, server.copy(id = "new-id", name = "Renamed").identityKey)
    }
}
