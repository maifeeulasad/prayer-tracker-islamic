package com.mua.prayertracker.ui.screens.tracker

import android.Manifest
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
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
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TrackerScreen(
    viewModel: PrayerTrackerViewModel = viewModel()
) {
    val prayers by viewModel.prayers.collectAsState()
    val currentRecord by viewModel.currentPrayerRecord.collectAsState()
    val nextPrayerInfo by viewModel.nextPrayerInfo.collectAsState()

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val hasLocationPermission = locationPermissionsState.permissions.any { permissionState ->
        permissionState.status.isGranted
    }
    val shouldShowRationale = locationPermissionsState.permissions.any { permissionState ->
        permissionState.status.shouldShowRationale
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.loadPrayerTimesFromLocation()
        } else if (!hasRequestedPermission) {
            hasRequestedPermission = true
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!hasLocationPermission) {
            LocationPermissionCard(
                shouldShowRationale = shouldShowRationale,
                onRequestPermission = {
                    hasRequestedPermission = true
                    locationPermissionsState.launchMultiplePermissionRequest()
                }
            )
        }

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

@Composable
private fun LocationPermissionCard(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Enable location for accurate prayer times",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (shouldShowRationale) {
                    "Allow location so the app can calculate prayer times for your area."
                } else {
                    "Prayer times are currently preview values until location access is allowed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onRequestPermission) {
                Text("Grant location permission")
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
