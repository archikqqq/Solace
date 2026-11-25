package com.v2nox.app

/** Manual import accepts client JSON as well as the existing share-link formats. */
internal fun parseProfileImport(text: String): List<ServerConfig> {
    val configs = RawConfigParser.parse(text) ?: listOf(ServerLinkParser.parse(text))
    require(configs.isNotEmpty()) { "В конфигурации нет поддерживаемых серверов" }
    return configs.distinctBy { it.identityKey }
}

/**
 * Clipboard text is a subscription URL when, after trim(), it starts with http:// or https://
 * and contains no whitespace or line breaks. Such text is routed to the subscription handler
 * (the same one used by the "URL подписка" menu item), not to the server-link parser.
 */
internal fun isSubscriptionUrl(text: String): Boolean {
    val candidate = text.trim()
    val lower = candidate.lowercase()
    return (lower.startsWith("http://") || lower.startsWith("https://")) &&
        candidate.none { it.isWhitespace() }
}
