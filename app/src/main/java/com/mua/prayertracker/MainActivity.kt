package com.mua.prayertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.mua.prayertracker.util.NotificationUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mua.prayertracker.ui.navigation.Screen
import com.mua.prayertracker.ui.navigation.bottomNavItems
import com.mua.prayertracker.ui.screens.calendar.CalendarScreen
import com.mua.prayertracker.ui.screens.settings.SettingsScreen
import com.mua.prayertracker.ui.screens.tracker.TrackerScreen
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.theme.PrayerTrackerIslamicTheme
import com.mua.prayertracker.ui.viewmodel.PrayerTrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create notification channel
        NotificationUtils.createNotificationChannel(this)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        enableEdgeToEdge()
        setContent {
            PrayerTrackerIslamicTheme {
                MainScreen()
            }
        }
    }
}

/**
 * Main screen with bottom navigation.
 */
@Composable
fun MainScreen(
    viewModel: PrayerTrackerViewModel = viewModel()
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                items = bottomNavItems,
                selectedIndex = selectedIndex,
                onItemClick = { index ->
                    selectedIndex = index
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (bottomNavItems[selectedIndex].route) {
                Screen.Tracker.route -> TrackerScreen(viewModel = viewModel)
                Screen.Calendar.route -> CalendarScreen(viewModel = viewModel)
                Screen.Settings.route -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Bottom navigation bar with Islamic green/gold theme.
 */
@Composable
private fun BottomNavigationBar(
    items: List<Screen>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEachIndexed { index, screen ->
            val isSelected = selectedIndex == index

            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!screen.disabled) onItemClick(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.title,
                        tint = if (screen.disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IslamicGreen,
                    selectedTextColor = IslamicGreen,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = IslamicGreen.copy(alpha = 0.1f)
                )
            )
        }
    }
}
