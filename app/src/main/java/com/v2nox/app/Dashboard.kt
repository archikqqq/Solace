package com.v2nox.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    onQuickSelect: (ServerConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val busy = state.vpnState == VpnState.CONNECTING || state.vpnState == VpnState.STOPPING
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = FloatingDockContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(R.drawable.solace_icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(scheme.onSurface),
                )
                Text("Solace", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            }
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
        if (busy) {
            Spacer(Modifier.height(16.dp))
            ExpressiveWavyIndicator(modifier = Modifier.widthIn(max = 220.dp))
        }
        Spacer(Modifier.height(32.dp))
        CurrentServerCard(state, onServers, onPing, Modifier.widthIn(max = 520.dp))
        if (state.savedConfigs.size > 1) {
            Spacer(Modifier.height(16.dp))
            QuickServerRow(state, onQuickSelect, Modifier.widthIn(max = 520.dp).fillMaxWidth())
        }
        state.errorMessage?.let { message ->
            ExpressiveCard(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                containerColor = scheme.errorContainer,
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ConnectionStatus(state: VpnState) {
    val tone = when (state) {
        VpnState.CONNECTED -> PillTone.Good
        VpnState.CONNECTING, VpnState.STOPPING -> PillTone.Warning
        VpnState.DISCONNECTED -> PillTone.Neutral
    }
    ExpressiveStatusPill(
        label = when (state) {
            VpnState.DISCONNECTED -> "Отключено"
            VpnState.CONNECTING -> "Подключение…"
            VpnState.CONNECTED -> "Защищено"
            VpnState.STOPPING -> "Отключение…"
        },
        tone = tone,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ExpressivePowerButton(state: VpnState, onToggle: () -> Unit) {
    val busy = state == VpnState.CONNECTING || state == VpnState.STOPPING
    val connected = state == VpnState.CONNECTED
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        ExpressiveConnectButton(
            isConnected = connected,
            isConnecting = busy,
            // Блокировка повторного нажатия во время переходного состояния (как было enabled = !busy).
            onClick = { if (!busy) onToggle() },
            size = 152.dp,
            modifier = Modifier.semantics {
                contentDescription = if (connected) "Отключить VPN" else "Подключить VPN"
                if (busy) disabled()
            },
            icon = {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(46.dp))
            },
        )
    }
}

@Composable
private fun CurrentServerCard(state: MainUiState, onServers: () -> Unit, onPing: () -> Unit, modifier: Modifier = Modifier) {
    val server = state.activeConfig
    val ping = server?.let { state.pingResults[it.id] }
    val pinging = server != null && state.pingingServerId == server.id
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onServers,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(20.dp),
    ) {
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
            ExpressiveStatusPill(
                label = when {
                    pinging -> "Проверяем пинг…"
                    ping == null -> "Проверить пинг"
                    ping < 0 -> "Недоступен · Повторить"
                    else -> "Пинг · $ping мс"
                },
                tone = when {
                    pinging || ping == null -> PillTone.Neutral
                    ping < 0 -> PillTone.Error
                    ping < 200 -> PillTone.Good
                    else -> PillTone.Warning
                },
                // Во время проверки пинга нажатие отключено (как было enabled = pingingServerId != id).
                onClick = if (pinging) null else onPing,
            )
            if (pinging) {
                Spacer(Modifier.height(12.dp))
                ExpressiveWavyIndicator()
            }
        }
    }
}

@Composable
private fun QuickServerRow(state: MainUiState, onSelect: (ServerConfig) -> Unit, modifier: Modifier = Modifier) {
    val active = state.activeConfig?.identityKey
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.savedConfigs.forEach { config ->
            FilterChip(
                selected = config.identityKey == active,
                onClick = { onSelect(config) },
                label = { Text(config.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (config.identityKey == active) {
                    { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)) }
                } else null,
                shape = CircleShape,
            )
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
