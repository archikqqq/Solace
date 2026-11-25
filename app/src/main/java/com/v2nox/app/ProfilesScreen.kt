package com.v2nox.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesScreen(
    state: MainUiState,
    onSelect: (ServerConfig) -> Unit,
    onDelete: (ServerConfig) -> Unit,
    onRename: (ServerConfig, String) -> Unit,
    onPingAll: () -> Unit,
    onImport: (String) -> Unit,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showImport by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSubscriptionId by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var clipboardError by remember { mutableStateOf<String?>(null) }
    val fromClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        }
        if (text.isNullOrBlank()) clipboardError = "В буфере обмена нет конфигурации профиля"
        else onImport(text)
    }
    LaunchedEffect(clipboardError) {
        clipboardError?.let { snackbar.showSnackbar(it); clipboardError = null }
    }
    Column(modifier) {
        TopAppBar(
            title = {
                Column {
                    Text("Серверы", style = MaterialTheme.typography.headlineMedium)
                    Text("Ваши профили · ${state.savedConfigs.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = {
                FilledTonalIconButton(onClick = fromClipboard) { Icon(Icons.Rounded.ContentPaste, "Импорт из буфера обмена") }
                FilledIconButton(onClick = { showImport = true }) { Icon(Icons.Rounded.Add, "Добавить профиль") }
                Spacer(Modifier.width(12.dp))
            },
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = FloatingDockContentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.errorMessage?.let { message ->
                    item(key = "error") {
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Info, null)
                                Text(message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (state.savedConfigs.isEmpty() && state.subscriptions.isEmpty()) {
                    item(key = "empty") { EmptyProfiles(fromClipboard, onAdd = { showImport = true }) }
                } else {
                    item(key = "actions") {
                        Column(Modifier.padding(bottom = 16.dp)) {
                            FilledTonalButton(onClick = onPingAll, enabled = !state.isPingingAll && state.savedConfigs.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Rounded.NetworkCheck, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.isPingingAll) "Проверяем серверы…" else "Проверить пинг всех")
                            }
                            Text("ПРОФИЛИ", Modifier.padding(start = 12.dp, top = 24.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    state.subscriptions.forEach { subscription ->
                        val configs = state.savedConfigs.filter { it.subscriptionId == subscription.id }
                        item(key = "subscription:${subscription.id}") {
                            SubscriptionHeader(subscription, refreshing = state.refreshingSubscriptionId == subscription.id,
                                refreshEnabled = state.refreshingSubscriptionId == null,
                                onRefresh = { onRefreshSubscription(subscription.id) },
                                onDelete = { deleteSubscriptionId = subscription.id },
                                modifier = Modifier.animateItem())
                        }
                        itemsIndexed(configs, key = { _, config -> config.id }) { index, config ->
                            ProfileCard(
                                config = config, index = index, count = configs.size,
                                selected = state.activeConfig?.identityKey == config.identityKey,
                                pingMs = state.pingResults[config.id], pinging = state.pingingServerId == config.id,
                                onSelect = { onSelect(config) }, onEdit = { editId = config.id }, onDelete = { deleteId = config.id },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    val manualConfigs = state.savedConfigs.filter { it.subscriptionId == null }
                    if (manualConfigs.isNotEmpty()) {
                        item(key = "manual") {
                            Text("Вручную", Modifier.padding(start = 12.dp, top = 20.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        itemsIndexed(manualConfigs, key = { _, config -> config.id }) { index, config ->
                            ProfileCard(
                                config = config, index = index, count = manualConfigs.size,
                                selected = state.activeConfig?.identityKey == config.identityKey,
                                pingMs = state.pingResults[config.id], pinging = state.pingingServerId == config.id,
                                onSelect = { onSelect(config) }, onEdit = { editId = config.id }, onDelete = { deleteId = config.id },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = "hint") {
                        Text("Пинг — время TCP-соединения с сервером, не скорость VPN.",
                            Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = FloatingDockContentPadding))
        }
    }
    if (showImport) ImportProfileSheet(onDismiss = { showImport = false },
        onImport = { onImport(it); showImport = false },
        onAddSubscription = { onAddSubscription(it); showImport = false })
    state.subscriptions.firstOrNull { it.id == deleteSubscriptionId }?.let { subscription ->
        AlertDialog(
            onDismissRequest = { deleteSubscriptionId = null }, shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.DeleteOutline, null) }, title = { Text("Удалить подписку?") },
            text = { Text("«${subscription.name}» и все её серверы будут удалены.") },
            confirmButton = {
                TextButton(onClick = { onDeleteSubscription(subscription.id); deleteSubscriptionId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteSubscriptionId = null }) { Text("Отмена") } },
        )
    }
    state.savedConfigs.firstOrNull { it.id == deleteId }?.let { config ->
        AlertDialog(
            onDismissRequest = { deleteId = null }, shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.DeleteOutline, null) }, title = { Text("Удалить профиль?") },
            text = { Text("«${config.name}» будет удалён из списка сохранённых серверов.") },
            confirmButton = {
                TextButton(onClick = { onDelete(config); deleteId = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } },
        )
    }
    state.savedConfigs.firstOrNull { it.id == editId }?.let { config ->
        RenameProfileSheet(config, onDismiss = { editId = null }, onSave = { onRename(config, it); editId = null })
    }
}

/**
 * Скругления элемента группы: крайние элементы получают большой радиус (24dp),
 * внутренние — малый (6dp). Для одиночного элемента (count == 1) скругляются все углы большим радиусом.
 */
internal fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    val isFirst = index == 0
    val isLast = index >= count - 1
    return RoundedCornerShape(
        topStart = if (isFirst) outer else inner,
        topEnd = if (isFirst) outer else inner,
        bottomStart = if (isLast) outer else inner,
        bottomEnd = if (isLast) outer else inner,
    )
}

@Composable
private fun SubscriptionHeader(subscription: SubscriptionConfig, refreshing: Boolean, refreshEnabled: Boolean,
    onRefresh: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subscription.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subscription.totalBytes > 0) {
                    val gb = 1024.0 * 1024 * 1024
                    val used = subscription.uploadBytes.toDouble() + subscription.downloadBytes.toDouble()
                    Text(String.format(Locale.getDefault(), "Использовано %.2f / %.2f ГБ", used / gb, subscription.totalBytes / gb),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (subscription.expireEpochSeconds > 0) {
                    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(subscription.expireEpochSeconds * 1000))
                    Text("Действует до $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                subscription.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                if (refreshing) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "Обновление подписки" }, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, "Обновить ${subscription.name}")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "Меню подписки ${subscription.name}") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Удалить подписку") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

/** Read existing parser metadata for display only; never mutates or reinterprets a profile. */
internal fun profileBadges(config: ServerConfig): List<String> {
    val values = config.rawUrl?.let { runCatching { ServerLinkParser.parseRawLink(it).values }.getOrNull() }.orEmpty()
    val stream = config.outboundJson?.let {
        runCatching { org.json.JSONObject(it).getJSONObject("outbound").optJSONObject("streamSettings") }.getOrNull()
    }
    val transport = stream?.optString("network")?.takeIf(String::isNotBlank)
        ?: if (config.protocol.equals("VMESS", ignoreCase = true)) values["net"] else values["type"]
    val security = config.security ?: stream?.optString("security")?.takeIf(String::isNotBlank)
        ?: values["security"] ?: values["tls"]
    return listOfNotNull(transport?.takeIf { it.isNotBlank() && it != "none" }?.uppercase(),
        security?.takeIf { it.isNotBlank() && it != "none" }?.uppercase()).distinct()
}

@Composable
private fun PingChip(ping: Long?, busy: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Semantic hues are blended into Monet surfaces rather than using neon fills.
    val dark = scheme.surface.luminance() < 0.5f
    val hue = when {
        ping == null || busy -> scheme.onSurfaceVariant
        ping < 0 || ping >= 300 -> if (dark) Color(0xFFFFB4AB) else Color(0xFF9C2825)
        ping >= 150 -> if (dark) Color(0xFFE8C36A) else Color(0xFF745800)
        else -> if (dark) Color(0xFFA2D5AC) else Color(0xFF256334)
    }
    Surface(shape = CircleShape, color = lerp(scheme.surfaceContainer, hue, 0.13f), contentColor = hue) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (busy) ExpressiveWavyIndicator(
                Modifier.width(40.dp), color = hue, amplitude = 2.dp, wavelength = 14.dp, strokeWidth = 3.dp,
            )
            else Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(16.dp))
            Text(when { busy -> "Проверяем…"; ping == null -> "Нет замера"; ping < 0 -> "Недоступен"; else -> "$ping мс" },
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyProfiles(onClipboard: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(topStart = 40.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 40.dp),
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Rounded.CloudQueue, null, Modifier.padding(28.dp).size(56.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text("Здесь начнётся подключение", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Скопируйте ссылку или JSON-конфигурацию и добавьте свой первый профиль.", Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        ExpressiveActionButton(
            text = "Добавить из буфера",
            onClick = onClipboard,
            icon = { Icon(Icons.Rounded.ContentPaste, null) },
        )
        TextButton(onClick = onAdd) { Text("Ввести конфигурацию вручную") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportProfileSheet(onDismiss: () -> Unit, onImport: (String) -> Unit, onAddSubscription: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var subscriptionMode by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy))
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Icon(Icons.Rounded.AddLink, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text("Новый профиль", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall)
            ExpressiveButtonGroup(
                options = listOf("Сервер", "Подписка по URL"),
                selectedIndex = if (subscriptionMode) 1 else 0,
                onSelect = { subscriptionMode = it == 1; error = null },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(if (subscriptionMode) "Вставьте HTTP- или HTTPS-ссылку на подписку." else "Вставьте ссылку на сервер или JSON-конфигурацию.",
                Modifier.padding(top = 8.dp, bottom = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = text, onValueChange = { text = it; error = null }, label = { Text(if (subscriptionMode) "URL подписки" else "Ссылка или JSON") },
                placeholder = { Text(if (subscriptionMode) "https://…" else "vless://… · vmess://… · trojan://…") }, minLines = 3, maxLines = 6,
                isError = error != null, supportingText = { error?.let { Text(it) } }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val pasted = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                if (pasted.isNullOrBlank()) error = "Буфер обмена пуст" else { text = pasted; error = null }
            }) { Icon(Icons.Rounded.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text("Вставить из буфера") }
            ExpressiveActionButton(
                text = if (subscriptionMode) "Добавить подписку" else "Добавить профиль",
                onClick = {
                    if (subscriptionMode) onAddSubscription(text.trim())
                    else {
                        // Only valid input dismisses the sheet.
                        if (isSubscriptionUrl(text)) onAddSubscription(text.trim())
                        else runCatching { parseProfileImport(text) }.onSuccess { onImport(text) }
                            .onFailure { error = it.message ?: "Не удалось прочитать профиль" }
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameProfileSheet(config: ServerConfig, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(config.id) { mutableStateOf(config.name) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary)
            Text("Имя профиля", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Text("Меняется только название. Параметры подключения останутся прежними.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Сохранить") }
        }
    }
}
