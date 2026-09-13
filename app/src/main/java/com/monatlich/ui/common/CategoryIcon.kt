package com.monatlich.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves the Material Icons name stored on `Category.icon` (e.g. `"ShoppingCart"`) to a vector.
 * Unknown names fall back to a generic category glyph so a typo never crashes the overview.
 *
 * [categoryIconNames] is the pick list for the category editor; every entry resolves here.
 */
fun categoryIcon(name: String): ImageVector = CATEGORY_ICONS[name] ?: Icons.Outlined.Category

/** Converts the ARGB `Long` stored on `Category.color` (e.g. `0xFF6B1F2A`) into a Compose color. */
fun categoryColor(argb: Long): Color = Color(argb)

/**
 * [categoryColor] adjusted for the current surface: dark category colours (deep maroon, navy)
 * vanish on the dark theme, so on dark surfaces they are lifted towards white until they read.
 */
@Composable
fun categoryTint(argb: Long): Color {
    val base = categoryColor(argb)
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    if (!onDarkSurface) return base
    val lift = ((MIN_DARK_LUMINANCE - base.luminance()) / MIN_DARK_LUMINANCE).coerceIn(0f, MAX_LIFT)
    return if (lift == 0f) base else lerp(base, Color.White, lift)
}

private const val MIN_DARK_LUMINANCE = 0.25f
private const val MAX_LIFT = 0.55f

/** Name of the fallback glyph; also the editor default. */
const val DEFAULT_CATEGORY_ICON = "Category"

/** Pick list for the category editor: every icon except the generic fallback. */
val categoryIconNames: List<String> get() = CATEGORY_ICONS.keys.filter { it != DEFAULT_CATEGORY_ICON }

private val CATEGORY_ICONS: Map<String, ImageVector> = linkedMapOf(
    "ShoppingCart" to Icons.Outlined.ShoppingCart,
    "Home" to Icons.Outlined.Home,
    "DirectionsBus" to Icons.Outlined.DirectionsBus,
    "Restaurant" to Icons.Outlined.Restaurant,
    "Celebration" to Icons.Outlined.Celebration,
    "Favorite" to Icons.Outlined.Favorite,
    "MoreHoriz" to Icons.Outlined.MoreHoriz,
    "Coffee" to Icons.Outlined.Coffee,
    "ShoppingBag" to Icons.Outlined.ShoppingBag,
    "DirectionsCar" to Icons.Outlined.DirectionsCar,
    "Train" to Icons.Outlined.Train,
    "Flight" to Icons.Outlined.Flight,
    "Luggage" to Icons.Outlined.Luggage,
    "LocalGasStation" to Icons.Outlined.LocalGasStation,
    "LocalHospital" to Icons.Outlined.LocalHospital,
    "FitnessCenter" to Icons.Outlined.FitnessCenter,
    "Spa" to Icons.Outlined.Spa,
    "Pets" to Icons.Outlined.Pets,
    "ChildCare" to Icons.Outlined.ChildCare,
    "School" to Icons.Outlined.School,
    "Book" to Icons.Outlined.Book,
    "Movie" to Icons.Outlined.Movie,
    "MusicNote" to Icons.Outlined.MusicNote,
    "SportsEsports" to Icons.Outlined.SportsEsports,
    "Subscriptions" to Icons.Outlined.Subscriptions,
    "Wifi" to Icons.Outlined.Wifi,
    "Phone" to Icons.Outlined.Phone,
    "Bolt" to Icons.Outlined.Bolt,
    "Checkroom" to Icons.Outlined.Checkroom,
    "CardGiftcard" to Icons.Outlined.CardGiftcard,
    "Build" to Icons.Outlined.Build,
    "Work" to Icons.Outlined.Work,
    "Savings" to Icons.Outlined.Savings,
    "Paid" to Icons.Outlined.Paid,
    "CreditCard" to Icons.Outlined.CreditCard,
    "Receipt" to Icons.Outlined.Receipt,
    "Category" to Icons.Outlined.Category,
)
