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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mua.prayertracker.data.entity.PrayerRecordEntity
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.ui.theme.FardColor
import com.mua.prayertracker.ui.theme.GoldenAccent
import com.mua.prayertracker.ui.theme.SuccessGreen
import com.mua.prayertracker.ui.theme.SunnatColor
import com.mua.prayertracker.ui.theme.WitrColor

/**
 * Data class representing a grouped prayer unit.
 */
data class GroupedPrayerUnit(
    val label: String,
    val unitIds: List<String>,
    val category: PrayerCategory,
    val isCompleted: Boolean
)

enum class PrayerCategory {
    FARD, SUNNAT, WITR
}

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
    record: PrayerRecordEntity?,
    onGroupToggle: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val groupedUnits = getGroupedPrayerUnits(prayerType, record)
    
    val fardCompleted = groupedUnits.filter { it.category == PrayerCategory.FARD }
        .all { it.isCompleted }
    val sunnatCompleted = groupedUnits.filter { it.category == PrayerCategory.SUNNAT }
        .all { it.isCompleted }
    val witrCompleted = groupedUnits.filter { it.category == PrayerCategory.WITR }
        .all { it.isCompleted }

    val allFardCompleted = fardCompleted
    val anyProgress = groupedUnits.any { it.isCompleted }

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
                    Text(
                        text = prayerTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = FardColor
                    )
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
                        isCompleted = group.isCompleted,
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
        PrayerCategory.SUNNAT -> SunnatColor
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
 * Get grouped prayer units for a specific prayer type.
 * Groups rakats together for easier tracking.
 */
private fun getGroupedPrayerUnits(
    prayerType: PrayerType,
    record: PrayerRecordEntity?
): List<GroupedPrayerUnit> {
    if (record == null) {
        return getEmptyGroupedUnits(prayerType)
    }

    return when (prayerType) {
        PrayerType.FAJR -> listOf(
            GroupedPrayerUnit(
                label = "2 Rakat Sunnat",
                unitIds = listOf("fajr_sunnat_1", "fajr_sunnat_2"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.fajrSunnat1 && record.fajrSunnat2
            ),
            GroupedPrayerUnit(
                label = "2 Rakat Fard",
                unitIds = listOf("fajr_fard_1", "fajr_fard_2"),
                category = PrayerCategory.FARD,
                isCompleted = record.fajrFard1 && record.fajrFard2
            )
        )

        PrayerType.DHUHR -> listOf(
            GroupedPrayerUnit(
                label = "4 Rakat Sunnat",
                unitIds = listOf("dhuhr_sunnat_pre_1", "dhuhr_sunnat_pre_2", "dhuhr_sunnat_pre_3", "dhuhr_sunnat_pre_4"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.dhuhrSunnatPre1 && record.dhuhrSunnatPre2 && 
                              record.dhuhrSunnatPre3 && record.dhuhrSunnatPre4
            ),
            GroupedPrayerUnit(
                label = "4 Rakat Fard",
                unitIds = listOf("dhuhr_fard_1", "dhuhr_fard_2", "dhuhr_fard_3", "dhuhr_fard_4"),
                category = PrayerCategory.FARD,
                isCompleted = record.dhuhrFard1 && record.dhuhrFard2 && 
                              record.dhuhrFard3 && record.dhuhrFard4
            ),
            GroupedPrayerUnit(
                label = "2 Rakat Sunnat",
                unitIds = listOf("dhuhr_sunnat_post_1", "dhuhr_sunnat_post_2"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.dhuhrSunnatPost1 && record.dhuhrSunnatPost2
            )
        )

        PrayerType.ASR -> listOf(
            GroupedPrayerUnit(
                label = "4 Rakat Sunnat",
                unitIds = listOf("asr_sunnat_1", "asr_sunnat_2", "asr_sunnat_3", "asr_sunnat_4"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.asrSunnat1 && record.asrSunnat2 && 
                              record.asrSunnat3 && record.asrSunnat4
            ),
            GroupedPrayerUnit(
                label = "4 Rakat Fard",
                unitIds = listOf("asr_fard_1", "asr_fard_2", "asr_fard_3", "asr_fard_4"),
                category = PrayerCategory.FARD,
                isCompleted = record.asrFard1 && record.asrFard2 && 
                              record.asrFard3 && record.asrFard4
            )
        )

        PrayerType.MAGHRIB -> listOf(
            GroupedPrayerUnit(
                label = "3 Rakat Fard",
                unitIds = listOf("maghrib_fard_1", "maghrib_fard_2", "maghrib_fard_3"),
                category = PrayerCategory.FARD,
                isCompleted = record.maghribFard1 && record.maghribFard2 && record.maghribFard3
            ),
            GroupedPrayerUnit(
                label = "2 Rakat Sunnat",
                unitIds = listOf("maghrib_sunnat_1", "maghrib_sunnat_2"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.maghribSunnat1 && record.maghribSunnat2
            )
        )

        PrayerType.ISHA -> listOf(
            GroupedPrayerUnit(
                label = "4 Rakat Sunnat",
                unitIds = listOf("isha_sunnat_pre_1", "isha_sunnat_pre_2", "isha_sunnat_pre_3", "isha_sunnat_pre_4"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.ishaSunnatPre1 && record.ishaSunnatPre2 && 
                              record.ishaSunnatPre3 && record.ishaSunnatPre4
            ),
            GroupedPrayerUnit(
                label = "4 Rakat Fard",
                unitIds = listOf("isha_fard_1", "isha_fard_2", "isha_fard_3", "isha_fard_4"),
                category = PrayerCategory.FARD,
                isCompleted = record.ishaFard1 && record.ishaFard2 && 
                              record.ishaFard3 && record.ishaFard4
            ),
            GroupedPrayerUnit(
                label = "2 Rakat Sunnat",
                unitIds = listOf("isha_sunnat_post_1", "isha_sunnat_post_2"),
                category = PrayerCategory.SUNNAT,
                isCompleted = record.ishaSunnatPost1 && record.ishaSunnatPost2
            ),
            GroupedPrayerUnit(
                label = "3 Rakat Witr",
                unitIds = listOf("isha_witr_1", "isha_witr_2", "isha_witr_3"),
                category = PrayerCategory.WITR,
                isCompleted = record.ishaWitr1 && record.ishaWitr2 && record.ishaWitr3
            )
        )
    }
}

/**
 * Get empty grouped units for when record is null.
 */
private fun getEmptyGroupedUnits(prayerType: PrayerType): List<GroupedPrayerUnit> {
    return when (prayerType) {
        PrayerType.FAJR -> listOf(
            GroupedPrayerUnit("2 Rakat Sunnat", listOf("fajr_sunnat_1", "fajr_sunnat_2"), PrayerCategory.SUNNAT, false),
            GroupedPrayerUnit("2 Rakat Fard", listOf("fajr_fard_1", "fajr_fard_2"), PrayerCategory.FARD, false)
        )
        PrayerType.DHUHR -> listOf(
            GroupedPrayerUnit("4 Rakat Sunnat", listOf("dhuhr_sunnat_pre_1", "dhuhr_sunnat_pre_2", "dhuhr_sunnat_pre_3", "dhuhr_sunnat_pre_4"), PrayerCategory.SUNNAT, false),
            GroupedPrayerUnit("4 Rakat Fard", listOf("dhuhr_fard_1", "dhuhr_fard_2", "dhuhr_fard_3", "dhuhr_fard_4"), PrayerCategory.FARD, false),
            GroupedPrayerUnit("2 Rakat Sunnat", listOf("dhuhr_sunnat_post_1", "dhuhr_sunnat_post_2"), PrayerCategory.SUNNAT, false)
        )
        PrayerType.ASR -> listOf(
            GroupedPrayerUnit("4 Rakat Sunnat", listOf("asr_sunnat_1", "asr_sunnat_2", "asr_sunnat_3", "asr_sunnat_4"), PrayerCategory.SUNNAT, false),
            GroupedPrayerUnit("4 Rakat Fard", listOf("asr_fard_1", "asr_fard_2", "asr_fard_3", "asr_fard_4"), PrayerCategory.FARD, false)
        )
        PrayerType.MAGHRIB -> listOf(
            GroupedPrayerUnit("3 Rakat Fard", listOf("maghrib_fard_1", "maghrib_fard_2", "maghrib_fard_3"), PrayerCategory.FARD, false),
            GroupedPrayerUnit("2 Rakat Sunnat", listOf("maghrib_sunnat_1", "maghrib_sunnat_2"), PrayerCategory.SUNNAT, false)
        )
        PrayerType.ISHA -> listOf(
            GroupedPrayerUnit("4 Rakat Sunnat", listOf("isha_sunnat_pre_1", "isha_sunnat_pre_2", "isha_sunnat_pre_3", "isha_sunnat_pre_4"), PrayerCategory.SUNNAT, false),
            GroupedPrayerUnit("4 Rakat Fard", listOf("isha_fard_1", "isha_fard_2", "isha_fard_3", "isha_fard_4"), PrayerCategory.FARD, false),
            GroupedPrayerUnit("2 Rakat Sunnat", listOf("isha_sunnat_post_1", "isha_sunnat_post_2"), PrayerCategory.SUNNAT, false),
            GroupedPrayerUnit("3 Rakat Witr", listOf("isha_witr_1", "isha_witr_2", "isha_witr_3"), PrayerCategory.WITR, false)
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
