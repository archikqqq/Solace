package com.v2nox.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.IBinder
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.UUID

data class MainUiState(
    val vpnState: VpnState = VpnState.DISCONNECTED,
    val activeConfig: ServerConfig? = null,
    val savedConfigs: List<ServerConfig> = emptyList(),
    val subscriptions: List<SubscriptionConfig> = emptyList(),
    val refreshingSubscriptionId: String? = null,
    val errorMessage: String? = null,
    val pingResults: Map<String, Long> = emptyMap(),
    val pingingServerId: String? = null,
    val isPingingAll: Boolean = false,
    val routeRussiaDirect: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()
    private val serverRepository = ServerRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)
    private val appearanceRepository = AppearanceRepository(application)

    private val mutablePermissionRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<Intent> = mutablePermissionRequests.asSharedFlow()

    private var service: V2NoxVpnService? = null
    private var boundContext: Context? = null
    private var pendingConfig: ServerConfig? = null
    private var pingJob: Job? = null
    private var lastPingTarget: Triple<String, String, Int>? = null

    init {
        viewModelScope.launch {
            appearanceRepository.settings
                .catch { }
                .collect { settings ->
                    mutableUiState.value = mutableUiState.value.copy(routeRussiaDirect = settings.routeRussiaDirect)
                }
        }
        viewModelScope.launch {
            subscriptionRepository.subscriptions
                .catch { mutableUiState.value = mutableUiState.value.copy(errorMessage = "Не удалось загрузить подписки") }
                .collect { subscriptions ->
                    mutableUiState.value = mutableUiState.value.copy(subscriptions = subscriptions)
                }
        }
        viewModelScope.launch {
            combine(serverRepository.servers, serverRepository.activeServerId) { configs, activeId ->
                configs to activeId
            }.collect { (configs, activeId) ->
                mutableUiState.value = mutableUiState.value.copy(
                    savedConfigs = configs,
                    activeConfig = configs.firstOrNull { it.id == activeId },
                )
                val active = configs.firstOrNull { it.id == activeId }
                val pingTarget = active?.let { Triple(it.id, it.address, it.port) }
                if (pingTarget != lastPingTarget) {
                    lastPingTarget = pingTarget
                    active?.let(::refreshPing)
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val instance = (binder as? V2NoxVpnService.LocalBinder)?.service ?: return
            service = instance
            viewModelScope.launch {
                instance.state.collect { value -> mutableUiState.value = mutableUiState.value.copy(vpnState = value) }
            }
            viewModelScope.launch {
                instance.error.collect { message ->
                    mutableUiState.value = mutableUiState.value.copy(errorMessage = message)
                }
            }
            pendingConfig?.also { pendingConfig = null; instance.startVpn(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    fun setRouteRussiaDirect(enabled: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(routeRussiaDirect = enabled)
        viewModelScope.launch {
            try {
                appearanceRepository.setRouteRussiaDirect(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableUiState.value = mutableUiState.value.copy(errorMessage = "Не удалось сохранить настройку маршрутизации")
            }
        }
    }

    fun importFromClipboard(text: String) {
        if (isSubscriptionUrl(text)) {
            addSubscription(text.trim())
            return
        }
        runCatching { parseProfileImport(text) }
            .onSuccess { parsed ->
                val existing = mutableUiState.value.savedConfigs
                val identities = existing.map { it.identityKey }.toMutableSet()
                val added = parsed.filter { identities.add(it.identityKey) }
                if (added.isNotEmpty()) {
                    val shouldActivate = mutableUiState.value.activeConfig == null
                    mutableUiState.value = mutableUiState.value.copy(
                        savedConfigs = existing + added,
                        activeConfig = mutableUiState.value.activeConfig ?: added.first(),
                        errorMessage = null,
                    )
                    viewModelScope.launch {
                        added.forEach { serverRepository.saveServer(it) }
                        if (shouldActivate && serverRepository.activeServerId.first() == null) {
                            serverRepository.setActiveServerId(added.first().id)
                        }
                    }
                }
            }
            .onFailure { mutableUiState.value = mutableUiState.value.copy(errorMessage = it.message ?: "Не удалось прочитать конфигурацию") }
    }

    fun addSubscription(url: String) {
        viewModelScope.launch {
            try {
                mutableUiState.value = mutableUiState.value.copy(errorMessage = null)
                val subscription = subscriptionRepository.add(url).getOrThrow()
                if (serverRepository.activeServerId.first() == null) {
                    serverRepository.servers.first().firstOrNull { it.subscriptionId == subscription.id }?.let {
                        serverRepository.setActiveServerId(it.id)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = e.message ?: "Не удалось добавить подписку",
                )
            }
        }
    }

    fun refreshSubscription(id: String) {
        if (mutableUiState.value.refreshingSubscriptionId != null) return
        mutableUiState.value = mutableUiState.value.copy(refreshingSubscriptionId = id, errorMessage = null)
        viewModelScope.launch {
            try {
                subscriptionRepository.update(id).getOrThrow()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = e.message ?: "Не удалось обновить подписку",
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(refreshingSubscriptionId = null)
            }
        }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch {
            try {
                subscriptionRepository.remove(id)
                mutableUiState.value = mutableUiState.value.copy(errorMessage = null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableUiState.value = mutableUiState.value.copy(errorMessage = "Не удалось удалить подписку. Попробуйте ещё раз.")
            }
        }
    }

    fun selectConfig(config: ServerConfig) {
        val selected = mutableUiState.value.savedConfigs.firstOrNull { it.identityKey == config.identityKey } ?: return
        mutableUiState.value = mutableUiState.value.copy(activeConfig = selected, errorMessage = null)
        refreshPing(selected)
        viewModelScope.launch { serverRepository.setActiveServerId(selected.id) }
    }

    fun refreshPing(config: ServerConfig? = mutableUiState.value.activeConfig) {
        config ?: return
        pingJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(pingingServerId = config.id, isPingingAll = false)
        pingJob = viewModelScope.launch {
            val result = serverRepository.measurePing(config)
            val current = mutableUiState.value
            mutableUiState.value = current.copy(
                pingResults = current.pingResults + (config.id to result),
                pingingServerId = current.pingingServerId.takeUnless { it == config.id },
            )
        }
    }

    /** Reuses the existing TCP measurement, sequentially; never starts a VPN. */
    fun refreshAllPings() {
        val configs = mutableUiState.value.savedConfigs
        if (configs.isEmpty()) return
        pingJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(isPingingAll = true)
        pingJob = viewModelScope.launch {
            try {
                for (config in configs) {
                    if (mutableUiState.value.savedConfigs.none { it.id == config.id }) continue
                    mutableUiState.value = mutableUiState.value.copy(pingingServerId = config.id)
                    val result = serverRepository.measurePing(config)
                    val current = mutableUiState.value
                    if (current.savedConfigs.any { it.id == config.id }) {
                        mutableUiState.value = current.copy(pingResults = current.pingResults + (config.id to result))
                    }
                }
            } finally {
                // A cancelled batch must not erase a newer single/batch measurement.
                if (currentCoroutineContext().isActive) {
                    mutableUiState.value = mutableUiState.value.copy(isPingingAll = false, pingingServerId = null)
                }
            }
        }
    }

    fun renameConfig(config: ServerConfig, name: String) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        val current = mutableUiState.value
        val existing = current.savedConfigs.firstOrNull { it.id == config.id } ?: return
        val renamed = existing.copy(name = trimmed)
        mutableUiState.value = current.copy(
            savedConfigs = current.savedConfigs.map { if (it.id == renamed.id) renamed else it },
            activeConfig = if (current.activeConfig?.id == renamed.id) renamed else current.activeConfig,
        )
        viewModelScope.launch { serverRepository.saveServer(renamed) }
    }

    fun deleteConfig(config: ServerConfig) {
        val current = mutableUiState.value
        val updated = current.savedConfigs.filterNot { it.id == config.id }
        mutableUiState.value = current.copy(savedConfigs = updated, activeConfig = if (current.activeConfig?.id == config.id) updated.firstOrNull() else current.activeConfig)
        viewModelScope.launch {
            serverRepository.removeServer(config.id)
            if (current.activeConfig?.id == config.id) {
                serverRepository.setActiveServerId(updated.firstOrNull()?.id)
            }
        }
    }

    fun toggleVpn(context: Context) {
        val state = mutableUiState.value
        if (state.vpnState == VpnState.CONNECTED || state.vpnState == VpnState.CONNECTING) {
            if (service != null) service?.stopVpn() else context.startService(Intent(context, V2NoxVpnService::class.java).setAction(V2NoxVpnService.ACTION_STOP))
            return
        }
        val config = state.activeConfig ?: run {
            mutableUiState.value = state.copy(errorMessage = "Сначала выберите сервер")
            return
        }
        pendingConfig = config
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent != null) mutablePermissionRequests.tryEmit(permissionIntent)
        else onVpnPermissionResult(context, granted = true)
    }

    fun onVpnPermissionResult(context: Context, granted: Boolean) {
        if (!granted) {
            pendingConfig = null
            mutableUiState.value = mutableUiState.value.copy(errorMessage = "Разрешение VPN не предоставлено")
            return
        }
        val intent = Intent(context, V2NoxVpnService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        if (service == null && boundContext == null) {
            boundContext = context.applicationContext
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } else pendingConfig?.also { service?.startVpn(it); pendingConfig = null }
    }

    override fun onCleared() {
        boundContext?.unbindService(connection)
        boundContext = null
        super.onCleared()
    }
}

/** Imports the share-link formats commonly used by Xray-compatible clients. */
object ServerLinkParser {
    private val supportedSchemes = setOf("vless", "vmess", "trojan", "ss", "shadowsocks", "hysteria2", "hy2", "tuic", "wireguard")

    fun parse(text: String): ServerConfig {
        val link = text.trim().lineSequence().map(String::trim).firstOrNull { candidate ->
            candidate.substringBefore("://").lowercase() in supportedSchemes && "://" in candidate
        } ?: error("В буфере не найдена поддерживаемая ссылка Xray")
        val scheme = link.substringBefore("://").lowercase()
        val protocol = when (scheme) {
            "ss", "shadowsocks" -> "SHADOWSOCKS"
            "hy2", "hysteria2" -> "HYSTERIA2"
            else -> scheme.uppercase()
        }
        val parts = when (scheme) {
            "vmess" -> parseVmess(link)
            "ss", "shadowsocks" -> parseShadowsocks(link)
            else -> parseUri(link)
        }
        val (host, port) = parts.host to parts.port
        require(host.isNotBlank()) { "В конфигурации отсутствует адрес сервера" }
        require(port in 1..65535) { "Некорректный порт сервера" }
        val name = parts.name?.takeIf(String::isNotBlank) ?: host
        val security = parts.values["security"] ?: when (scheme) {
            "trojan", "hysteria2", "hy2", "tuic" -> "tls"
            else -> null
        }
        return ServerConfig(UUID.randomUUID().toString(), name, host, port, link, security, protocol)
    }

    internal data class LinkParts(
        val host: String,
        val port: Int,
        val name: String?,
        val values: Map<String, String>,
        val username: String? = null,
        val password: String? = null,
    )

    internal fun parseRawLink(link: String): LinkParts = when (link.substringBefore("://").lowercase()) {
        "vmess" -> parseVmess(link)
        "ss", "shadowsocks" -> parseShadowsocks(link)
        else -> parseUri(link)
    }

    private fun parseUri(link: String): LinkParts {
        val uri = java.net.URI(LinkUtils.fixIllegalUrl(link))
        val values = decodeQuery(uri.rawQuery)
        val userInfo = uri.rawUserInfo?.let(::decode)
        return LinkParts(
            host = uri.host ?: error("В конфигурации отсутствует адрес сервера"),
            port = uri.port,
            name = uri.rawFragment?.let(::decode),
            values = values,
            username = userInfo?.substringBefore(':'),
            password = userInfo?.substringAfter(':', missingDelimiterValue = "")?.takeIf { ':' in userInfo },
        )
    }

    private fun parseVmess(link: String): LinkParts {
        val payload = link.substringAfter("://").substringBefore('#')
        val json = runCatching { decodeBase64(payload) }.getOrElse { error("Некорректная VMess-ссылка") }
        val item = runCatching { org.json.JSONObject(json) }.getOrElse { error("Некорректные данные VMess") }
        val values = buildMap {
            put("id", item.optString("id"))
            put("aid", item.optString("aid", "0"))
            put("scy", item.optString("scy", item.optString("security", "auto")))
            put("net", item.optString("net", "tcp"))
            put("type", item.optString("type", "none"))
            put("host", item.optString("host"))
            put("path", item.optString("path"))
            put("tls", item.optString("tls"))
            put("sni", item.optString("sni"))
            put("fp", item.optString("fp"))
        }
        val host = item.optString("add").takeIf(String::isNotBlank) ?: error("В конфигурации VMess отсутствует адрес сервера")
        val port = item.optString("port").toIntOrNull() ?: error("Некорректный порт сервера")
        return LinkParts(host, port, item.optString("ps").takeIf(String::isNotBlank), values)
    }

    private fun parseShadowsocks(link: String): LinkParts {
        val sanitized = LinkUtils.fixIllegalUrl(link)
        val body = sanitized.substringAfter("://").substringBefore('#')
        val userAt = body.lastIndexOf('@')
        val userInfo: String
        val server: String
        if (userAt >= 0) {
            userInfo = body.substring(0, userAt)
            server = body.substring(userAt + 1)
        } else {
            val decoded = runCatching { decodeBase64(body) }.getOrElse { error("Некорректная Shadowsocks-ссылка") }
            val split = decoded.lastIndexOf('@')
            require(split > 0) { "В Shadowsocks-ссылке отсутствуют параметры шифрования" }
            userInfo = decoded.substring(0, split)
            server = decoded.substring(split + 1)
        }
        val credentials = runCatching { decodeBase64(userInfo) }.getOrDefault(decode(userInfo))
        val colon = credentials.indexOf(':')
        require(colon > 0) { "Некорректные параметры Shadowsocks" }
        val uri = java.net.URI(LinkUtils.fixIllegalUrl("ss://$server"))
        val fragment = sanitized.substringAfter('#', "").takeIf(String::isNotBlank)?.let(::decode)
        return LinkParts(
            host = uri.host ?: error("В конфигурации отсутствует адрес сервера"),
            port = uri.port,
            name = fragment,
            values = mapOf("method" to credentials.substring(0, colon)),
            username = credentials.substring(0, colon),
            password = credentials.substring(colon + 1),
        )
    }

    private fun decodeQuery(query: String?): Map<String, String> = query.orEmpty()
        .split('&').mapNotNull { entry ->
            if (entry.isBlank()) return@mapNotNull null
            val split = entry.split('=', limit = 2)
            decode(split[0]) to decode(split.getOrElse(1) { "" })
        }.toMap()

    private fun decode(value: String): String = java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }
}

/** Kept as a source-compatible alias for clients which used the original parser name. */
@Deprecated("Use ServerLinkParser")
object VlessParser {
    fun parse(text: String): ServerConfig = ServerLinkParser.parse(text)
}
