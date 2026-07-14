package com.mua.prayertracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mua.prayertracker.domain.model.ForbiddenTime
import com.mua.prayertracker.domain.model.ForbiddenTimeType
import com.mua.prayertracker.domain.model.PrayerCategory
import com.mua.prayertracker.domain.model.PrayerTimeRange
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnitCatalog
import com.mua.prayertracker.ui.theme.FardColor
import com.mua.prayertracker.ui.theme.GoldenAccent
import com.mua.prayertracker.ui.theme.SuccessGreen
import com.mua.prayertracker.ui.theme.SunnatColor
import com.mua.prayertracker.ui.theme.WarningRed
import com.mua.prayertracker.ui.theme.WitrColor

/**
 * Card displaying a single prayer (Waqt) with grouped prayer units.
 * Uses a more productive UI where each group (e.g., "4 Rakat Fard")
 * can be marked with a single tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerCard(
    prayerType: PrayerType,
    prayerTime: String,
    completedUnits: Set<String>,
    onGroupToggle: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val groupedUnits = PrayerUnitCatalog.groupsFor(prayerType)

    val allFardCompleted = groupedUnits.filter { it.category == PrayerCategory.FARD }
        .all { it.isCompleted(completedUnits) }
    val anyProgress = groupedUnits.any { group -> group.unitIds.any { it in completedUnits } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Prayer header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    allFardCompleted -> SuccessGreen
                                    anyProgress -> GoldenAccent
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = prayerType.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = prayerType.arabicName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val prayerTimeParts = prayerTime.split("~", limit = 2).map { it.trim() }

                    Column(horizontalAlignment = Alignment.End) {
                        if (prayerTimeParts.size == 2) {
                            Text(
                                text = prayerTimeParts[0],
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = FardColor
                            )
                            Text(
                                text = prayerTimeParts[1],
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = FardColor
                            )
                        } else {
                            Text(
                                text = prayerTime,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = FardColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick toggle buttons (always visible)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedUnits.forEach { group ->
                    CompactPrayerToggle(
                        label = group.label,
                        isCompleted = group.isCompleted(completedUnits),
                        category = group.category,
                        onToggle = { onGroupToggle(group.unitIds) }
                    )
                }
            }

            // Expandable details
            AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "Prayer Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getPrayerGuide(prayerType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Compact toggle button for grouped prayer units.
 * Shows as a pill-shaped button with check/uncheck state.
 */
@Composable
private fun CompactPrayerToggle(
    label: String,
    isCompleted: Boolean,
    category: PrayerCategory,
    onToggle: () -> Unit
) {
    val categoryColor = when (category) {
        PrayerCategory.FARD -> FardColor
        PrayerCategory.SUNNAT, PrayerCategory.NAFL -> SunnatColor
        PrayerCategory.WITR -> WitrColor
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isCompleted) categoryColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "bg_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isCompleted) categoryColor else categoryColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 200),
        label = "border_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) Color.White else categoryColor,
        animationSpec = tween(durationMillis = 200),
        label = "text_color"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

/**
 * Get a prayer guide text for the expanded section.
 */
private fun getPrayerGuide(prayerType: PrayerType): String {
    return when (prayerType) {
        PrayerType.FAJR -> "Begin your day with 2 Sunnat Rakats followed by 2 Fard Rakats."
        PrayerType.DHUHR -> "After Zuhr time enters: 4 Sunnat, 4 Fard, then 2 Sunnat Rakats."
        PrayerType.ASR -> "4 Sunnat Rakats followed by 4 Fard Rakats."
        PrayerType.MAGHRIB -> "3 Fard Rakats immediately after sunset, followed by 2 Sunnat."
        PrayerType.ISHA -> "After Esha time: 4 Sunnat, 4 Fard, 2 Sunnat, and 3 Witr Rakats."
    }
}

/**
 * Card displaying forbidden times when prayer is not allowed.
 *
 * There are three forbidden (makruh) times:
 * 1. During Sunrise (~20 minutes)
 * 2. At Zenith (very brief, ~5 minutes)
 * 3. During Sunset (~20 minutes)
 *
 * Reference: Islam-QA - Times when prayer is prohibited
 */
@Composable
fun ForbiddenTimeCard(
    forbiddenTime: ForbiddenTime,
    modifier: Modifier = Modifier
) {
    val cardColor = if (forbiddenTime.isCurrentlyActive) {
        WarningRed.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val iconColor = if (forbiddenTime.isCurrentlyActive) {
        WarningRed
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val icon: ImageVector = when (forbiddenTime.type) {
        ForbiddenTimeType.SUNRISE -> Icons.Default.Warning
        ForbiddenTimeType.ZENITH -> Icons.Default.Warning
        ForbiddenTimeType.SUNSET -> Icons.Default.Warning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Forbidden time",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = forbiddenTime.type.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (forbiddenTime.isCurrentlyActive) {
                            WarningRed
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = forbiddenTime.type.arabicName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (forbiddenTime.isCurrentlyActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "NOW - Prayer not allowed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningRed
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${forbiddenTime.startTimeFormatted} - ${forbiddenTime.endTimeFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${forbiddenTime.durationMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card showing a prayer time range with start and end times.
 *
 * Each prayer has a valid time window:
 * - Fajr: Dawn to Sunrise
 * - Dhuhr: After zenith to Asr
 * - Asr: Afternoon to Sunset
 * - Maghrib: Sunset to twilight end
 * - Isha: Night to midnight
 *
 * Reference: Islam 365 - When to Pray: Understanding the Five Daily Prayer Times
 */
@Composable
fun PrayerRangeCard(
    prayerTimeRange: PrayerTimeRange,
    modifier: Modifier = Modifier
) {
    val rangeColor = when (prayerTimeRange.preferredPortion) {
        com.mua.prayertracker.domain.model.PreferredPortion.EARLY -> SuccessGreen.copy(alpha = 0.15f)
        com.mua.prayertracker.domain.model.PreferredPortion.MIDDLE -> GoldenAccent.copy(alpha = 0.15f)
        com.mua.prayertracker.domain.model.PreferredPortion.LATE -> WarningRed.copy(alpha = 0.15f)
    }

    val statusIndicatorColor = when (prayerTimeRange.preferredPortion) {
        com.mua.prayertracker.domain.model.PreferredPortion.EARLY -> SuccessGreen
        com.mua.prayertracker.domain.model.PreferredPortion.MIDDLE -> GoldenAccent
        com.mua.prayertracker.domain.model.PreferredPortion.LATE -> WarningRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = rangeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusIndicatorColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${prayerTimeRange.prayerType.displayName} Time Window",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (prayerTimeRange.preferredPortion) {
                            com.mua.prayertracker.domain.model.PreferredPortion.EARLY -> "Best Time - Pray Early"
                            com.mua.prayertracker.domain.model.PreferredPortion.MIDDLE -> "Acceptable Time"
                            com.mua.prayertracker.domain.model.PreferredPortion.LATE -> "Late - Still Valid"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusIndicatorColor
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${prayerTimeRange.startTimeFormatted} - ${prayerTimeRange.endTimeFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${prayerTimeRange.durationMinutes} min window",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Section header for different categories of prayer information.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
