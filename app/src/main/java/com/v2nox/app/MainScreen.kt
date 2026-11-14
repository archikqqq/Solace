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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Presentation only: connection and import operations remain owned by the ViewModel. */
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
                    onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.toggleVpn(context) },
                    onServers = { destination = MainDestination.Servers },
                    onPing = { viewModel.refreshPing(state.activeConfig) },
                    modifier = screenModifier,
                )
                MainDestination.Servers -> ProfilesScreen(
                    state = state,
                    onSelect = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.selectConfig(it) },
                    onDelete = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteConfig(it) },
                    onRename = viewModel::renameConfig,
                    onPingAll = viewModel::refreshAllPings,
                    onImport = viewModel::importFromClipboard,
                    modifier = screenModifier,
                )
                MainDestination.Settings -> SettingsScreen(modifier = screenModifier)
            }
        }
        FloatingNavigationDock(destination, onSelect = { destination = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

internal val FloatingDockContentPadding = 112.dp
