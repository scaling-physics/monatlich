package com.monatlich.ui.categories

import com.monatlich.ui.common.DEFAULT_CATEGORY_ICON
import com.monatlich.ui.common.categoryIconNames


/** Curated, ordered icon names offered by the picker. Every entry resolves via [categoryIcon]. */
object CategoryIconNames {
    const val DEFAULT = DEFAULT_CATEGORY_ICON

    /** Every entry resolves via [categoryIcon]. */
    val all: List<String> get() = categoryIconNames
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
