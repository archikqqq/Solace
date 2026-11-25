package com.v2nox.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Solace motion: Material 3 Expressive spring physics built only on the stable
 * androidx.compose.animation.core.spring API.
 *
 * - Spatial specs (position, size, shape) are underdamped, so they overshoot slightly.
 * - Effect specs (color, alpha) are critically damped, so they settle without overshoot.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object SolaceMotion : MotionScheme {

    private const val SPATIAL_DAMPING = 0.6f
    private const val SPATIAL_DEFAULT_DAMPING = 0.8f
    private const val EFFECTS_DAMPING = 1f

    // Geometry: position, size, corner radius.
    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = 800f)

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DEFAULT_DAMPING, stiffness = 380f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DEFAULT_DAMPING, stiffness = 200f)

    // Color and alpha.
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = 3800f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = 1600f)

    fun <T> slowEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = 800f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = fastSpatial()
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = defaultSpatial()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = slowSpatial()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fastEffects()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = defaultEffects()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = slowEffects()
}

@Composable
fun V2NoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Expressive motion (SolaceMotion springs), shape defaults and our Inter type scale.
    // Dynamic (Monet) schemes supply their own surface-container tonal ladder, unchanged here.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = SolaceMotion,
        typography = Typography,
        content = content
    )
}
