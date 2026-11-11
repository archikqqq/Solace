package com.v2nox.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import kotlinx.coroutines.delay

/** Main screen; the ViewModel owns VPN state and configuration mutations. */
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
    MainScreenContent(
        state = state,
        onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.toggleVpn(context) },
        onSelect = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.selectConfig(it) },
        onDelete = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteConfig(it) },
        onPing = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.refreshPing(it)
        },
        onImport = viewModel::importFromClipboard,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainScreenContent(
    state: MainUiState,
    onToggle: () -> Unit,
    onSelect: (ServerConfig) -> Unit,
    onDelete: (ServerConfig) -> Unit,
    onPing: (ServerConfig?) -> Unit,
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentGreeting = rememberGreeting()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(currentGreeting, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.vpnState == VpnState.CONNECTED) "Защищено · ${state.activeConfig?.name ?: "VPN"}"
                            else state.activeConfig?.name?.let { "Выбран сервер · $it" } ?: "Сеть не защищена",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf(String::isNotBlank)?.let(onImport)
                    }) { Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            ConnectionHero(state, onToggle, onPing = { onPing(state.activeConfig) })
            state.errorMessage?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Серверы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.savedConfigs.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.savedConfigs.isEmpty()) {
                Text("Добавьте конфигурацию из буфера обмена", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(state.savedConfigs, key = { it.id }) { config ->
                        ServerCard(
                            config,
                            selected = state.activeConfig?.id == config.id,
                            pingMs = state.pingResults[config.id],
                            onSelect = { onSelect(config) },
                            onDelete = { onDelete(config) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionHero(state: MainUiState, onToggle: () -> Unit, onPing: () -> Unit) {
    val connected = state.vpnState == VpnState.CONNECTED
    val connecting = state.vpnState == VpnState.CONNECTING || state.vpnState == VpnState.STOPPING
    val transition = rememberInfiniteTransition(label = "connection pulse")
    val pulse by transition.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val corner by animateFloatAsState(if (connected) 92f else 32f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f), label = "button shape")
    val emphasis by animateFloatAsState(if (connected) 1f else 0.94f, animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f), label = "hero emphasis")
    val scheme = MaterialTheme.colorScheme
    val buttonColor = if (connected) scheme.primaryContainer else scheme.surfaceContainerHighest
    val contentColor = if (connected) scheme.onPrimaryContainer else scheme.onSurface
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).scale(emphasis),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = if (connected) scheme.primaryContainer else scheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "Защищено" else "Не защищено", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (connected) scheme.onPrimaryContainer else scheme.onSurface)
                    Text(state.activeConfig?.name ?: "Выберите профиль для подключения", style = MaterialTheme.typography.bodyMedium, color = if (connected) scheme.onPrimaryContainer.copy(alpha = 0.78f) else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onPing),
                    shape = CircleShape,
                    color = if (connected) scheme.primary else scheme.surfaceContainerHigh,
                ) {
                    val ping = state.activeConfig?.let { state.pingResults[it.id] }
                    val pingLabel = when {
                        state.pingingServerId == state.activeConfig?.id -> "Пинг…"
                        ping == null -> "Пинг · Проверить"
                        ping < 0L -> "Пинг · ERR"
                        else -> "Пинг · $ping ms"
                    }
                    Text(pingLabel, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (connected) scheme.onPrimary else scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
                if (connecting) CircularProgressIndicator(Modifier.size(136.dp), strokeWidth = 3.dp, color = scheme.primary)
                Button(
                    onClick = onToggle,
                    enabled = !connecting,
                    modifier = Modifier.size(112.dp).scale(if (connecting) pulse else 1f),
                    shape = if (connected) CircleShape else RoundedCornerShape(corner.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = contentColor),
                    border = if (connected) null else BorderStroke(1.dp, scheme.outlineVariant),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp),
                    contentPadding = PaddingValues(0.dp),
                ) { Text(if (connected) "◈" else "⏻", style = MaterialTheme.typography.displayMedium) }
            }
            Text(
                when (state.vpnState) {
                    VpnState.CONNECTED -> "Нажмите, чтобы отключить"
                    VpnState.CONNECTING, VpnState.STOPPING -> "Подключение…"
                    else -> "Нажмите, чтобы подключиться"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (connected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(config: ServerConfig, selected: Boolean, pingMs: Long?, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.pointerInput(config.id) {
        var distance = 0f
        detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> distance += amount }, onDragEnd = {
            if (kotlin.math.abs(distance) > 120f) onDelete()
            distance = 0f
        }, onDragCancel = { distance = 0f })
    }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(config.protocol, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text("${config.address}:${config.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                when {
                    pingMs == null -> "— ms"
                    pingMs < 0L -> "ERR"
                    else -> "$pingMs ms"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (pingMs != null && pingMs < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadioButton(selected = selected, onClick = onSelect)
            IconButton(onClick = onDelete) { Text("×", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun rememberGreeting(): String {
    var greeting by remember { mutableStateOf(greetingFor(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            greeting = greetingFor(LocalTime.now())
            delay(60_000L)
        }
    }
    return greeting
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}
