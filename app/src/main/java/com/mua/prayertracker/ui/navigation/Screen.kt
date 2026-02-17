package com.mua.prayertracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class representing navigation routes in the app.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Tracker : Screen(
        route = "tracker",
        title = "Tracker",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Calendar : Screen(
        route = "calendar",
        title = "Calendar",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )
}

/**
 * List of all bottom navigation items.
 */
val bottomNavItems = listOf(
    Screen.Tracker,
    Screen.Calendar
)
