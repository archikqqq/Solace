package com.v2nox.app

import android.content.Context
import java.io.File
import java.io.OutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Selects only outbounds; the VPN owns its inbounds, routing and direct outbound. */
internal fun buildXrayOutbounds(config: ServerConfig, routeRussiaDirect: Boolean = false): JSONArray {
    val outbounds = config.outboundJson?.let { raw ->
        val bundle = JSONObject(raw)
        val primary = bundle.getJSONObject("outbound")
        val extra = bundle.optJSONArray("extra") ?: JSONArray()
        val objects = listOf(primary) + (0 until extra.length()).map { extra.getJSONObject(it) }
        val reserved = setOf("proxy", "direct", "tun-in", "dns-out")
        val used = (reserved + objects.map { it.optString("tag") }).toMutableSet()
        val renamed = mutableMapOf<String, String>()
        val primaryTag = primary.optString("tag")
        if (primaryTag.isNotBlank() && primaryTag != "proxy") renamed[primaryTag] = "proxy"
        for ((index, outbound) in objects.drop(1).withIndex()) {
            val tag = outbound.optString("tag")
            if (tag in reserved) {
                var replacement = "extra-$index"
                while (!used.add(replacement)) replacement += "-extra"
                renamed[tag] = replacement
                outbound.put("tag", replacement)
            }
        }
        primary.put("tag", "proxy")
        objects.forEach { outbound ->
            val sockopt = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
            val target = sockopt?.optString("dialerProxy")
            renamed[target]?.let { sockopt?.put("dialerProxy", it) }
        }
        JSONArray().apply { objects.forEach { put(it) } }
    } ?: JSONArray().put(buildLinkOutbound(config))
    return ensureDirectOutbound(outbounds)
}

/** Guarantees a freedom outbound tagged "direct" exists (added only when missing). */
internal fun ensureDirectOutbound(outbounds: JSONArray): JSONArray {
    val exists = (0 until outbounds.length()).any { outbounds.optJSONObject(it)?.optString("tag") == "direct" }
    return if (exists) outbounds else outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
}

/**
 * Sets domainStrategy and, when [routeRussiaDirect] is true, inserts Russian "direct" rules.
 * Domains and IPs go into separate rules because Xray ANDs the fields within one rule.
 * The rules follow the local/private rules and precede any catch-all proxy rule.
 * Geo tags are used only if the matching .dat file really contains them; regexp rules always apply.
 */
internal fun applyRussiaRouting(context: Context?, routing: JSONObject, routeRussiaDirect: Boolean) {
    routing.put("domainStrategy", "IPIfNonMatch")
    if (!routeRussiaDirect) return
    val domains = JSONArray()
        .put("regexp:.*\\.ru$")
        .put("regexp:.*\\.\u0440\u0444$")
        .put("domain:su")
    if (hasGeoTag(context, "geosite.dat", "CATEGORY-RU")) domains.put("geosite:category-ru")
    val inserted = mutableListOf(
        JSONObject().put("type", "field").put("outboundTag", "direct").put("domain", domains),
    )
    if (hasGeoTag(context, "geoip.dat", "RU")) {
        inserted += JSONObject().put("type", "field").put("outboundTag", "direct")
            .put("ip", JSONArray().put("geoip:ru"))
    }
    val rules = routing.optJSONArray("rules") ?: JSONArray()
    val existing = (0 until rules.length()).map { rules.get(it) }
    val position = existing.indexOfLast { it is JSONObject && isPrivateRule(it) } + 1
    val merged = JSONArray()
    existing.take(position).forEach { merged.put(it) }
    inserted.forEach { merged.put(it) }
    existing.drop(position).forEach { merged.put(it) }
    routing.put("rules", merged)
}

private val PRIVATE_GEO_TAGS = setOf("geosite:private", "geoip:private")

private fun isPrivateRule(rule: JSONObject): Boolean = listOf("domain", "ip").any { key ->
    val values = rule.optJSONArray(key) ?: return@any false
    (0 until values.length()).any { values.optString(it) in PRIVATE_GEO_TAGS }
}

/** True only if [context] is available and [file] contains the geo [code]; any failure counts as absent. */
private fun hasGeoTag(context: Context?, file: String, code: String): Boolean {
    if (context == null) return false
    return runCatching {
        val installed = File(context.filesDir, file)
        val source = if (installed.isFile) installed.inputStream() else context.assets.open(file)
        source.use { XrayGeoAssets.slice(it, DiscardingOutput, setOf(code)).isEmpty() }
    }.getOrDefault(false)
}

private object DiscardingOutput : OutputStream() {
    override fun write(b: Int) = Unit
}
private fun buildLinkOutbound(config: ServerConfig): JSONObject {
    val rawUrl = config.rawUrl ?: error("У профиля отсутствуют ссылка и JSON-конфигурация")
    val link = ServerLinkParser.parseRawLink(rawUrl)
    val query = link.values
    val scheme = rawUrl.substringBefore("://").lowercase()
    val outbound = when (scheme) {
        "vless" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "vless")
            put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", link.host).put("port", link.port)
                .put("users", JSONArray().put(JSONObject()
                    .put("id", link.username?.takeIf(String::isNotBlank) ?: error("В VLESS-ссылке отсутствует UUID"))
                    .put("encryption", query["encryption"] ?: "none")
                    .apply { query["flow"]?.let { put("flow", it) } })))))
        }
        "vmess" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "vmess")
            put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", link.host).put("port", link.port)
                .put("users", JSONArray().put(JSONObject()
                    .put("id", query["id"]?.takeIf(String::isNotBlank) ?: error("В VMess-ссылке отсутствует UUID"))
                    .put("alterId", query["aid"]?.toIntOrNull() ?: 0)
                    .put("security", query["scy"] ?: "auto"))))))
        }
        "trojan" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "trojan")
            put("settings", JSONObject().put("servers", JSONArray().put(JSONObject()
                .put("address", link.host).put("port", link.port)
                .put("password", link.username?.takeIf(String::isNotBlank) ?: error("В Trojan-ссылке отсутствует пароль")))))
        }
        "ss", "shadowsocks" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "shadowsocks")
            put("settings", JSONObject().put("servers", JSONArray().put(JSONObject()
                .put("address", link.host).put("port", link.port)
                .put("method", query["method"] ?: error("В Shadowsocks-ссылке отсутствует метод шифрования"))
                .put("password", link.password ?: error("В Shadowsocks-ссылке отсутствует пароль")))))
        }
        "wireguard" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "wireguard")
            put("settings", JSONObject()
                .put("secretKey", link.username ?: error("В WireGuard-ссылке отсутствует приватный ключ"))
                .put("address", stringArray(query["address"] ?: error("В WireGuard-ссылке отсутствует address")))
                .put("peers", JSONArray().put(JSONObject()
                    .put("publicKey", query["publickey"] ?: query["publicKey"] ?: error("В WireGuard-ссылке отсутствует publicKey"))
                    .put("endpoint", "${link.host}:${link.port}")
                    .put("allowedIPs", stringArray(query["allowedips"] ?: query["allowedIPs"] ?: "0.0.0.0/0,::/0"))))
                .apply { query["mtu"]?.toIntOrNull()?.let { put("mtu", it) } })
        }
        "hysteria2", "hy2" -> JSONObject().apply {
            put("tag", "proxy"); put("protocol", "hysteria")
            put("settings", JSONObject().put("version", 2).put("address", link.host).put("port", link.port))
        }
        "tuic" -> error("TUIC импортирован, но не поддерживается встроенным Xray Core")
        else -> error("Протокол ${config.protocol} не поддерживается")
    }
    val transport = when (scheme) {
        "hysteria2", "hy2" -> "hysteria"
        "vmess" -> query["net"] ?: "tcp"
        else -> query["type"] ?: "tcp"
    }
    val security = when {
        scheme == "vmess" && query["tls"]?.lowercase() in setOf("tls", "true") -> "tls"
        scheme in setOf("trojan", "hysteria2", "hy2", "tuic") -> "tls"
        else -> query["security"]?.lowercase()
    }
    val stream = JSONObject().put("network", transport).put("security", security ?: "none")
    if (security == "tls") stream.put("tlsSettings", JSONObject().apply {
        (query["sni"] ?: query["peer"] ?: query["host"])?.takeIf(String::isNotBlank)?.let { put("serverName", it) }
        query["fp"]?.takeIf(String::isNotBlank)?.let { put("fingerprint", it) }
        query["alpn"]?.takeIf(String::isNotBlank)?.let { put("alpn", stringArray(it)) }
        query["allowInsecure"]?.toBooleanStrictOrNull()?.let { put("allowInsecure", it) }
    })
    if (security == "reality") stream.put("realitySettings", JSONObject().apply {
        (query["sni"] ?: query["serverName"])?.let { put("serverName", it) }
        (query["fp"] ?: query["fingerprint"])?.let { put("fingerprint", it) }
        (query["pbk"] ?: query["publicKey"])?.let { put("publicKey", it) }
        (query["sid"] ?: query["shortId"])?.let { put("shortId", it) }
        (query["spx"] ?: query["spiderX"])?.let { put("spiderX", it) }
        (query["pqv"] ?: query["mldsa65Verify"])?.let { put("mldsa65Verify", it) }
    })
    if (scheme in setOf("hysteria2", "hy2")) {
        stream.put("hysteriaSettings", JSONObject().put("version", 2).put("auth", link.username ?: ""))
        query["obfs-password"]?.let { stream.getJSONObject("hysteriaSettings").put("obfsPassword", it) }
    }
    when (transport.lowercase()) {
        "xhttp", "splithttp" -> stream.put("xhttpSettings", JSONObject().apply {
            query["path"]?.let { put("path", it) }
            query["host"]?.let { put("host", it) }
            query["mode"]?.let { put("mode", it) }
            query["extra"]?.takeIf(String::isNotBlank)?.let { put("extra", JSONObject(it)) }
        })
        "ws", "websocket" -> stream.put("wsSettings", JSONObject().apply {
            query["path"]?.let { put("path", it) }
            (query["host"] ?: query["authority"])?.let { put("headers", JSONObject().put("Host", it)) }
        })
        "grpc" -> stream.put("grpcSettings", JSONObject().apply {
            (query["serviceName"] ?: query["service-name"] ?: query["path"])?.let { put("serviceName", it) }
            query["authority"]?.let { put("authority", it) }
        })
        "http", "h2" -> stream.put("httpSettings", JSONObject().apply {
            query["path"]?.let { put("path", it) }
            (query["host"] ?: query["authority"])?.let { put("host", JSONArray().put(it)) }
        })
    }
    if (scheme == "vless") linkFinalMask(query)?.let { stream.put("finalmask", it) }
    return outbound.put("streamSettings", stream)
}

private fun linkFinalMask(query: Map<String, String>): JSONObject? {
    val fragment = query["fragment"]?.trim()
    val options = fragment?.takeIf { it.startsWith("{") }?.let(::JSONObject)
    val compact = fragment?.takeUnless { options != null || it in setOf("true", "1", "false", "0") }
        ?.split(',')
    val length = query["fragmentLength"] ?: query["length"] ?: options?.optString("length")
        ?: compact?.getOrNull(1)
    val interval = query["fragmentInterval"] ?: query["interval"] ?: options?.optString("interval")
        ?: compact?.getOrNull(2)
    val mask = JSONObject()
    val packets = query["fragmentPackets"] ?: query["packets"] ?: options?.optString("packets")
        ?: compact?.firstOrNull()
    if (fragment !in setOf("false", "0") && (fragment != null || length != null || interval != null || packets != null)) {
        val selection = (packets ?: "tlshello").trim().lowercase()
        val settings = JSONObject().put("packets", when (selection) {
            "tlshello" -> selection
            "", "all", "tcp" -> ""
            else -> range(selection, 1..Int.MAX_VALUE)
        }).put("length", range(length.orEmpty(), 1..65535))
            .put("delay", range(interval.orEmpty(), 0..1000))
        mask.put("tcp", JSONArray().put(JSONObject().put("type", "fragment").put("settings", settings)))
    }
    query["noises"]?.takeIf(String::isNotBlank)?.let { raw ->
        val input = JSONArray(raw)
        require(input.length() <= 16) { "Слишком много записей noises" }
        val noises = JSONArray()
        for (i in 0 until input.length()) {
            val noise = input.getJSONObject(i)
            val type = noise.getString("type").lowercase()
            val packet = noise.getString("packet").trim()
            require(noise.optString("applyTo").lowercase() in setOf("", "ip", "all")) {
                "Фильтр applyTo поддерживается только в готовых JSON-профилях"
            }
            val item = JSONObject().put("delay", range(noise.optString("delay", "0"), 0..1000))
            when (type) {
                "rand" -> item.put("rand", range(packet, 1..65507))
                "str", "hex", "base64" -> {
                    require(packet.length <= 8192) { "Слишком большой пакет noises" }
                    val data = if (type == "base64") {
                        val normalized = packet.replace('-', '+').replace('_', '/')
                        java.util.Base64.getEncoder().encodeToString(java.util.Base64.getDecoder().decode(normalized))
                    } else packet
                    require(type != "hex" || (data.length % 2 == 0 && data.all { it.digitToIntOrNull(16) != null })) {
                        "Некорректный hex-пакет noises"
                    }
                    item.put("type", type).put("packet", data)
                }
                else -> error("Неподдерживаемый тип noises")
            }
            noises.put(item)
        }
        if (noises.length() > 0) mask.put("udp", JSONArray().put(JSONObject().put("type", "noise")
            .put("settings", JSONObject().put("noise", noises))))
    }
    return mask.takeIf { it.length() > 0 }
}

private fun range(value: String, limits: IntRange): String {
    val parts = value.trim().split('-', limit = 2)
    val from = parts.first().toIntOrNull()
    val to = parts.last().toIntOrNull()
    require(from != null && to != null && from in limits && to in limits && from <= to) {
        "Некорректный диапазон фрагментации или noises"
    }
    return "$from-$to"
}

private fun stringArray(value: String): JSONArray = JSONArray().apply {
    value.split(',').map(String::trim).filter(String::isNotBlank).forEach(::put)
}
