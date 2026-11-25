package com.v2nox.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import com.v2nox.app.ui.theme.SolaceMotion
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme.typography
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

// ---------------------------------------------------------------------------
// Motion adapter поверх SolaceMotion (Theme.kt).
// Все компоненты берут спецификации только отсюда — менять в одном месте.
//   press    — мгновенный отклик на нажатие (scale)      -> fastSpatial
//   morph    — морфинг формы / скругления (Dp)            -> defaultSpatial
//   color    — смена цветов контейнера и контента         -> defaultEffects
//   progress — плавное движение прогресса волны           -> slowSpatial
// ---------------------------------------------------------------------------
private object SolaceExpressiveMotion {
    val press: FiniteAnimationSpec<Float> = SolaceMotion.fastSpatial()
    val morph: FiniteAnimationSpec<Dp> = SolaceMotion.defaultSpatial()
    val color: FiniteAnimationSpec<Color> = SolaceMotion.defaultEffects()
    val progress: FiniteAnimationSpec<Float> = SolaceMotion.slowSpatial()
}

// ---------------------------------------------------------------------------
// Haptics helpers: LongPress — основные действия, TextHandleMove — выбор/переключение.
// ---------------------------------------------------------------------------
private fun HapticFeedback.actionTick() =
    performHapticFeedback(HapticFeedbackType.LongPress)

private fun HapticFeedback.selectionTick() =
    performHapticFeedback(HapticFeedbackType.TextHandleMove)

// ===========================================================================
// 1. ExpressiveConnectButton
// Круг в покое <-> squircle при подключении/активности, пружинное сжатие
// при нажатии и пульсация при connecting.
// ===========================================================================
@Composable
fun ExpressiveConnectButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    icon: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = isConnected || isConnecting

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = SolaceExpressiveMotion.press,
        label = "connectPressScale"
    )
    val corner by animateDpAsState(
        targetValue = if (active) 36.dp else size / 2f,
        animationSpec = SolaceExpressiveMotion.morph,
        label = "connectCorner"
    )
    val container by animateColorAsState(
        targetValue = when {
            isConnected -> cs.primary
            isConnecting -> cs.primaryContainer
            else -> cs.surfaceContainerHigh
        },
        animationSpec = SolaceExpressiveMotion.color,
        label = "connectContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isConnected -> cs.onPrimary
            isConnecting -> cs.onPrimaryContainer
            else -> cs.onSurface
        },
        animationSpec = SolaceExpressiveMotion.color,
        label = "connectContent"
    )

    val infinite = rememberInfiniteTransition(label = "connectPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connectPulseScale"
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connectPulseAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            Box(
                Modifier
                    .size(size)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .border(2.dp, cs.primary, RoundedCornerShape(corner))
            )
        }
        Box(
            Modifier
                .size(size)
                .scale(pressScale)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = {
                        haptic.actionTick()
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
    }
}

// ===========================================================================
// 2. ExpressiveWavyIndicator
// Волнистый индикатор на Canvas. progress == null -> indeterminate.
// ===========================================================================
@Composable
fun ExpressiveWavyIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    amplitude: Dp = 4.dp,
    wavelength: Dp = 36.dp,
    strokeWidth: Dp = 6.dp,
) {
    val tau = (2 * PI).toFloat()

    val infinite = rememberInfiniteTransition(label = "wavyPhase")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = tau,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyPhaseValue"
    )

    // Для indeterminate: «голова» волны ходит туда-обратно.
    val sweep by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavySweep"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 1f,
        animationSpec = SolaceExpressiveMotion.progress,
        label = "wavyProgress"
    )

    val fraction = if (progress == null) sweep else animatedProgress

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(amplitude * 2f + strokeWidth)
            .semantics {
                progressBarRangeInfo = if (progress != null) {
                    ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                } else {
                    ProgressBarRangeInfo.Indeterminate
                }
            }
    ) {
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val strokePx = strokeWidth.toPx()
        val midY = size.height / 2f
        val endX = size.width * fraction
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Трек (нешироко прогресс) — прямая линия.
        if (endX < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(endX, midY),
                end = Offset(size.width, midY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        // Активная волна от 0 до endX.
        if (endX > 0f) {
            val path = Path()
            path.moveTo(0f, midY + ampPx * sin(phase))
            var x = 0f
            val step = 2f
            while (x < endX) {
                val nx = min(x + step, endX)
                val y = midY + ampPx * sin((nx / waveLenPx) * tau + phase)
                path.lineTo(nx, y)
                x = nx
            }
            drawPath(path = path, color = color, style = stroke)
        }
    }
}

// ===========================================================================
// 3. ExpressiveButtonGroup (Connected Button Group)
// Внешние углы группы фиксированы, внутренние углы выбранного сегмента
// морфятся к более круглой форме.
// ===========================================================================
@Composable
fun ExpressiveButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val lastIndex = options.lastIndex

    Row(
        modifier = modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()

            val innerRadius by animateDpAsState(
                targetValue = if (selected) 28.dp else 8.dp,
                animationSpec = SolaceExpressiveMotion.morph,
                label = "groupRadius$index"
            )
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = SolaceExpressiveMotion.press,
                label = "groupScale$index"
            )
            val bg by animateColorAsState(
                targetValue = if (selected) cs.primary else cs.surfaceContainerHigh,
                animationSpec = SolaceExpressiveMotion.color,
                label = "groupBg$index"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) cs.onPrimary else cs.onSurface,
                animationSpec = SolaceExpressiveMotion.color,
                label = "groupFg$index"
            )

            val outer = 24.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) outer else innerRadius,
                bottomStart = if (index == 0) outer else innerRadius,
                topEnd = if (index == lastIndex) outer else innerRadius,
                bottomEnd = if (index == lastIndex) outer else innerRadius
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scale(pressScale)
                    .clip(shape)
                    .background(bg)
                    .selectable(
                        selected = selected,
                        interactionSource = source,
                        indication = LocalIndication.current,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) haptic.selectionTick()
                            onSelect(index)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = typography.labelLarge,
                    color = fg
                )
            }
        }
    }
}

// ===========================================================================
// 4. ExpressiveCard
// Крупное скругление 28dp (24dp при нажатии), пружинный scale-отклик.
// ===========================================================================
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    shape: androidx.compose.ui.graphics.Shape? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val clickable = onClick != null
    val isDown = pressed && clickable

    val scale by animateFloatAsState(
        targetValue = if (isDown) 0.95f else 1f,
        animationSpec = SolaceExpressiveMotion.press,
        label = "cardScale"
    )
    val radius by animateDpAsState(
        targetValue = if (isDown) 24.dp else 28.dp,
        animationSpec = SolaceExpressiveMotion.morph,
        label = "cardRadius"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = {
                haptic.actionTick()
                onClick()
            }
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape ?: RoundedCornerShape(radius))
            .background(containerColor)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

// ===========================================================================
// 5. ExpressiveSwitchRow
// Строка настройки: иконка, заголовок, подзаголовок, M3 Switch, haptic.
// ===========================================================================
@Composable
fun ExpressiveSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = SolaceExpressiveMotion.press,
        label = "switchRowScale"
    )
    val bg by animateColorAsState(
        targetValue = if (checked) cs.secondaryContainer else cs.surfaceContainer,
        animationSpec = SolaceExpressiveMotion.color,
        label = "switchRowBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(bg)
            .selectable(
                selected = checked,
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onClick = {
                    haptic.selectionTick()
                    onCheckedChange(!checked)
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (checked) cs.onSecondaryContainer else cs.onSurfaceVariant
                ) {
                    icon()
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.titleMedium,
                color = if (checked) cs.onSecondaryContainer else cs.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = typography.bodyMedium,
                    color = if (checked) cs.onSecondaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // onCheckedChange = null: переключение обрабатывает вся строка (selectable).
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                disabledCheckedThumbColor = cs.onSurface.copy(alpha = 0.38f),
                disabledCheckedTrackColor = cs.surfaceVariant.copy(alpha = 0.6f),
                disabledCheckedBorderColor = cs.outline.copy(alpha = 0.38f),
                disabledUncheckedThumbColor = cs.onSurfaceVariant.copy(alpha = 0.38f),
                disabledUncheckedTrackColor = cs.surfaceContainerHighest,
                disabledUncheckedBorderColor = cs.outline.copy(alpha = 0.3f),
            ),
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize).alpha(0.38f)
                    )
                }
            }
        )
    }
}

// ===========================================================================
// 6. ExpressiveStatusPill
// Чип статуса/пинга с плавной анимацией цвета контейнера.
// ===========================================================================
enum class PillTone { Good, Warning, Error, Neutral }

@Composable
fun ExpressiveStatusPill(
    label: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    pingMs: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val (targetContainer, targetContent) = when (tone) {
        PillTone.Good -> cs.primaryContainer to cs.onPrimaryContainer
        PillTone.Warning -> cs.tertiaryContainer to cs.onTertiaryContainer
        PillTone.Error -> cs.errorContainer to cs.onErrorContainer
        PillTone.Neutral -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }

    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = SolaceExpressiveMotion.color,
        label = "pillContainer"
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = SolaceExpressiveMotion.color,
        label = "pillContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.94f else 1f,
        animationSpec = SolaceExpressiveMotion.press,
        label = "pillScale"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = {
                haptic.selectionTick()
                onClick()
            }
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(container)
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(content)
        )
        Text(
            text = label,
            style = typography.labelLarge,
            color = content
        )
        if (pingMs != null) {
            Text(
                text = "· $pingMs ms",
                style = typography.labelMedium,
                color = content.copy(alpha = 0.8f)
            )
        }
    }
}

// ===========================================================================
// 7. ExpressiveActionButton
// Кнопка действия: при нажатии cornerRadius морфится 28dp -> 12dp и сжимается.
// ===========================================================================
@Composable
fun ExpressiveActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    icon: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val isDown = pressed && enabled

    val radius by animateDpAsState(
        targetValue = if (isDown) 12.dp else 28.dp,
        animationSpec = SolaceExpressiveMotion.morph,
        label = "actionRadius"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDown) 0.97f else 1f,
        animationSpec = SolaceExpressiveMotion.press,
        label = "actionScale"
    )
    val bg by animateColorAsState(
        targetValue = if (enabled) containerColor else cs.surfaceVariant,
        animationSpec = SolaceExpressiveMotion.color,
        label = "actionBg"
    )
    val fg by animateColorAsState(
        targetValue = if (enabled) contentColor else cs.onSurfaceVariant,
        animationSpec = SolaceExpressiveMotion.color,
        label = "actionFg"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptic.actionTick()
                    onClick()
                }
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides fg) {
                    icon()
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = typography.labelLarge,
                color = fg
            )
        }
    }
}
