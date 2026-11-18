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
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class V2NoxVpnService : VpnService() {
    inner class LocalBinder : android.os.Binder() { val service: V2NoxVpnService get() = this@V2NoxVpnService }
    private val localBinder = LocalBinder()

    override fun onBind(intent: Intent): android.os.IBinder? =
        if (intent.action == SERVICE_INTERFACE) super.onBind(intent) else localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WIDGET_TOGGLE) {
            when (state.value) {
                VpnState.CONNECTED, VpnState.CONNECTING -> stopVpn()
                VpnState.STOPPING -> Unit
                VpnState.DISCONNECTED -> {
                    if (VpnService.prepare(this) != null) {
                        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.widget_permission_required)))
                        android.widget.Toast.makeText(this, R.string.widget_permission_required, android.widget.Toast.LENGTH_LONG).show()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    } else {
                        return onStartCommand(Intent(this, V2NoxVpnService::class.java).setAction(ACTION_START), flags, startId)
                    }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) stopVpn()
        if (intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, buildNotification("Подключение…"))
            serviceScope.launch {
                operationMutex.withLock {
                    if (destroyed) return@withLock
                    try {
                        val repository = ServerRepository(applicationContext)
                        val servers = repository.servers.first()
                        val activeServerId = repository.activeServerId.first()
                        val config = servers.firstOrNull { it.id == activeServerId }
                            ?: run {
                                android.os.Handler(mainLooper).post {
                                    android.widget.Toast.makeText(this@V2NoxVpnService, R.string.widget_no_server, android.widget.Toast.LENGTH_LONG).show()
                                }
                                error("Сначала выберите сервер")
                            }
                        startVpnInternal(config)
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        failVpn(exception.message ?: "Не удалось загрузить активный сервер")
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state: StateFlow<VpnState> = VpnStateHolder.state
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    @Volatile
    private var destroyed = false
    private var tunInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var bridgeConfig: File? = null
    private var geoAssets: File? = null
    private var bridgeStarted = false
    private var bridgeMonitor: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /** Safe to call from UI code; service operations are serialized on the IO dispatcher. */
    fun startVpn(config: ServerConfig) {
        serviceScope.launch {
            operationMutex.withLock {
                if (!destroyed) startVpnInternal(config)
            }
        }
    }

    private fun startVpnInternal(config: ServerConfig) {
        releaseTunnel()
        mutableError.value = null
        VpnStateHolder.mutableState.value = VpnState.CONNECTING
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Подключение…"))
            val session = VpnTunnelConfig.create(config)
            val xray = session.xrayConfig(config)
            geoAssets = XrayGeoAssets.prepare(this, xray)
            Libv2ray.initCoreEnv((geoAssets ?: filesDir).absolutePath, "")
            val controller = Libv2ray.newCoreController(coreCallbacks)
            coreController = controller
            controller.startLoop(xray, 0) // HEV, not Xray, owns TUN I/O.
            session.socks.verify()

            val yaml = File.createTempFile("hev-session-", ".yml", cacheDir)
            bridgeConfig = yaml
            yaml.writeText(session.hevConfig())

            val established = Builder()
                .setSession(config.name)
                .setMtu(VpnTunnelConfig.MTU)
                .addAddress(VpnTunnelConfig.TUN_IPV4, 32)
                .addAddress(VpnTunnelConfig.TUN_IPV6, 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer(VpnTunnelConfig.DNS_IPV4)
                // Native Xray sockets and its upstream DNS must not loop back into TUN.
                .addDisallowedApplication(packageName)
                .establish() ?: error("Не удалось создать VPN-интерфейс")
            tunInterface = established
            check(TProxyService.TProxyStartService(yaml.absolutePath, established.fd)) {
                "Не удалось запустить tun2socks"
            }
            bridgeStarted = true
            check(TProxyService.TProxyIsRunning()) { "Мост tun2socks завершился при запуске" }

            ActiveProxyLatency.start(config.id, session.socks)
            VpnStateHolder.mutableState.value = VpnState.CONNECTED
            updateNotification("Подключено: ${config.name}")
            monitorBridge()
        } catch (exception: Exception) {
            failVpn(exception.message ?: "Не удалось запустить VPN")
        } catch (_: LinkageError) {
            failVpn("Не удалось загрузить нативную библиотеку VPN")
        }
    }

    private fun monitorBridge() {
        bridgeMonitor = serviceScope.launch {
            while (true) {
                delay(1_000)
                operationMutex.withLock {
                    if (destroyed) return@launch
                    if (!TProxyService.TProxyIsRunning()) {
                        failVpn("Мост tun2socks остановился")
                        return@launch
                    }
                }
            }
        }
    }

    /** Safe to call from UI code. */
    fun stopVpn() {
        serviceScope.launch {
            operationMutex.withLock {
                if (!destroyed) stopVpnInternal()
            }
        }
    }

    private fun failVpn(message: String) {
        mutableError.value = message
        stopVpnInternal()
    }

    private fun stopVpnInternal() {
        if (VpnStateHolder.mutableState.value != VpnState.DISCONNECTED) {
            VpnStateHolder.mutableState.value = VpnState.STOPPING
        }
        releaseTunnel()
        VpnStateHolder.mutableState.value = VpnState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseTunnel() {
        ActiveProxyLatency.stop()
        bridgeMonitor?.cancel()
        bridgeMonitor = null
        // HEV borrows the descriptor; join its worker before closing the descriptor.
        if (bridgeStarted) runCatching { TProxyService.TProxyStopService() }
        bridgeStarted = false
        runCatching { tunInterface?.close() }
        tunInterface = null
        runCatching { coreController?.stopLoop() }
        coreController = null
        geoAssets?.deleteRecursively()
        geoAssets = null
        runCatching { bridgeConfig?.delete() }
        bridgeConfig = null
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        destroyed = true
        serviceScope.launch {
            operationMutex.withLock {
                if (coreController != null) {
                    releaseTunnel()
                    VpnStateHolder.mutableState.value = VpnState.DISCONNECTED
                }
            }
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Solace VPN",
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
            .setContentTitle("Solace")
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
        // The Xray environment and the HEV worker are process-wide.
        private val operationMutex = Mutex()
        private val coreCallbacks = object : CoreCallbackHandler {
            override fun startup() = 0L
            override fun shutdown() = 0L
            override fun onEmitStatus(code: Long, message: String?) = 0L
        }
        const val CHANNEL_ID = "v2nox_vpn"
        const val ACTION_START = "com.v2nox.app.action.START_VPN"
        const val ACTION_STOP = "com.v2nox.app.action.STOP_VPN"
        const val ACTION_WIDGET_TOGGLE = "com.v2nox.app.action.WIDGET_TOGGLE_VPN"
        private const val NOTIFICATION_ID = 2101
    }
}
