package com.v2nox.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SolaceApplication : Application() {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val repository = ServerRepository(this)
        widgetScope.launch {
            combine(VpnStateHolder.state, repository.servers, repository.activeServerId) { state, servers, id ->
                state to servers.firstOrNull { it.id == id }?.name
            }.distinctUntilChanged().collect { (state, name) ->
                SolaceWidgetProvider.updateAll(this@SolaceApplication, state, name)
            }
        }
    }
}
