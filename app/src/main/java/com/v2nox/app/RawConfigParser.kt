package com.v2nox.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/** Reads full client configs without rebuilding or dropping outbound settings. */
object RawConfigParser {
    private val proxyProtocols = setOf("vless", "vmess", "trojan", "shadowsocks")

    /** Null means a different dialect; an empty list means no supported proxies. */
    fun parse(body: String): List<ServerConfig>? {
        val text = body.trim().removePrefix("\uFEFF").trim()
        if (!text.startsWith("[") && !text.startsWith("{")) return null
        val root = runCatching {
            val tokener = JSONTokener(text)
            tokener.nextValue().also { require(tokener.nextClean() == '\u0000') }
        }.getOrNull() ?: return null
        val configs = when (root) {
            is JSONObject -> listOf(root)
            is JSONArray -> root.objects()
            else -> return null
        }
        if (configs.none { it.optJSONArray("outbounds") != null }) return null

        return configs.flatMap { config ->
            val outbounds = config.optJSONArray("outbounds")?.objects().orEmpty()
            val proxies = outbounds.filter { it.optString("protocol") in proxyProtocols }
            val remarks = config.optString("remarks", "").trim()
            val fullConfig = config.keys().asSequence().any { it !in setOf("remarks", "outbounds") }
            val profiles = proxies.asSequence().mapNotNull { source ->
                val endpoint = source.proxyEndpoint() ?: return@mapNotNull null
                val address = endpoint.optString("address", "")
                val port = endpoint.optInt("port", 0)
                if (address.isBlank() || port !in 1..65535) return@mapNotNull null
                val tag = source.optString("tag", "")
                val name = when {
                    !fullConfig && proxies.size > 1 && remarks.isNotEmpty() && tag.isNotEmpty() -> "$remarks · $tag"
                    remarks.isNotEmpty() -> remarks
                    tag.isNotEmpty() -> tag
                    else -> address
                }
                val outbound = JSONObject(source.toString()).put("tag", "proxy")
                val extra = JSONArray()
                val visited = mutableSetOf(tag)
                var dependency = if (fullConfig) null else source.dialerProxy()
                while (!dependency.isNullOrBlank() && visited.add(dependency)) {
                    val linked = outbounds.firstOrNull { it.optString("tag") == dependency } ?: break
                    extra.put(JSONObject(linked.toString()))
                    dependency = linked.dialerProxy()
                }
                ServerConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    address = address,
                    port = port,
                    protocol = source.getString("protocol").uppercase(),
                    security = source.optJSONObject("streamSettings")
                        ?.optString("security", "")?.takeIf(String::isNotBlank),
                    outboundJson = JSONObject().put("outbound", outbound).put("extra", extra).toString(),
                    fullConfigJson = if (fullConfig) config.toString() else null,
                )
            }
            (if (fullConfig) profiles.take(1) else profiles).toList()
        }.distinctBy { it.fullConfigJson ?: it.identityKey }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.dialerProxy(): String? =
        optJSONObject("streamSettings")?.optJSONObject("sockopt")?.optString("dialerProxy")
}

internal fun JSONObject.proxyEndpoint(): JSONObject? {
    val settings = optJSONObject("settings") ?: this
    return settings.optJSONArray("vnext")?.optJSONObject(0)
        ?: settings.optJSONArray("servers")?.optJSONObject(0)
        ?: settings
}
