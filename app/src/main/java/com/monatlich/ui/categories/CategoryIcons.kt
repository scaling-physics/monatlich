package com.monatlich.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves a [com.monatlich.domain.model.Category.icon] name (the `Icons.Filled.*` property name,
 * the same convention `data/local/Seed.kt` uses) to an [ImageVector]. Unknown names fall back to a
 * neutral label glyph so a renamed or removed icon never crashes a list.
 *
 * Shared by the category manager and the overview rows.
 */
fun categoryIcon(name: String): ImageVector = when (name) {
    "ShoppingCart" -> Icons.Filled.ShoppingCart
    "Home" -> Icons.Filled.Home
    "DirectionsBus" -> Icons.Filled.DirectionsBus
    "DirectionsCar" -> Icons.Filled.DirectionsCar
    "Train" -> Icons.Filled.Train
    "Flight" -> Icons.Filled.Flight
    "LocalGasStation" -> Icons.Filled.LocalGasStation
    "Restaurant" -> Icons.Filled.Restaurant
    "LocalCafe" -> Icons.Filled.LocalCafe
    "Celebration" -> Icons.Filled.Celebration
    "Movie" -> Icons.Filled.Movie
    "SportsEsports" -> Icons.Filled.SportsEsports
    "MusicNote" -> Icons.Filled.MusicNote
    "Subscriptions" -> Icons.Filled.Subscriptions
    "Favorite" -> Icons.Filled.Favorite
    "LocalHospital" -> Icons.Filled.LocalHospital
    "FitnessCenter" -> Icons.Filled.FitnessCenter
    "Checkroom" -> Icons.Filled.Checkroom
    "Pets" -> Icons.Filled.Pets
    "ChildCare" -> Icons.Filled.ChildCare
    "School" -> Icons.Filled.School
    "Work" -> Icons.Filled.Work
    "Phone" -> Icons.Filled.Phone
    "Wifi" -> Icons.Filled.Wifi
    "Bolt" -> Icons.Filled.Bolt
    "CardGiftcard" -> Icons.Filled.CardGiftcard
    "Savings" -> Icons.Filled.Savings
    "CreditCard" -> Icons.Filled.CreditCard
    "AttachMoney" -> Icons.Filled.AttachMoney
    "MoreHoriz" -> Icons.Filled.MoreHoriz
    else -> Icons.Filled.Label
}

/** Curated, ordered icon names offered by the picker. Every entry resolves via [categoryIcon]. */
object CategoryIconNames {
    const val DEFAULT = "Label"

    val all: List<String> = listOf(
        "ShoppingCart", "Home", "Restaurant", "LocalCafe", "DirectionsBus", "DirectionsCar",
        "Train", "Flight", "LocalGasStation", "Celebration", "Movie", "SportsEsports",
        "MusicNote", "Subscriptions", "Favorite", "LocalHospital", "FitnessCenter", "Checkroom",
        "Pets", "ChildCare", "School", "Work", "Phone", "Wifi",
        "Bolt", "CardGiftcard", "Savings", "CreditCard", "AttachMoney", "MoreHoriz",
    )
}

/**
 * Preset ARGB swatches for [com.monatlich.domain.model.Category.color]. Mid-tone, slightly
 * desaturated so they sit comfortably next to the maroon primary on both the warm off-white and
 * the warm near-black surfaces (white icon glyphs stay legible on every swatch).
 */
object CategoryColors {
    const val DEFAULT = 0xFF6B1F2A

    val all: List<Long> = listOf(
        0xFF6B1F2A, // maroon (brand)
        0xFFC62828, // red
        0xFFEF6C00, // orange
        0xFFB8860B, // ochre
        0xFF2E7D32, // green
        0xFF00796B, // teal
        0xFF1565C0, // blue
        0xFF283593, // indigo
        0xFF7B1FA2, // purple
        0xFFAD1457, // magenta
        0xFF6D4C41, // brown
        0xFF546E7A, // slate
    )
}
