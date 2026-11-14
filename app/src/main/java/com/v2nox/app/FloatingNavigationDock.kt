package com.v2nox.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class MainDestination(val label: String) {
    Home("Главная"), Servers("Серверы"), Settings("Настройки"),
}

@Composable
internal fun FloatingNavigationDock(selected: MainDestination, onSelect: (MainDestination) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val showLabel = maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.selectableGroup().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainDestination.entries.forEach { destination ->
                    val active = selected == destination
                    val color by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "dock container",
                    )
                    Surface(shape = CircleShape, color = color) {
                        Row(
                            Modifier.selectable(active, role = Role.Tab, onClick = { onSelect(destination) })
                                .heightIn(min = 48.dp)
                                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                 .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                when (destination) {
                                    MainDestination.Home -> Icons.Rounded.Home
                                    MainDestination.Servers -> Icons.Rounded.Dns
                                    MainDestination.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = if (active && showLabel) null else destination.label,
                                tint = contentColor,
                            )
                            AnimatedVisibility(
                                visible = active && showLabel,
                                enter = expandHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = shrinkHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut(),
                            ) {
                                Text(
                                    destination.label,
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
