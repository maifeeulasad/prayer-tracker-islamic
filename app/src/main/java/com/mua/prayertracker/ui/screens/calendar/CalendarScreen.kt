package com.mua.prayertracker.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mua.prayertracker.ui.components.PrayerCalendar
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.viewmodel.PrayerTrackerViewModel

/**
 * Calendar screen showing monthly prayer history.
 * Displays a calendar view with color-coded days based on prayer completion.
 */
@Composable
fun CalendarScreen(
    viewModel: PrayerTrackerViewModel = viewModel(),
    onDaySelected: (String) -> Unit = {}
) {
    val calendarDays by viewModel.calendarDays.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Prayer History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = IslamicGreen,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Track your daily prayers and monitor your progress",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Calendar component
        PrayerCalendar(
            days = calendarDays,
            currentMonth = selectedMonth,
            onPreviousMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() },
            onDayClick = { date ->
                viewModel.setCurrentDate(date)
                onDaySelected(date)
            }
        )
    }
}
