package com.v2nox.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
internal fun Dashboard(
    state: MainUiState,
    onToggle: () -> Unit,
    onServers: () -> Unit,
    onPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = FloatingDockContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Text("V2NOX", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(rememberDashboardGreeting(), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            ConnectionStatus(state.vpnState)
        }
        Spacer(Modifier.height(32.dp))
        ExpressivePowerButton(state.vpnState, onToggle)
        Spacer(Modifier.height(20.dp))
        Text(
            when (state.vpnState) {
                VpnState.CONNECTED -> "Вы под защитой"
                VpnState.CONNECTING -> "Создаём защищённое соединение"
                VpnState.STOPPING -> "Завершаем соединение"
                VpnState.DISCONNECTED -> "Подключитесь к свободному интернету"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (state.vpnState) {
                VpnState.CONNECTED -> "Нажмите, чтобы отключить VPN"
                VpnState.CONNECTING, VpnState.STOPPING -> "Это займёт немного времени"
                VpnState.DISCONNECTED -> "Нажмите кнопку для подключения"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        CurrentServerCard(state, onServers, onPing, Modifier.widthIn(max = 520.dp))
        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(24.dp), color = scheme.errorContainer,
            ) {
                Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ConnectionStatus(state: VpnState) {
    val connected = state == VpnState.CONNECTED
    Surface(
        shape = CircleShape,
        color = if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (connected) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon, null, Modifier.size(16.dp))
            Text(
                when (state) {
                    VpnState.DISCONNECTED -> "Отключено"
                    VpnState.CONNECTING -> "Подключение…"
                    VpnState.CONNECTED -> "Подключено"
                    VpnState.STOPPING -> "Отключение…"
                },
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ExpressivePowerButton(state: VpnState, onToggle: () -> Unit) {
    val busy = state == VpnState.CONNECTING || state == VpnState.STOPPING
    val connected = state == VpnState.CONNECTED
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "power press",
    )
    // Radii are expressed as percentages of the 188.dp button. The connected
    // state is an asymmetric, soft expressive shape rather than another circle.
    val corner by animateFloatAsState(
        when { connected -> 24f; busy -> 32f; else -> 50f },
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "power shape",
    )
    val accentCorner by animateFloatAsState(
        if (connected) 46f else if (busy) 32f else 50f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "power accent corners",
    )
    val shape = RoundedCornerShape(
        topStart = (1.88f * corner.coerceIn(0f, 50f)).dp,
        topEnd = (1.88f * accentCorner.coerceIn(0f, 50f)).dp,
        bottomEnd = (1.88f * corner.coerceIn(0f, 50f)).dp,
        bottomStart = (1.88f * accentCorner.coerceIn(0f, 50f)).dp,
    )
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (connected) scheme.primary else scheme.primaryContainer, tween(450), label = "power container")
    val content by animateColorAsState(if (connected) scheme.onPrimary else scheme.onPrimaryContainer, tween(450), label = "power content")
    Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
        if (busy) {
            val transition = rememberInfiniteTransition(label = "connecting halo")
            val pulse by transition.animateFloat(0.92f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "halo scale")
            Box(Modifier.size(220.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.35f }
                .border(2.dp, scheme.primary, shape))
            CircularProgressIndicator(Modifier.size(216.dp), color = scheme.primary, strokeWidth = 2.dp)
        }
        Button(
            onClick = onToggle,
            enabled = !busy,
            interactionSource = interactions,
            modifier = Modifier.size(188.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics { contentDescription = if (connected) "Отключить VPN" else "Подключить VPN" },
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = content,
                disabledContainerColor = container, disabledContentColor = content,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun CurrentServerCard(state: MainUiState, onServers: () -> Unit, onPing: () -> Unit, modifier: Modifier = Modifier) {
    val server = state.activeConfig
    val ping = server?.let { state.pingResults[it.id] }
    Card(
        onClick = onServers, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Текущий сервер", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Rounded.Dns, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(server?.name ?: "Выберите сервер", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(server?.protocol ?: "Добавьте профиль для подключения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Открыть серверы", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (server != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPing, enabled = state.pingingServerId != server.id,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        when {
                            state.pingingServerId == server.id -> "Проверяем пинг…"
                            ping == null -> "Проверить пинг"
                            ping < 0 -> "Недоступен · Повторить"
                            else -> "Пинг · $ping мс"
                        }, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDashboardGreeting(): String {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = LocalTime.now().hour
            delay(60_000)
        }
    }
    return greetingForHour(hour)
}

internal fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Доброе утро!"
    in 12..16 -> "Добрый день!"
    in 17..22 -> "Добрый вечер!"
    else -> "Доброй ночи!"
}
