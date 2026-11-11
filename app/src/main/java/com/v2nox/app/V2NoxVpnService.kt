package com.v2nox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject

class V2NoxVpnService : VpnService() {
    inner class LocalBinder : android.os.Binder() { val service: V2NoxVpnService get() = this@V2NoxVpnService }
    private val localBinder = LocalBinder()

    override fun onBind(intent: Intent): android.os.IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopVpn()
        return START_NOT_STICKY
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null
    @Volatile
    private var coreController: CoreController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /** Safe to call from UI code; service operations are serialized on the IO dispatcher. */
    fun startVpn(config: ServerConfig) {
        serviceScope.launch {
            operationMutex.withLock {
                if (mutableState.value == VpnState.CONNECTED) stopVpnInternal()
                mutableError.value = null
                mutableState.value = VpnState.CONNECTING
                startForeground(NOTIFICATION_ID, buildNotification("Подключение…"))

                try {
                    Libv2ray.initCoreEnv(filesDir.absolutePath, cacheDir.absolutePath)
                    val established = Builder()
                        .setSession(config.name)
                        .setMtu(1500)
                        .addAddress("10.0.0.2", 32)
                        .addRoute("0.0.0.0", 0)
                        .establish() ?: error("Не удалось создать VPN-интерфейс")

                    tunInterface = established
                    val controller = Libv2ray.newCoreController(null)
                    controller.startLoop(buildXrayConfig(config), established.fd)
                    coreController = controller

                    mutableState.value = VpnState.CONNECTED
                    updateNotification("Подключено: ${config.name}")
                } catch (exception: Exception) {
                    runCatching { coreController?.stopLoop() }
                    coreController = null
                    tunInterface?.close()
                    tunInterface = null
                    mutableState.value = VpnState.DISCONNECTED
                    mutableError.value = exception.message ?: "Не удалось запустить VPN"
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    /** Safe to call from UI code. */
    fun stopVpn() {
        serviceScope.launch {
            operationMutex.withLock { stopVpnInternal() }
        }
    }

    private fun stopVpnInternal() {
        if (mutableState.value == VpnState.DISCONNECTED) {
            stopSelf()
            return
        }
        mutableState.value = VpnState.STOPPING

        runCatching { coreController?.stopLoop() }
        coreController = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        mutableState.value = VpnState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildXrayConfig(config: ServerConfig): String {
        val rawUrl = config.rawUrl ?: error("У профиля отсутствует исходная ссылка")
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
        val transport = if (scheme in setOf("hysteria2", "hy2")) "hysteria" else query["type"] ?: query["net"] ?: "tcp"
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
        })
        if (scheme in setOf("hysteria2", "hy2")) {
            stream.put("hysteriaSettings", JSONObject().put("version", 2).put("auth", link.username ?: ""))
            query["obfs-password"]?.let { stream.getJSONObject("hysteriaSettings").put("obfsPassword", it) }
        }
        when (transport.lowercase()) {
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
        outbound.put("streamSettings", stream)
        return JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", JSONArray().put(JSONObject()
                .put("tag", "tun-in")
                .put("port", 0)
                .put("protocol", "dokodemo-door")
                .put("settings", JSONObject().put("network", "tcp,udp").put("followRedirect", true))))
            put("outbounds", JSONArray().put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
            put("routing", JSONObject().put("rules", JSONArray().put(JSONObject()
                .put("type", "field").put("inboundTag", JSONArray().put("tun-in")).put("outboundTag", "proxy"))))
        }.toString()
    }

    private fun stringArray(value: String): JSONArray = JSONArray().apply {
        value.split(',').map(String::trim).filter(String::isNotBlank).forEach(::put)
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { coreController?.stopLoop() }
        coreController = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "v2Nox VPN",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Состояние VPN-подключения" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("v2Nox")
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    companion object {
        const val CHANNEL_ID = "v2nox_vpn"
        const val ACTION_STOP = "com.v2nox.app.action.STOP_VPN"
        private const val NOTIFICATION_ID = 2101
    }
}
