package com.v2nox.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Presentation only: connection and import operations remain owned by the ViewModel. */
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onVpnPermissionResult(context, result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.permissionRequests.collect { permissionLauncher.launch(it) }
    }
    var destination by rememberSaveable { mutableStateOf(MainDestination.Home) }
    BackHandler(enabled = destination != MainDestination.Home) { destination = MainDestination.Home }
    // The dock overlays content; scroll containers reserve trailing space for it.
    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ) { padding ->
            val screenModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            when (destination) {
                MainDestination.Home -> Dashboard(
                    state = state,
                    onToggle = { if (state.vpnState == VpnState.DISCONNECTED) confirmHaptic(view) else contextHaptic(view); viewModel.toggleVpn(context) },
                    onServers = { tabHaptic(view); destination = MainDestination.Servers },
                    onPing = { contextHaptic(view); viewModel.refreshPing(state.activeConfig) },
                    onQuickSelect = { contextHaptic(view); viewModel.selectConfig(it) },
                    modifier = screenModifier,
                )
                MainDestination.Servers -> ProfilesScreen(
                    state = state,
                    onSelect = { contextHaptic(view); viewModel.selectConfig(it) },
                    onDelete = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteConfig(it) },
                    onRename = viewModel::renameConfig,
                    onPingAll = viewModel::refreshAllPings,
                    onImport = viewModel::importFromClipboard,
                    onAddSubscription = viewModel::addSubscription,
                    onRefreshSubscription = viewModel::refreshSubscription,
                    onDeleteSubscription = viewModel::deleteSubscription,
                    modifier = screenModifier,
                )
                MainDestination.Settings -> SettingsScreen(modifier = screenModifier)
            }
        }
        FloatingNavigationDock(
            selected = destination,
            onSelect = { if (it != destination) { tabHaptic(view); destination = it } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal val FloatingDockContentPadding = 112.dp

private fun confirmHaptic(view: View) {
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
}

private fun contextHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/** Tab switch feedback: SEGMENT_TICK (API 30+) matches the segmented-control feel; falls back to CONTEXT_CLICK. */
private fun tabHaptic(view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CONTEXT_CLICK
    )
}
