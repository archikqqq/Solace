package com.v2nox.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.SecureRandom

internal class VpnTunnelConfig private constructor(
    val socks: LocalSocks,
    private val rawConfig: JSONObject?,
) {
    fun xrayConfig(config: ServerConfig): String = rawConfig?.toString() ?: JSONObject().apply {
        put("log", JSONObject().put("loglevel", "warning"))
        val account = JSONObject().put("user", socks.username).put("pass", socks.password)
        val settings = JSONObject()
            .put("auth", "password")
            .put("accounts", JSONArray().put(account))
            .put("udp", true)
            .put("ip", LOOPBACK)
        val inbound = JSONObject()
            .put("tag", "tun-in")
            .put("listen", LOOPBACK)
            .put("port", socks.port)
            .put("protocol", "socks")
            .put("settings", settings)
            .put("sniffing", JSONObject().put("enabled", true).put("routeOnly", true)
                .put("destOverride", JSONArray(listOf("http", "tls", "quic"))))
        put("inbounds", JSONArray().put(inbound))
        val directDomains = JSONArray().put("geosite:private")
        put("dns", JSONObject().put("disableFallbackIfMatch", true).put("servers", JSONArray()
            .put(JSONObject().put("address", "https://$DNS_IPV4/dns-query"))
            .put(JSONObject().put("address", "8.8.8.8").put("tag", "dns-domestic")
                .put("domains", directDomains).put("skipFallback", true))))
        val outbounds = buildXrayOutbounds(config)
        put("outbounds", outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns")
            .put("settings", JSONObject().put("rules", JSONArray()
                .put(JSONObject().put("action", "hijack").put("qType", "1,28"))
                .put(JSONObject().put("action", "return").put("rCode", 5))))))
        fun rule(tag: String) = JSONObject().put("type", "field").put("outboundTag", tag)
        put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", JSONArray()
            .put(rule("dns-out").put("inboundTag", JSONArray().put("tun-in")).put("port", 53))
            .put(rule("direct").put("inboundTag", JSONArray().put("dns-domestic")))
            .put(rule("proxy").put("ip", JSONArray().put(DNS_IPV4)).put("port", 443))
            .put(rule("direct").put("domain", directDomains))
            .put(rule("direct").put("ip", JSONArray().put("geoip:private")))))
    }.toString()

    fun hevConfig(): String = """
        tunnel:
          mtu: $MTU
          ipv4: '$TUN_IPV4'
          ipv6: '$TUN_IPV6'
        socks5:
          address: '${socks.host}'
          port: ${socks.port}
          udp: 'udp'
        misc:
          log-level: error
    """.trimIndent().let { yaml ->
        val user = socks.username ?: return@let yaml
        fun quote(value: String) = "'${value.replace("'", "''")}'"
        yaml.replace("\nmisc:", "\n  username: ${quote(user)}\n  password: ${quote(socks.password!!)}\nmisc:")
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private val localHosts = setOf(LOOPBACK, "::1")
        const val MTU = 1500
        const val TUN_IPV4 = "198.18.0.1"
        const val TUN_IPV6 = "fd00:198:18::1"
        const val DNS_IPV4 = "1.1.1.1"
        private val random = SecureRandom()
        private const val HEX = "0123456789abcdef"

        fun create(config: ServerConfig? = null): VpnTunnelConfig {
            val raw = config?.fullConfigJson?.let(::JSONObject)
            val inbound = raw?.let(::localInbound)
            val host = inbound?.getString("listen") ?: LOOPBACK
            val settings = inbound?.optJSONObject("settings")
            val account = when (settings?.optString("auth").orEmpty()) {
                "", "noauth" -> null
                "password" -> (settings?.optJSONArray("accounts") ?: settings?.optJSONArray("users"))
                    ?.optJSONObject(0) ?: error("В SOCKS5-конфигурации отсутствуют учётные данные")
                else -> error("Неподдерживаемая авторизация SOCKS5")
            }
            val user = if (raw == null) token() else account?.getString("user")
            val pass = if (raw == null) token() else account?.getString("pass")
            require(listOfNotNull(user, pass).all {
                it.toByteArray(Charsets.UTF_8).size in 1..255 && it.none { c -> c.isISOControl() }
            }) { "Неподдерживаемые учётные данные SOCKS5" }
            val port = ServerSocket().use {
                it.bind(InetSocketAddress(host, 0))
                it.localPort
            }
            inbound?.put("port", port)
            return VpnTunnelConfig(LocalSocks(host, port, user, pass), raw)
        }

        private fun localInbound(raw: JSONObject): JSONObject {
            val api = raw.optJSONObject("api")
            val services = api?.optJSONArray("services")
            require(services == null || (0 until services.length()).none {
                services.optString(it).equals("HandlerService", ignoreCase = true)
            }) { "Импортированный профиль не должен включать HandlerService" }
            val apiListen = api?.optString("listen").orEmpty()
            require(apiListen.isEmpty() || apiListen.startsWith("$LOOPBACK:") || apiListen.startsWith("[::1]:")) {
                "API импортированного профиля должен слушать loopback"
            }
            val inbounds = raw.optJSONArray("inbounds") ?: error("В JSON-профиле отсутствует локальный SOCKS5 inbound")
            val listeners = (0 until inbounds.length()).map(inbounds::getJSONObject)
            require(listeners.all { it.optString("listen") in localHosts }) {
                "Входящие подключения JSON-профиля должны слушать 127.0.0.1 или ::1"
            }
            return listeners.filter { it.optString("protocol") in setOf("socks", "mixed") }
                .sortedWith(compareBy({ it.optJSONObject("settings")?.optBoolean("udp") != true },
                    { it.optString("listen") != LOOPBACK }, { it.optString("protocol") != "socks" }))
                .firstOrNull() ?: error("В JSON-профиле отсутствует локальный SOCKS5 inbound")
        }

        private fun token(): String = buildString {
            val bytes = ByteArray(16).also(random::nextBytes)
            for (byte in bytes) {
                append(HEX[(byte.toInt() ushr 4) and 15])
                append(HEX[byte.toInt() and 15])
            }
        }
    }
}
