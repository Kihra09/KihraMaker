package com.kihra.maker.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.kihra.maker.R

/** Fira Sans: all normal text. */
val KihraSans = FontFamily(
    Font(R.font.fira_sans_light, FontWeight.Light),
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold),
    Font(R.font.fira_sans_bold, FontWeight.Bold),
)

/** M PLUS 1 Code: logs, code and key names (also covers Japanese). */
val KihraMono = FontFamily(
    Font(R.font.mplus_code_regular, FontWeight.Normal),
    Font(R.font.mplus_code_bold, FontWeight.Bold),
)

private fun TextStyle.sans() = copy(fontFamily = KihraSans)

val KihraTypography: Typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.sans(), displayMedium = it.displayMedium.sans(), displaySmall = it.displaySmall.sans(),
        headlineLarge = it.headlineLarge.sans(), headlineMedium = it.headlineMedium.sans(), headlineSmall = it.headlineSmall.sans(),
        titleLarge = it.titleLarge.sans(), titleMedium = it.titleMedium.sans(), titleSmall = it.titleSmall.sans(),
        bodyLarge = it.bodyLarge.sans(), bodyMedium = it.bodyMedium.sans(), bodySmall = it.bodySmall.sans(),
        labelLarge = it.labelLarge.sans(), labelMedium = it.labelMedium.sans(), labelSmall = it.labelSmall.sans(),
    )
}
