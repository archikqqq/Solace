package com.v2nox.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.v2nox.app.R

// Bundled Inter (SIL OFL): no provider, network access or synthetic bold required.
val Inter = FontFamily(
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
                FontVariation.Setting("opsz", 14f),
            ),
        )
    }
)

private fun interStyle(size: Int, height: Int, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = tracking.sp,
    )

// Compact display scale; body and labels retain readable MD3 sizes.
private val BaseTypography = Typography(
    fontFamily = Inter,
    displayLarge = interStyle(40, 48, FontWeight.Bold, -0.5f),
    displayMedium = interStyle(36, 44, FontWeight.Bold, -0.5f),
    displaySmall = interStyle(32, 40, FontWeight.SemiBold, -0.25f),
    headlineLarge = interStyle(30, 38, FontWeight.SemiBold, -0.25f),
    headlineMedium = interStyle(28, 36, FontWeight.SemiBold),
    headlineSmall = interStyle(24, 32, FontWeight.SemiBold),
    titleLarge = interStyle(22, 28, FontWeight.SemiBold),
    titleMedium = interStyle(16, 24, FontWeight.SemiBold, 0.1f),
    titleSmall = interStyle(14, 20, FontWeight.SemiBold, 0.1f),
    bodyLarge = interStyle(16, 24, FontWeight.Normal, 0.1f),
    bodyMedium = interStyle(14, 20, FontWeight.Normal, 0.1f),
    bodySmall = interStyle(12, 16, FontWeight.Normal, 0.1f),
    labelLarge = interStyle(14, 20, FontWeight.Medium, 0.1f),
    labelMedium = interStyle(12, 16, FontWeight.Medium, 0.2f),
    labelSmall = interStyle(11, 16, FontWeight.Medium, 0.2f),
)

// Expressive emphasis changes weight, not size or layout rhythm.
val Typography = BaseTypography.run {
    copy(
        displayLargeEmphasized = displayLarge.copy(fontWeight = FontWeight.ExtraBold),
        displayMediumEmphasized = displayMedium.copy(fontWeight = FontWeight.ExtraBold),
        displaySmallEmphasized = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLargeEmphasized = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMediumEmphasized = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmallEmphasized = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLargeEmphasized = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmallEmphasized = titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLargeEmphasized = bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMediumEmphasized = bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmallEmphasized = bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMediumEmphasized = labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmallEmphasized = labelSmall.copy(fontWeight = FontWeight.Bold),
    )
}
