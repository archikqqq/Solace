package com.v2nox.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Скругления элемента внутри сгруппированной секции настроек: крайние элементы
 * получают большой радиус (24dp) с одной стороны и малый (4dp) — с другой,
 * средние элементы — малый радиус со всех сторон. Зазор между элементами
 * группы задаётся отдельно (Arrangement.spacedBy(2.dp)).
 */
private fun settingsGroupShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 4.dp
    if (count <= 1) return RoundedCornerShape(outer)
    val isFirst = index == 0
    val isLast = index == count - 1
    return RoundedCornerShape(
        topStart = if (isFirst) outer else inner,
        topEnd = if (isFirst) outer else inner,
        bottomStart = if (isLast) outer else inner,
        bottomEnd = if (isLast) outer else inner,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { AppearanceRepository(context) }
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val happEnabled by remember(context) { HappSpoof.enabled(context) }.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    val systemDarkTheme = isSystemInDarkTheme()
    var saveError by remember { mutableStateOf(false) }
    var tileResult by remember { mutableStateOf<String?>(null) }
    var tileRequestPending by remember { mutableStateOf(false) }
    fun save(operation: suspend () -> Unit) {
        scope.launch {
            try { operation(); saveError = false } catch (_: IOException) { saveError = true }
        }
    }
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    "Настройки",
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text("ОФОРМЛЕНИЕ", Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                ExpressiveSwitchRow(
                    title = "Как в системе",
                    subtitle = "Автоматически менять светлую и тёмную тему",
                    icon = { Icon(Icons.Rounded.BrightnessAuto, null) },
                    checked = settings.followSystem,
                    onCheckedChange = { save { repository.setFollowSystem(it) } },
                    shape = settingsGroupShape(0, 3),
                )
            }
            item {
                ExpressiveSwitchRow(
                    title = "Тёмная тема",
                    subtitle = if (settings.followSystem) "Управляется системой" else "Комфортное оформление в темноте",
                    icon = { Icon(Icons.Rounded.DarkMode, null) },
                    checked = if (settings.followSystem) systemDarkTheme else settings.darkTheme,
                    enabled = !settings.followSystem,
                    onCheckedChange = { save { repository.setDarkTheme(it) } },
                    shape = settingsGroupShape(1, 3),
                )
            }
            item {
                ExpressiveSwitchRow(
                    title = "Цвета обоев",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Палитра Monet из вашей системы" else "Доступно с Android 12",
                    icon = { Icon(Icons.Rounded.Palette, null) },
                    checked = settings.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = { save { repository.setDynamicColor(it) } },
                    shape = settingsGroupShape(2, 3),
                )
            }
            item {
                Text("ПОДПИСКИ", Modifier.padding(top = 28.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                ExpressiveSwitchRow(
                    title = "Представляться как Happ",
                    subtitle = "Использовать заголовки Happ при загрузке подписок",
                    icon = { Icon(Icons.Rounded.Sync, null) },
                    checked = happEnabled,
                    onCheckedChange = { save { HappSpoof.setEnabled(context, it) } },
                    shape = settingsGroupShape(0, 2),
                )
            }
            item {
                ExpressiveSwitchRow(
                    title = "Маршрутизация для России",
                    subtitle = "Пускать сайты и сервисы РФ напрямую без VPN",
                    icon = { Icon(Icons.Rounded.Language, null) },
                    checked = settings.routeRussiaDirect,
                    onCheckedChange = { save { repository.setRouteRussiaDirect(it) } },
                    shape = settingsGroupShape(1, 2),
                )
            }
            if (saveError) item {
                Text("Не удалось сохранить настройки. Попробуйте ещё раз.", Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text("УПРАВЛЕНИЕ", Modifier.padding(top = 28.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                ExpressiveCard(shape = settingsGroupShape(0, 1)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Dashboard, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Плитка быстрого включения", style = MaterialTheme.typography.titleMedium)
                            Text("Включайте VPN из шторки", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ExpressiveActionButton(
                                text = "Добавить",
                                enabled = !tileRequestPending,
                                onClick = {
                                    tileRequestPending = true
                                    tileResult = null
                                    try {
                                        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                            ComponentName(context, V2NoxTileService::class.java), "Solace",
                                            Icon.createWithResource(context, R.drawable.ic_tile_off), context.mainExecutor,
                                        ) { result ->
                                            tileRequestPending = false
                                            tileResult = when (result) {
                                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Плитка добавлена."
                                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Плитка уже добавлена."
                                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Добавление плитки отклонено."
                                                else -> "Не удалось добавить плитку. Попробуйте ещё раз."
                                            }
                                        }
                                    } catch (_: RuntimeException) {
                                        tileRequestPending = false
                                        tileResult = "Не удалось добавить плитку. Попробуйте ещё раз."
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) item {
                Text("Потяните шторку вниз → ✎ → перетащите Solace в панель", Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            tileResult?.let { result ->
                item {
                    Text(result, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
