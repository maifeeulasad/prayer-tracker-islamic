package com.mua.prayertracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maifee.velvet.component.card.VelvetCard
import com.mua.prayertracker.domain.model.ForbiddenTime
import com.mua.prayertracker.domain.model.PrayerStatusSnapshot
import com.mua.prayertracker.domain.model.PrayerTimeRange
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.ui.theme.ErrorRed
import com.mua.prayertracker.ui.theme.IslamicGreen
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 24-hour circular prayer schedule, wrapped in a [VelvetCard].
 *
 * Each prayer's validity window is drawn as a colored arc on a ring; a tick
 * at today's current minute shows where the day is. The center carries the
 * same current-vs-next summary as the header, so this view alone tells you
 * both the day's shape and what's happening right now.
 *
 * Midnight is at the top (12 o'clock); the day runs clockwise. Isha wraps
 * past midnight into the next day without a visual break.
 */
@Composable
fun PrayerClockRing(
    ranges: Map<PrayerType, PrayerTimeRange>,
    status: PrayerStatusSnapshot,
    forbiddenTimes: List<ForbiddenTime> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var nowMinute by remember { mutableIntStateOf(currentMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinute = currentMinuteOfDay()
            delay(30_000L)
        }
    }

    VelvetCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                val trackColor = MaterialTheme.colorScheme.outlineVariant
                val tickColor = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.size(220.dp)) {
                    val strokeDp = 18f
                    val ringPad = strokeDp / 2f + 6f
                    val size = Size(
                        size.width - ringPad * 2,
                        size.height - ringPad * 2,
                    )
                    val topLeft = Offset(ringPad, ringPad)

                    // Base track
                    drawArc(
                        color = trackColor.copy(alpha = 0.5f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = size,
                        style = Stroke(width = strokeDp),
                    )

                    // Prayer arcs — bleed each sweep by a tiny amount so
                    // adjacent windows (Dhuhr/Asr, Asr/Maghrib) don't show a
                    // sub-pixel gap where their integer-minute edges meet.
                    PrayerType.entries.forEach { type ->
                        val range = ranges[type] ?: return@forEach
                        val (startDeg, sweepDeg) = arcForWindow(
                            startHours = range.startTimeHours,
                            endHours = range.endTimeHours,
                        )
                        drawArc(
                            color = colorFor(type),
                            startAngle = startDeg,
                            sweepAngle = sweepDeg + 0.4f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = size,
                            style = Stroke(width = strokeDp),
                        )
                    }

                    // Forbidden-time marks — thin red arcs on top of whatever
                    // prayer band they sit in (sunrise/zenith/sunset).
                    forbiddenTimes.forEach { forbidden ->
                        val (startDeg, sweepDeg) = arcForWindow(
                            startHours = forbidden.startTimeHours,
                            endHours = forbidden.endTimeHours,
                        )
                        if (sweepDeg <= 0f) return@forEach
                        drawArc(
                            color = ErrorRed,
                            startAngle = startDeg,
                            // Give very short forbidden slots a visible minimum.
                            sweepAngle = sweepDeg.coerceAtLeast(1.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = size,
                            style = Stroke(width = strokeDp),
                        )
                    }

                    // Current-time tick
                    val nowAngle = minuteToDegrees(nowMinute)
                    val radius = min(size.width, size.height) / 2f
                    val center = Offset(
                        topLeft.x + size.width / 2f,
                        topLeft.y + size.height / 2f,
                    )
                    val innerR = radius - strokeDp
                    val outerR = radius + strokeDp / 2f + 4f
                    val rad = Math.toRadians(nowAngle.toDouble())
                    val cx = cos(rad).toFloat()
                    val sy = sin(rad).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x + cx * innerR, center.y + sy * innerR),
                        end = Offset(center.x + cx * outerR, center.y + sy * outerR),
                        strokeWidth = 3f,
                    )
                }

                CenterLabel(status = status, nowMinute = nowMinute)
            }

            Spacer(Modifier.height(12.dp))
            Legend(ranges = ranges)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Grey = no prayer window · red = forbidden",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenterLabel(status: PrayerStatusSnapshot, nowMinute: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val current = status.current
        if (current != null) {
            Text(
                text = current.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = IslamicGreen,
            )
            Text(
                text = "${status.currentEndsIn ?: ""} left",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = formatClock(nowMinute),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = IslamicGreen,
            )
            val next = status.next
            if (next != null) {
                Text(
                    text = "${next.displayName} in ${status.nextStartsIn ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Legend(ranges: Map<PrayerType, PrayerTimeRange>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PrayerType.entries.forEach { type ->
            val range = ranges[type]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colorFor(type)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = type.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = range?.startTimeFormatted ?: "--:--",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ==================== GEOMETRY HELPERS ====================

/**
 * Canvas angle for a given minute of the day. 0° on `drawArc` is at 3 o'clock
 * and goes clockwise, so midnight (top) is at -90°.
 */
private fun minuteToDegrees(minute: Int): Float {
    val fraction = minute / (24f * 60f)
    return -90f + fraction * 360f
}

/**
 * Map a (startHours, endHours) window to a (startDeg, sweepDeg) arc. Handles
 * windows that cross midnight by extending the end past 24h.
 */
private fun arcForWindow(startHours: Double, endHours: Double): Pair<Float, Float> {
    val startMin = (startHours * 60).toInt()
    val endMin = (endHours * 60).toInt().let { if (it <= startMin) it + 24 * 60 else it }
    val sweep = max(0, endMin - startMin).toFloat() / (24f * 60f) * 360f
    return minuteToDegrees(startMin) to sweep
}

/**
 * Five distinct hues walking the day's temperature, so each waqt is
 * immediately distinguishable on the ring.
 *
 * - Fajr: indigo — pre-dawn
 * - Dhuhr: islamic green — high noon
 * - Asr: gold — mid-afternoon
 * - Maghrib: burnt orange — sunset (distinct from Asr's gold)
 * - Isha: teal — night
 */
private fun colorFor(type: PrayerType): Color = when (type) {
    PrayerType.FAJR -> Color(0xFF7B68EE)       // Indigo
    PrayerType.DHUHR -> Color(0xFF1E5945)      // Islamic green
    PrayerType.ASR -> Color(0xFFC5A059)        // Gold
    PrayerType.MAGHRIB -> Color(0xFFE07A3C)    // Burnt orange — clearly not gold
    PrayerType.ISHA -> Color(0xFF4ECDC4)       // Teal
}

private fun currentMinuteOfDay(): Int {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}

private fun formatClock(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}
