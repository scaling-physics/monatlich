package com.monatlich.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seed: deep maroon. Tonal roles below are derived from it by hand (M3 tonal palette,
// warm neutrals). Dynamic color is deliberately OFF — see AGENT.md "Theme".
val MaroonSeed = Color(0xFF6B1F2A)

// --- Light scheme ---
val md_light_primary = Color(0xFF6B1F2A)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFFFDAD9)
val md_light_onPrimaryContainer = Color(0xFF410009)
val md_light_secondary = Color(0xFF765657)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFFFDAD9)
val md_light_onSecondaryContainer = Color(0xFF2C1516)
val md_light_tertiary = Color(0xFF775930)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFFFDDB4)
val md_light_onTertiaryContainer = Color(0xFF2A1800)
val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)
val md_light_background = Color(0xFFFFF8F7)
val md_light_onBackground = Color(0xFF221919)
val md_light_surface = Color(0xFFFFF8F7)
val md_light_onSurface = Color(0xFF221919)
val md_light_surfaceVariant = Color(0xFFF4DDDD)
val md_light_onSurfaceVariant = Color(0xFF524343)
val md_light_outline = Color(0xFF857373)
val md_light_outlineVariant = Color(0xFFD7C1C1)
val md_light_inverseSurface = Color(0xFF382E2E)
val md_light_inverseOnSurface = Color(0xFFFFEDEC)
val md_light_inversePrimary = Color(0xFFFFB3B4)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFFFF0F0)
val md_light_surfaceContainer = Color(0xFFFCEAEA)
val md_light_surfaceContainerHigh = Color(0xFFF6E4E4)
val md_light_surfaceContainerHighest = Color(0xFFF0DEDE)

// --- Dark scheme ---
val md_dark_primary = Color(0xFFFFB3B4)
val md_dark_onPrimary = Color(0xFF561D25)
val md_dark_primaryContainer = Color(0xFF72333B)
val md_dark_onPrimaryContainer = Color(0xFFFFDAD9)
val md_dark_secondary = Color(0xFFE6BDBD)
val md_dark_onSecondary = Color(0xFF44292A)
val md_dark_secondaryContainer = Color(0xFF5D3F40)
val md_dark_onSecondaryContainer = Color(0xFFFFDAD9)
val md_dark_tertiary = Color(0xFFE8C08E)
val md_dark_onTertiary = Color(0xFF432C06)
val md_dark_tertiaryContainer = Color(0xFF5D421B)
val md_dark_onTertiaryContainer = Color(0xFFFFDDB4)
// Tone 60 rather than the usual tone 80: tone 80 (#FFB4AB) is indistinguishable from the dark
// primary (#FFB3B4), and over-budget must never read as "brand" (AGENT.md "Theme").
val md_dark_error = Color(0xFFFF5449)
val md_dark_onError = Color(0xFF410002)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_dark_background = Color(0xFF1A1111)
val md_dark_onBackground = Color(0xFFF0DEDE)
val md_dark_surface = Color(0xFF1A1111)
val md_dark_onSurface = Color(0xFFF0DEDE)
val md_dark_surfaceVariant = Color(0xFF524343)
val md_dark_onSurfaceVariant = Color(0xFFD7C1C1)
val md_dark_outline = Color(0xFF9F8C8C)
val md_dark_outlineVariant = Color(0xFF524343)
val md_dark_inverseSurface = Color(0xFFF0DEDE)
val md_dark_inverseOnSurface = Color(0xFF382E2E)
val md_dark_inversePrimary = Color(0xFF6B1F2A)
val md_dark_surfaceContainerLowest = Color(0xFF140C0C)
val md_dark_surfaceContainerLow = Color(0xFF221919)
val md_dark_surfaceContainer = Color(0xFF271D1D)
val md_dark_surfaceContainerHigh = Color(0xFF322727)
val md_dark_surfaceContainerHighest = Color(0xFF3D3232)

// Semantic (budget status). Distinct from primary so "over budget" never reads as "brand".
val BudgetOnTrackLight = Color(0xFF3B6E3F)
val BudgetOnTrackDark = Color(0xFF9BD59B)
val BudgetWarningLight = Color(0xFF8A6100)
val BudgetWarningDark = Color(0xFFF2C24D)

// Chart categorical palette (Overview "Spending" card): a soft, earthy quartet independent of
// each category's own custom color (used for badges/rows elsewhere). Computed, not eyeballed —
// assigned in this fixed ring order (ochre -> sage -> sand -> teal -> wraps to ochre) so that
// only ring-adjacent slots, the ones that actually sit next to each other in a donut or bar list,
// have to clear the dataviz skill's CVD/normal-vision separation gates; the two diagonal pairs
// (ochre/sand, sage/teal — the closest-in-spirit hues) are exempt from that gate by construction,
// though still checked to a softer floor. Same four hexes pass contrast against both the light and
// dark `surfaceContainerLow` card background, so no separate dark variant is needed.
val ChartOchre = Color(0xFFC87A00)
val ChartSage = Color(0xFF487738)
val ChartSand = Color(0xFFC77A65)
val ChartTeal = Color(0xFF008E9C)
