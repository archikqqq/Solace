package com.v2nox.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.v2nox.app.R

// Bundled Inter (SIL OFL) variable font: no provider, network access or synthetic bold required.
// The optical-size axis (opsz, 14..32) is bound per family: text sizes use the 14 cut,
// display and headline sizes use the 32 cut, whose tighter shapes read more crisply at large sizes.
private fun interFamily(opticalSize: Float) = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    ).map { weight ->
        Font(
            resId = R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    }
)

val Inter: FontFamily = interFamily(opticalSize = 14f)
private val InterDisplay: FontFamily = interFamily(opticalSize = 32f)

private fun interStyle(
    size: Int,
    height: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    family: FontFamily = Inter,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
)

// Higher contrast hierarchy: display and headline tiers are heavier and tighter,
// body stays Normal, and labels are SemiBold so they separate from body text.
private val BaseTypography = Typography(
    fontFamily = Inter,
    displayLarge = interStyle(40, 48, FontWeight.ExtraBold, -0.75f, InterDisplay),
    displayMedium = interStyle(36, 44, FontWeight.Bold, -0.5f, InterDisplay),
    displaySmall = interStyle(32, 40, FontWeight.Bold, -0.25f, InterDisplay),
    headlineLarge = interStyle(30, 38, FontWeight.Bold, -0.25f, InterDisplay),
    headlineMedium = interStyle(28, 36, FontWeight.Bold, -0.15f, InterDisplay),
    headlineSmall = interStyle(24, 32, FontWeight.SemiBold, 0f, InterDisplay),
    titleLarge = interStyle(22, 28, FontWeight.SemiBold, 0f, InterDisplay),
    titleMedium = interStyle(16, 24, FontWeight.SemiBold, 0.1f),
    titleSmall = interStyle(14, 20, FontWeight.SemiBold, 0.1f),
    bodyLarge = interStyle(16, 24, FontWeight.Normal, 0.1f),
    bodyMedium = interStyle(14, 20, FontWeight.Normal, 0.1f),
    bodySmall = interStyle(12, 16, FontWeight.Normal, 0.1f),
    labelLarge = interStyle(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = interStyle(12, 16, FontWeight.SemiBold, 0.2f),
    labelSmall = interStyle(11, 16, FontWeight.SemiBold, 0.2f),
)

// Expressive emphasis changes weight, not size or layout rhythm.
val Typography = BaseTypography.run {
    copy(
        displayLargeEmphasized = displayLarge.copy(fontWeight = FontWeight.ExtraBold),
        displayMediumEmphasized = displayMedium.copy(fontWeight = FontWeight.ExtraBold),
        displaySmallEmphasized = displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLargeEmphasized = headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMediumEmphasized = headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmallEmphasized = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLargeEmphasized = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmallEmphasized = titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLargeEmphasized = bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyMediumEmphasized = bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        bodySmallEmphasized = bodySmall.copy(fontWeight = FontWeight.SemiBold),
        labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMediumEmphasized = labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmallEmphasized = labelSmall.copy(fontWeight = FontWeight.Bold),
    )
}
