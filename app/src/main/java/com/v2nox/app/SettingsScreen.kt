package com.v2nox.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
        TopAppBar(title = { Text("Настройки", style = MaterialTheme.typography.headlineMedium) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = FloatingDockContentPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                Text("ОФОРМЛЕНИЕ", Modifier.padding(start = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                SettingSwitch("Как в системе", "Автоматически менять светлую и тёмную тему", Icons.Rounded.BrightnessAuto,
                    checked = settings.followSystem, index = 0, count = 3,
                    onCheckedChange = { save { repository.setFollowSystem(it) } })
            }
            item {
                SettingSwitch("Тёмная тема", if (settings.followSystem) "Управляется системой" else "Комфортное оформление в темноте", Icons.Rounded.DarkMode,
                    checked = if (settings.followSystem) systemDarkTheme else settings.darkTheme,
                    enabled = !settings.followSystem, index = 1, count = 3,
                    onCheckedChange = { save { repository.setDarkTheme(it) } })
            }
            item {
                SettingSwitch("Цвета обоев", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Палитра Monet из вашей системы" else "Доступно с Android 12",
                    Icons.Rounded.Palette, checked = settings.dynamicColor, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    index = 2, count = 3, onCheckedChange = { save { repository.setDynamicColor(it) } })
            }
            item {
                Text("ПОДПИСКИ", Modifier.padding(start = 12.dp, top = 28.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                SettingSwitch("Представляться как Happ", "Использовать заголовки Happ при загрузке подписок", Icons.Rounded.Sync,
                    checked = happEnabled, index = 0, count = 1,
                    onCheckedChange = { save { HappSpoof.setEnabled(context, it) } })
            }
            if (saveError) item {
                Text("Не удалось сохранить настройки. Попробуйте ещё раз.", Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text("УПРАВЛЕНИЕ", Modifier.padding(start = 12.dp, top = 28.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Dashboard, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Плитка быстрого включения", style = MaterialTheme.typography.titleMedium)
                            Text("Включайте VPN из шторки", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(enabled = !tileRequestPending, onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            }) { Text("Добавить") }
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
            item {
                Text("О ПРИЛОЖЕНИИ", Modifier.padding(start = 12.dp, top = 28.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Solace", style = MaterialTheme.typography.titleMedium)
                            Text("Inter · Material 3 Expressive", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Настройки оформления не меняют параметры VPN и Xray.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, icon: ImageVector, checked: Boolean, index: Int, count: Int,
    enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit,
) {
    Surface(shape = groupedShape(index, count), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, thumbContent = {
                Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
            })
        }
    }
}
