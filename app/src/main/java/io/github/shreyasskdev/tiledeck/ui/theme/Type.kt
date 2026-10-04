package io.github.shreyasskdev.tiledeck.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.shreyasskdev.tiledeck.R

/**
 * Needs the Google Sans Flex variable TTF at:
 * app/src/main/res/font/google_sans_flex.ttf
 */
@OptIn(ExperimentalTextApi::class)
private fun flexFont(weight: FontWeight, rond: Float = 100f) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", rond) // 0 = normal, 100 = fully rounded
    )
)

val AppFontFamily = FontFamily(
    flexFont(FontWeight.Normal),
    flexFont(FontWeight.Medium),
    flexFont(FontWeight.SemiBold),
    flexFont(FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily),

    // Expressive emphasized styles. Delete any line that doesn't compile on your alpha.
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = AppFontFamily),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = AppFontFamily),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = AppFontFamily),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = AppFontFamily),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = AppFontFamily),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = AppFontFamily),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = AppFontFamily),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = AppFontFamily),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = AppFontFamily),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = AppFontFamily),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = AppFontFamily),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = AppFontFamily),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = AppFontFamily),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = AppFontFamily),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = AppFontFamily),
)