package com.mua.prayertracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class representing navigation routes in the app.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val disabled: Boolean = false,
) {
    data object Namaz : Screen(
        route = "namaz",
        title = "Namaz",
        selectedIcon = Icons.Filled.Mosque,
        unselectedIcon = Icons.Outlined.Mosque
    )

    data object Tasbih : Screen(
        route = "tasbih",
        title = "Tasbih",
        selectedIcon = Icons.Filled.Adjust,
        unselectedIcon = Icons.Outlined.Adjust
    )

    data object Calendar : Screen(
        route = "calendar",
        title = "Calendar",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

/**
 * List of all bottom navigation items.
 */
val bottomNavItems = listOf(
    Screen.Namaz,
    Screen.Tasbih,
    Screen.Calendar,
    Screen.Settings,
)
