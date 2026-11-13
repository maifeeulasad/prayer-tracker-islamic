package com.mua.prayertracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mua.prayertracker.domain.model.PrayerCategory
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnitCatalog
import com.mua.prayertracker.domain.model.PrayerUnitGroup
import com.mua.prayertracker.ui.theme.FardColor
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.theme.SuccessGreen
import com.mua.prayertracker.ui.theme.SunnatColor
import com.mua.prayertracker.ui.theme.TextSecondary
import com.mua.prayertracker.ui.theme.WitrColor
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Modal dialog showing the prayer details of a single calendar day:
 * every prayer with its rakat groups and how much of each was completed.
 */
@Composable
fun DayDetailsDialog(
    date: String,
    completedUnits: Set<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Column {
                Text(
                    text = formatDayDisplay(date),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IslamicGreen
                )
                Text(
                    text = fardSummary(completedUnits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrayerType.entries.forEach { prayerType ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prayerType.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = prayerType.arabicName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        PrayerUnitCatalog.groupsFor(prayerType).forEach { group ->
                            DayDetailGroupRow(group, completedUnits)
                        }
                    }
                }
            }
        }
    )
}

/**
 * One rakat group row: label plus completed count.
 */
@Composable
private fun DayDetailGroupRow(
    group: PrayerUnitGroup,
    completedUnits: Set<String>
) {
    val completedCount = group.unitIds.count { it in completedUnits }
    val totalCount = group.unitIds.size

    val statusText = when (completedCount) {
        totalCount -> "✓"
        0 -> "—"
        else -> "$completedCount/$totalCount"
    }
    val statusColor = when (completedCount) {
        totalCount -> SuccessGreen
        0 -> TextSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.bodyMedium,
            color = categoryColor(group.category)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

private fun categoryColor(category: PrayerCategory): Color = when (category) {
    PrayerCategory.FARD -> FardColor
    PrayerCategory.SUNNAT, PrayerCategory.NAFL -> SunnatColor
    PrayerCategory.WITR -> WitrColor
}

/**
 * Summary line for the dialog header, e.g. "Fard completed: 11 of 17 rakats".
 */
private fun fardSummary(completedUnits: Set<String>): String {
    val allFard = PrayerUnitCatalog.allFardUnitIds
    val completedFard = allFard.count { it in completedUnits }
    return "Fard completed: $completedFard of ${allFard.size} rakats"
}

/**
 * Format an ISO date ("2026-07-14") for display ("Tuesday, July 14, 2026").
 * Falls back to the raw string for unparseable input.
 */
private fun formatDayDisplay(date: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        inputFormat.parse(date)?.let { outputFormat.format(it) } ?: date
    } catch (e: Exception) {
        date
    }
}
