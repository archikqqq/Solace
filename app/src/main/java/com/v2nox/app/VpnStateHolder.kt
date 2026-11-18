package com.v2nox.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateHolder {
    internal val mutableState = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = mutableState.asStateFlow()
}
