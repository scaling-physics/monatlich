package com.monatlich.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.monatlich.ui.common.PlaceholderScreen

const val SETTINGS_SCREEN_TAG = "settings_screen"

/** Placeholder until M6 delivers base currency + rate table settings. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Settings",
        message = "Coming in M6",
        modifier = modifier,
        testTag = SETTINGS_SCREEN_TAG,
    )
}
