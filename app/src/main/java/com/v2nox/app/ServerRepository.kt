package com.v2nox.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.serverPreferences by preferencesDataStore(name = "servers")

class ServerRepository(context: Context) {
    private val dataStore = context.applicationContext.serverPreferences

    val servers: Flow<List<ServerConfig>> = dataStore.data.map { preferences ->
        decodeServers(preferences[SERVER_LIST_KEY].orEmpty())
    }

    val activeServerId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_SERVER_ID_KEY]
    }

    suspend fun measurePing(server: ServerConfig): Long = withContext(Dispatchers.IO) {
        ActiveProxyLatency.measure(server.id)
    }

    suspend fun saveServer(config: ServerConfig) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences[SERVER_LIST_KEY].orEmpty())
            val updated = current.toMutableList()
            val index = updated.indexOfFirst { it.id == config.id }
            if (index >= 0) updated[index] = config else updated.add(config)
            preferences[SERVER_LIST_KEY] = encodeServers(updated)
        }
    }

    suspend fun removeServer(id: String) {
        dataStore.edit { preferences ->
            val updated = decodeServers(preferences[SERVER_LIST_KEY].orEmpty()).filterNot { it.id == id }
            preferences[SERVER_LIST_KEY] = encodeServers(updated)
        }
    }

    suspend fun replaceSubscriptionServers(subscriptionId: String, newServers: List<ServerConfig>) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences[SERVER_LIST_KEY].orEmpty())
            val previous = current.filter { it.subscriptionId == subscriptionId }
            val previousByIdentity = previous.groupBy { it.identityKey }
                .mapValues { (_, servers) -> servers.toMutableList() }
            val replacements = newServers.map { server ->
                val oldServer = previousByIdentity[server.identityKey]?.removeFirstOrNull()
                server.copy(id = oldServer?.id ?: server.id, subscriptionId = subscriptionId)
            }
            val updated = current.filterNot { it.subscriptionId == subscriptionId } + replacements
            preferences[SERVER_LIST_KEY] = encodeServers(updated)

            val activeId = preferences[ACTIVE_SERVER_ID_KEY]
            if (previous.any { it.id == activeId } && replacements.none { it.id == activeId }) {
                val nextActiveId = replacements.firstOrNull()?.id
                if (nextActiveId == null) preferences.remove(ACTIVE_SERVER_ID_KEY)
                else preferences[ACTIVE_SERVER_ID_KEY] = nextActiveId
            }
        }
    }

    suspend fun removeSubscriptionServers(subscriptionId: String) {
        replaceSubscriptionServers(subscriptionId, emptyList())
    }

    suspend fun setActiveServerId(id: String?) {
        dataStore.edit { preferences ->
            if (id == null) preferences.remove(ACTIVE_SERVER_ID_KEY)
            else preferences[ACTIVE_SERVER_ID_KEY] = id
        }
    }

    private fun decodeServers(value: String): List<ServerConfig> {
        if (value.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val item = array.getJSONObject(index)
                        ServerConfig(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            address = item.getString("address"),
                            port = item.getInt("port"),
                            rawUrl = item.optionalString("rawUrl"),
                            security = item.optionalString("security"),
                            protocol = item.optionalString("protocol") ?: "VLESS",
                            subscriptionId = item.optionalString("subscriptionId"),
                            outboundJson = item.optionalString("outboundJson"),
                            fullConfigJson = item.optionalString("fullConfigJson"),
                        )
                    }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeServers(servers: List<ServerConfig>): String = JSONArray().apply {
        servers.forEach { config ->
            put(JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("address", config.address)
                put("port", config.port)
                put("rawUrl", config.rawUrl ?: JSONObject.NULL)
                put("security", config.security ?: JSONObject.NULL)
                put("protocol", config.protocol)
                put("subscriptionId", config.subscriptionId ?: JSONObject.NULL)
                put("outboundJson", config.outboundJson ?: JSONObject.NULL)
                put("fullConfigJson", config.fullConfigJson ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name) || !has(name)) null else optString(name)

    private companion object {
        val SERVER_LIST_KEY = stringPreferencesKey("server_list")
        val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    }
}
