package com.monatlich.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 defaults, system font. Amount text uses [AmountTextStyle] for tabular figures.
val MonatlichTypography = Typography()

/** Tabular figures so amounts line up in columns. Apply on top of any M3 style. */
val AmountFontFeatures = "tnum"

val AmountTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    fontFeatureSettings = AmountFontFeatures,
)
