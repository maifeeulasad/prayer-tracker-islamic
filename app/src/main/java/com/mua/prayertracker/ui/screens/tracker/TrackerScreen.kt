package com.mua.prayertracker.ui.screens.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.ui.components.PrayerCard
import com.mua.prayertracker.ui.theme.GoldenAccent
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.viewmodel.PrayerTrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main prayer tracker screen showing today's prayers.
 * Displays all five daily prayers with their respective units.
 */
@Composable
fun TrackerScreen(
    viewModel: PrayerTrackerViewModel = viewModel()
) {
    val prayers by viewModel.prayers.collectAsState()
    val currentRecord by viewModel.currentPrayerRecord.collectAsState()
    val nextPrayerInfo by viewModel.nextPrayerInfo.collectAsState()

    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with date
        TrackerHeader(
            date = dateFormat.format(Date()),
            nextPrayer = nextPrayerInfo.first,
            timeRemaining = nextPrayerInfo.second
        )

        // Prayer list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(prayers) { prayer ->
                PrayerCard(
                    prayerType = prayer.type,
                    prayerTime = prayer.time,
                    record = currentRecord,
                    onGroupToggle = { unitIds ->
                        viewModel.togglePrayerUnits(unitIds)
                    }
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Header section of the tracker screen.
 */
@Composable
private fun TrackerHeader(
    date: String,
    nextPrayer: PrayerType?,
    timeRemaining: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = IslamicGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (nextPrayer != null) {
                Text(
                    text = "Next: ${nextPrayer.displayName} in $timeRemaining",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "All prayers completed for today!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decorative golden accent
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GoldenAccent)
            )
        }
    }
}
