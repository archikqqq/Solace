package com.v2nox.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class V2NoxTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { ServerRepository(applicationContext) }
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            combine(VpnStateHolder.state, repository.servers, repository.activeServerId) { state, servers, activeId ->
                tileModelFor(state, servers.firstOrNull { it.id == activeId }?.name)
            }.collect { model ->
                val tile = qsTile ?: return@collect
                tile.label = getString(R.string.tile_label)
                tile.state = model.state
                tile.icon = Icon.createWithResource(this@V2NoxTileService, model.iconRes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = model.subtitle
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnStateHolder.state.value) {
            VpnState.CONNECTED, VpnState.CONNECTING -> stopVpn()
            VpnState.STOPPING -> Unit
            VpnState.DISCONNECTED -> serviceScope.launch {
                val server = combine(repository.servers, repository.activeServerId) { servers, activeId ->
                    servers.firstOrNull { it.id == activeId }
                }.first()
                // State can change while the selected server is being read.
                when (VpnStateHolder.state.value) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> stopVpn()
                    VpnState.STOPPING -> Unit
                    VpnState.DISCONNECTED -> {
                        if (VpnService.prepare(this@V2NoxTileService) != null || server == null) {
                            openApp()
                        } else {
                            ContextCompat.startForegroundService(
                                this@V2NoxTileService,
                                Intent(this@V2NoxTileService, V2NoxVpnService::class.java)
                                    .setAction(V2NoxVpnService.ACTION_START),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopVpn() {
        startService(Intent(this, V2NoxVpnService::class.java).setAction(V2NoxVpnService.ACTION_STOP))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
