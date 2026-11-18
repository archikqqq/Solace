package com.v2nox.app

import org.json.JSONObject

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STOPPING,
}

/** Remove this declaration if the project already provides ServerConfig in another package. */
data class ServerConfig(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val rawUrl: String? = null,
    val security: String? = null,
    val protocol: String = "VLESS",
    val subscriptionId: String? = null,
    val outboundJson: String? = null,
    val fullConfigJson: String? = null,
) {
    val identityKey: String
        get() = rawUrl ?: run {
            val outbound = runCatching {
                JSONObject(outboundJson.orEmpty()).optJSONObject("outbound")
            }.getOrNull()
            val endpoint = outbound?.proxyEndpoint()
            val settings = outbound?.optJSONObject("settings")
            val user = endpoint?.optJSONArray("users")?.optJSONObject(0)
            val credential = user?.optString("id")?.takeIf(String::isNotEmpty)
                ?: endpoint?.optString("id")?.takeIf(String::isNotEmpty)
                ?: settings?.optString("id")?.takeIf(String::isNotEmpty)
                ?: endpoint?.optString("password")?.takeIf(String::isNotEmpty)
                ?: settings?.optString("password").orEmpty()
            val host = endpoint?.optString("address")?.ifBlank { null } ?: address
            val endpointPort = endpoint?.optInt("port", port) ?: port
            val outboundProtocol = outbound?.optString("protocol")?.ifBlank { null } ?: protocol
            val endpointKey = "${outboundProtocol.lowercase()}|$host|$endpointPort|$credential"
            fullConfigJson?.let {
                val json = JSONObject(it)
                val tags = json.getJSONArray("outbounds").let { outbounds ->
                    (0 until outbounds.length()).map { i -> outbounds.optJSONObject(i)?.optString("tag") }
                }
                "xray|${org.json.JSONArray(listOf(json.optString("remarks"), endpointKey) + tags)}"
            } ?: endpointKey
        }
}
