package com.v2nox.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Карточка сервера/профиля в списке [ProfilesScreen]. Структура как в обычном списке:
 * Material-карточка с групповым скруглением [groupedShape], обводка у выбранного профиля.
 * Результат пинга выводится одной строкой среди бейджей, без отдельных элементов.
 */
@Composable
internal fun ProfileCard(
    config: ServerConfig, index: Int, count: Int, selected: Boolean, pingMs: Long?, pinging: Boolean,
    onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "profile selection",
    )
    val border by animateColorAsState(if (selected) scheme.primary else Color.Transparent, spring(), label = "profile outline")
    val details = remember(config) { profileBadges(config) }
    val ping = when {
        pinging -> "Проверяем…" to scheme.onSurfaceVariant
        pingMs == null -> null
        pingMs < 0 -> "Недоступен" to scheme.error
        pingMs >= 300 -> "$pingMs мс" to scheme.error
        pingMs >= 150 -> "$pingMs мс" to scheme.tertiary
        else -> "$pingMs мс" to scheme.onSurfaceVariant
    }
    Card(onClick = onSelect, shape = groupedShape(index, count), colors = CardDefaults.cardColors(
        containerColor = container, contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface),
        border = BorderStroke(1.dp, border), modifier = modifier.fillMaxWidth().semantics {
            this.selected = selected
            role = Role.RadioButton
        }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Dns, null,
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProfileBadge(config.protocol.uppercase())
                        details.forEach { ProfileBadge(it) }
                        ping?.let { (text, tint) ->
                            Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold, color = tint)
                        }
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Edit, "Переименовать ${config.name}") }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, "Удалить ${config.name}") }
            }
        }
    }
}

@Composable
private fun ProfileBadge(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}
