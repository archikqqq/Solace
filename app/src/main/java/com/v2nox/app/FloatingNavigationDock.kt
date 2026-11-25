package com.v2nox.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2nox.app.ui.theme.SolaceMotion

internal enum class MainDestination(val label: String) {
    Home("Главная"), Servers("Профили"), Settings("Настройки"),
}

private val DockHeight = 64.dp
private val DockItemHeight = 48.dp
private const val DockPressedScale = 0.92f

/**
 * MD3 Expressive floating toolbar + detached action circle.
 * Left: pill with Home / Profiles tabs. Right: Settings circle.
 */
@Composable
internal fun FloatingNavigationDock(selected: MainDestination, onSelect: (MainDestination) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        val showLabel = maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavigationPill(
                selected = selected,
                showLabel = showLabel,
                onSelect = onSelect,
            )
            SettingsCircle(
                selected = selected == MainDestination.Settings,
                onClick = { onSelect(MainDestination.Settings) },
            )
        }
    }
}

@Composable
private fun NavigationPill(selected: MainDestination, showLabel: Boolean, onSelect: (MainDestination) -> Unit) {
    Surface(
        modifier = Modifier.height(DockHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockTab(
                destination = MainDestination.Home,
                icon = Icons.Rounded.Home,
                active = selected == MainDestination.Home,
                showLabel = showLabel,
                onClick = { onSelect(MainDestination.Home) },
            )
            DockTab(
                destination = MainDestination.Servers,
                icon = Icons.Rounded.Dns,
                active = selected == MainDestination.Servers,
                showLabel = showLabel,
                onClick = { onSelect(MainDestination.Servers) },
            )
        }
    }
}

/**
 * Tab inside the pill. Active tab = capsule with icon + animated label;
 * inactive tab = compact 48dp icon target.
 */
@Composable
private fun DockTab(
    destination: MainDestination,
    icon: ImageVector,
    active: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) DockPressedScale else 1f,
        animationSpec = SolaceMotion.fastSpatial(),
        label = "dock tab scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = SolaceMotion.defaultEffects(),
        label = "dock tab container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = SolaceMotion.defaultEffects(),
        label = "dock tab content",
    )
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(DockItemHeight)
            .clip(CircleShape)
            .background(containerColor)
            .selectable(
                selected = active,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (active && showLabel) null else destination.label,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        AnimatedVisibility(
            visible = active && showLabel,
            enter = fadeIn(SolaceMotion.defaultEffects()) + expandHorizontally(SolaceMotion.defaultSpatial()),
            exit = fadeOut(SolaceMotion.fastSpatial()) + shrinkHorizontally(SolaceMotion.defaultSpatial()),
        ) {
            Text(
                text = destination.label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** Detached 64dp action circle with the Settings gear. */
@Composable
private fun SettingsCircle(selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) DockPressedScale else 1f,
        animationSpec = SolaceMotion.fastSpatial(),
        label = "settings scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = SolaceMotion.defaultEffects(),
        label = "settings container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = SolaceMotion.defaultEffects(),
        label = "settings content",
    )
    Surface(
        modifier = Modifier
            .size(DockHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = MainDestination.Settings.label,
                tint = contentColor,
            )
        }
    }
}
