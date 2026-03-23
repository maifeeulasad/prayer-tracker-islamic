package com.mua.prayertracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.viewmodel.PrayerTrackerViewModel

/**
 * Settings screen with a basic header.
 */
@Composable
fun SettingsScreen(
	viewModel: PrayerTrackerViewModel = viewModel()
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
			.padding(16.dp)
	) {
		Text(
			text = "Settings",
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.Bold,
			color = IslamicGreen
		)
	}
}