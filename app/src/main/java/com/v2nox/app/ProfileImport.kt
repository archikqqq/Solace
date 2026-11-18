package com.v2nox.app

/** Manual import accepts client JSON as well as the existing share-link formats. */
internal fun parseProfileImport(text: String): List<ServerConfig> {
    val configs = RawConfigParser.parse(text) ?: listOf(ServerLinkParser.parse(text))
    require(configs.isNotEmpty()) { "В конфигурации нет поддерживаемых серверов" }
    return configs.distinctBy { it.identityKey }
}
