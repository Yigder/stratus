package com.weatherclone.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.weatherclone.app.R

/*
 * Inter (SIL Open Font License) is the closest freely-licensed match for Apple's SF Pro.
 * It is a variable font: "opsz" 14 is the text cut, 32 the display cut (like SF Pro Display).
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, opticalSize: Float) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
)

val Inter = FontFamily((100..900 step 100).map { inter(it, 14f) })
val InterDisplay = FontFamily((100..900 step 100).map { inter(it, 32f) })

private fun TextStyle.inter() = copy(fontFamily = if ((fontSize.value) >= 24f) InterDisplay else Inter)

val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.inter(), displayMedium = t.displayMedium.inter(), displaySmall = t.displaySmall.inter(),
        headlineLarge = t.headlineLarge.inter(), headlineMedium = t.headlineMedium.inter(), headlineSmall = t.headlineSmall.inter(),
        titleLarge = t.titleLarge.inter(), titleMedium = t.titleMedium.inter(), titleSmall = t.titleSmall.inter(),
        bodyLarge = t.bodyLarge.inter(), bodyMedium = t.bodyMedium.inter(), bodySmall = t.bodySmall.inter(),
        labelLarge = t.labelLarge.inter(), labelMedium = t.labelMedium.inter(), labelSmall = t.labelSmall.inter(),
    )
}
