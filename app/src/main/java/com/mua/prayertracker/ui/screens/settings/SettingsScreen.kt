package com.mua.prayertracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mua.prayertracker.domain.PrayerTimeProvider
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.viewmodel.PrayerTrackerViewModel
import kotlin.math.roundToInt

/**
 * Settings screen for prayer-time calculation parameters.
 */
@Composable
fun SettingsScreen(
	viewModel: PrayerTrackerViewModel = viewModel()
) {
	val settings by viewModel.prayerSettings.collectAsState()
	var elevationInput by remember(settings.elevationMeters) {
		mutableStateOf(settings.elevationMeters.toString())
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
			.verticalScroll(rememberScrollState())
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = "Settings",
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.Bold,
			color = IslamicGreen
		)

		Text(
			text = "Prayer time calculation",
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		CycleSettingRow(
			title = "Calculation method",
			value = settings.method.name.replace('_', ' '),
			onPrevious = {
				viewModel.updateCalculationMethod(
					previousEnumValue(
						current = settings.method,
						all = PrayerTimeProvider.CalculationMethod.entries
					)
				)
			},
			onNext = {
				viewModel.updateCalculationMethod(
					nextEnumValue(
						current = settings.method,
						all = PrayerTimeProvider.CalculationMethod.entries
					)
				)
			}
		)

		CycleSettingRow(
			title = "Madhab",
			value = settings.madhab.name,
			onPrevious = {
				viewModel.updateMadhab(
					previousEnumValue(
						current = settings.madhab,
						all = PrayerTimeProvider.Madhab.entries
					)
				)
			},
			onNext = {
				viewModel.updateMadhab(
					nextEnumValue(
						current = settings.madhab,
						all = PrayerTimeProvider.Madhab.entries
					)
				)
			}
		)

		CycleSettingRow(
			title = "High latitude rule",
			value = settings.highLatitudeRule?.name?.replace('_', ' ') ?: "NONE",
			onPrevious = {
				viewModel.updateHighLatitudeRule(
					previousNullableEnumValue(
						current = settings.highLatitudeRule,
						all = PrayerTimeProvider.HighLatitudeRule.entries
					)
				)
			},
			onNext = {
				viewModel.updateHighLatitudeRule(
					nextNullableEnumValue(
						current = settings.highLatitudeRule,
						all = PrayerTimeProvider.HighLatitudeRule.entries
					)
				)
			}
		)

		OutlinedTextField(
			value = elevationInput,
			onValueChange = { raw ->
				elevationInput = raw
				raw.toDoubleOrNull()?.let { viewModel.updateElevationMeters(it) }
			},
			label = { Text("Elevation (meters)") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)

		OffsetSlider(
			title = "Fajr offset (minutes)",
			currentOffset = settings.fajrOffsetMinutes,
			onOffsetChanged = { viewModel.updateOffsetMinutes(PrayerType.FAJR, it) }
		)
		OffsetSlider(
			title = "Dhuhr offset (minutes)",
			currentOffset = settings.dhuhrOffsetMinutes,
			onOffsetChanged = { viewModel.updateOffsetMinutes(PrayerType.DHUHR, it) }
		)
		OffsetSlider(
			title = "Asr offset (minutes)",
			currentOffset = settings.asrOffsetMinutes,
			onOffsetChanged = { viewModel.updateOffsetMinutes(PrayerType.ASR, it) }
		)
		OffsetSlider(
			title = "Maghrib offset (minutes)",
			currentOffset = settings.maghribOffsetMinutes,
			onOffsetChanged = { viewModel.updateOffsetMinutes(PrayerType.MAGHRIB, it) }
		)
		OffsetSlider(
			title = "Isha offset (minutes)",
			currentOffset = settings.ishaOffsetMinutes,
			onOffsetChanged = { viewModel.updateOffsetMinutes(PrayerType.ISHA, it) }
		)

		Spacer(modifier = Modifier.height(8.dp))
	}
}

@Composable
private fun CycleSettingRow(
	title: String,
	value: String,
	onPrevious: () -> Unit,
	onNext: () -> Unit
) {
	Card(
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
		modifier = Modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(text = title, style = MaterialTheme.typography.titleSmall)
				Text(
					text = value,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.SemiBold,
					color = IslamicGreen
				)
			}

			Row {
				TextButton(onClick = onPrevious) { Text("Prev") }
				TextButton(onClick = onNext) { Text("Next") }
			}
		}
	}
}

@Composable
private fun OffsetSlider(
	title: String,
	currentOffset: Int,
	onOffsetChanged: (Int) -> Unit
) {
	var sliderValue by remember(currentOffset) { mutableFloatStateOf(currentOffset.toFloat()) }

	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant,
		shape = MaterialTheme.shapes.medium,
		modifier = Modifier.fillMaxWidth()
	) {
		Column(modifier = Modifier.padding(12.dp)) {
			Text(
				text = "$title: ${sliderValue.roundToInt()} min",
				style = MaterialTheme.typography.titleSmall
			)
			Slider(
				value = sliderValue,
				onValueChange = { sliderValue = it },
				onValueChangeFinished = { onOffsetChanged(sliderValue.roundToInt()) },
				valueRange = -90f..90f,
				steps = 179,
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}

private fun <T> nextEnumValue(current: T, all: List<T>): T {
	val index = all.indexOf(current)
	val nextIndex = (index + 1) % all.size
	return all[nextIndex]
}

private fun <T> previousEnumValue(current: T, all: List<T>): T {
	val index = all.indexOf(current)
	val previousIndex = if (index == 0) all.lastIndex else index - 1
	return all[previousIndex]
}

private fun <T> nextNullableEnumValue(current: T?, all: List<T>): T? {
	val values = listOf<T?>(null) + all
	val index = values.indexOf(current)
	val nextIndex = (index + 1) % values.size
	return values[nextIndex]
}

private fun <T> previousNullableEnumValue(current: T?, all: List<T>): T? {
	val values = listOf<T?>(null) + all
	val index = values.indexOf(current)
	val previousIndex = if (index == 0) values.lastIndex else index - 1
	return values[previousIndex]
}