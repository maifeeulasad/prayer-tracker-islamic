package com.mua.prayertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mua.prayertracker.domain.model.CalendarDay
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.ui.theme.ErrorRed
import com.mua.prayertracker.ui.theme.GoldenAccent
import com.mua.prayertracker.ui.theme.IslamicGreen
import com.mua.prayertracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Calendar view for tracking prayer completion across days.
 * Shows monthly view with color-coded days based on prayer completion.
 */
@Composable
fun PrayerCalendar(
    days: List<CalendarDay>,
    currentMonth: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Month navigation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = IslamicGreen
                    )
                }

                Text(
                    text = formatMonthDisplay(currentMonth),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IslamicGreen
                )

                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = IslamicGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day of week headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(days) { day ->
                    CalendarDayCell(
                        day = day,
                        onClick = { if (day.date.isNotEmpty()) onDayClick(day.date) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            CalendarLegend()
        }
    }
}

/**
 * Individual day cell in the calendar.
 */
@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit
) {
    if (day.dayOfMonth == 0) {
        // Empty cell for padding
        Box(modifier = Modifier.aspectRatio(1f))
    } else {
        val backgroundColor = when {
            day.isToday -> IslamicGreen
            day.completionStatus == DayCompletionStatus.COMPLETE -> IslamicGreen.copy(alpha = 0.8f)
            day.completionStatus == DayCompletionStatus.PARTIAL -> GoldenAccent
            day.completionStatus == DayCompletionStatus.MISSED -> ErrorRed
            else -> Color.Transparent
        }

        val textColor = when {
            day.isToday -> Color.White
            day.completionStatus == DayCompletionStatus.COMPLETE -> Color.White
            day.completionStatus == DayCompletionStatus.PARTIAL -> Color.Black
            day.completionStatus == DayCompletionStatus.MISSED -> Color.White
            !day.isCurrentMonth -> TextSecondary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onSurface
        }

        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (day.isToday) {
                        Modifier.border(2.dp, IslamicGreen, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = day.isCurrentMonth, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

/**
 * Legend for calendar completion status.
 */
@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(color = IslamicGreen, label = "Complete")
        LegendItem(color = GoldenAccent, label = "Partial")
        LegendItem(color = ErrorRed, label = "Missed")
        LegendItem(color = Color.Transparent, label = "Empty")
    }
}

/**
 * Single legend item.
 */
@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (color == Color.Transparent) {
                        Modifier.border(1.dp, TextSecondary, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Format month string for display.
 */
private fun formatMonthDisplay(yearMonth: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val date = inputFormat.parse(yearMonth + "-01")
        date?.let { outputFormat.format(it) } ?: yearMonth
    } catch (e: Exception) {
        yearMonth
    }
}
