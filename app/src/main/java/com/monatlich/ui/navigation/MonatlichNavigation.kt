package com.monatlich.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.Motion
import com.monatlich.ui.common.MotionTokens
import com.monatlich.ui.common.rememberMotion
import com.monatlich.ui.overview.OverviewRoute
import com.monatlich.ui.settings.SettingsScreen
import com.monatlich.ui.transactions.TransactionsScreen

const val NAV_BAR_TAG = "nav_bar"

/**
 * The three top-level destinations. String routes for now; switch to `@Serializable` objects
 * once kotlinx-serialization is part of the build.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Overview(
        route = "overview",
        label = "Overview",
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
    ),
    Transactions(
        route = "transactions",
        label = "Transactions",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
    ),
    Settings(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

/** App root: bottom navigation bar + NavHost. Replaces the M0 placeholder. */
@Composable
fun MonatlichAppShell(
    navController: NavHostController = rememberNavController(),
) {
    val motion = rememberMotion()
    CompositionLocalProvider(LocalMotion provides motion) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        Scaffold(
            // Screens own their top bars; only the bottom inset is handled here.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                MonatlichNavigationBar(
                    currentDestination = currentDestination,
                    onNavigate = { destination -> navController.navigateToTopLevel(destination) },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                MonatlichNavHost(navController = navController, motion = motion)
            }
        }
    }
}

@Composable
private fun MonatlichNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag(NAV_BAR_TAG)) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy
                ?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun MonatlichNavHost(
    navController: NavHostController,
    motion: Motion,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Overview.route,
        enterTransition = { fadeThroughEnter(motion) },
        exitTransition = { fadeThroughExit(motion) },
        popEnterTransition = { fadeThroughEnter(motion) },
        popExitTransition = { fadeThroughExit(motion) },
    ) {
        composable(TopLevelDestination.Overview.route) { OverviewRoute() }
        composable(TopLevelDestination.Transactions.route) { TransactionsScreen() }
        composable(TopLevelDestination.Settings.route) { SettingsScreen() }
    }
}

/** Material "fade through": outgoing fades quickly, incoming fades + scales in after it. */
private fun fadeThroughEnter(motion: Motion): EnterTransition {
    if (motion.reduceMotion) return EnterTransition.None
    val duration = MotionTokens.LAYOUT_MS - MotionTokens.FEEDBACK_MS / 2
    val delay = MotionTokens.FEEDBACK_MS / 2
    return fadeIn(motion.tween(duration, delayMs = delay, easing = MotionTokens.Incoming)) +
        scaleIn(motion.tween(duration, delayMs = delay, easing = MotionTokens.Incoming), initialScale = 0.94f)
}

private fun fadeThroughExit(motion: Motion): ExitTransition {
    if (motion.reduceMotion) return ExitTransition.None
    return fadeOut(motion.tween(MotionTokens.FEEDBACK_MS / 2, easing = MotionTokens.Outgoing))
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Pop up to the start destination to avoid a growing back stack, keeping each tab's state.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
