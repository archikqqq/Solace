package com.v2nox.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SolaceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ServerRepository(context)
                val name = combine(repository.servers, repository.activeServerId) { servers, id ->
                    servers.firstOrNull { it.id == id }?.name
                }.first()
                updateAll(context, VpnStateHolder.state.value, name)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun updateAll(context: Context, state: VpnState, serverName: String?) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, SolaceWidgetProvider::class.java)).forEach { id ->
                val square = views(context, R.layout.solace_widget, state, serverName)
                val wide = views(context, R.layout.solace_widget_wide, state, serverName)
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RemoteViews(mapOf(SizeF(140f, 140f) to square, SizeF(240f, 56f) to wide))
                } else {
                    val options = manager.getAppWidgetOptions(id)
                    if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) < 110 &&
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 220) wide else square
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun views(context: Context, layout: Int, state: VpnState, serverName: String?): RemoteViews {
            val connected = state == VpnState.CONNECTED
            val busy = state == VpnState.CONNECTING || state == VpnState.STOPPING
            val status = when (state) {
                VpnState.CONNECTED -> R.string.widget_connected
                VpnState.CONNECTING -> R.string.widget_connecting
                VpnState.STOPPING -> R.string.widget_stopping
                VpnState.DISCONNECTED -> R.string.widget_disconnected
            }
            val foreground = context.getColor(when {
                connected -> R.color.widget_on_primary_container
                busy -> R.color.widget_on_secondary_container
                else -> R.color.widget_on_surface
            })
            return RemoteViews(context.packageName, layout).apply {
                setInt(R.id.widget_root, "setBackgroundResource", when {
                    connected -> R.drawable.widget_container_active
                    busy -> R.drawable.widget_container_busy
                    else -> R.drawable.widget_container
                })
                setTextViewText(R.id.widget_status, context.getString(status))
                setTextViewText(R.id.widget_server, serverName ?: context.getString(R.string.widget_no_server))
                setTextColor(R.id.widget_status, foreground)
                setTextColor(R.id.widget_server, foreground)
                setInt(R.id.widget_logo, "setColorFilter", foreground)
                setInt(R.id.widget_power, "setBackgroundResource", if (connected) R.drawable.widget_power_active else R.drawable.widget_power)
                setInt(R.id.widget_power, "setColorFilter", context.getColor(if (connected) R.color.widget_on_primary else R.color.widget_on_primary_container))
                setContentDescription(R.id.widget_power, context.getString(
                    if (connected || state == VpnState.CONNECTING) R.string.widget_turn_off else R.string.widget_turn_on,
                ))
                setBoolean(R.id.widget_power, "setEnabled", state != VpnState.STOPPING)
                setOnClickPendingIntent(R.id.widget_power, PendingIntent.getForegroundService(
                    context, 4100,
                    Intent(context, V2NoxVpnService::class.java).setAction(V2NoxVpnService.ACTION_WIDGET_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ))
            }
        }
    }
}
