package com.mua.prayertracker.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mua.prayertracker.domain.calculation.CalculationParameters
import com.mua.prayertracker.domain.calculation.DailyPrayerTimes
import com.mua.prayertracker.domain.calculation.PrayerTimeEngine
import com.mua.prayertracker.domain.location.GeoPoint
import com.mua.prayertracker.domain.model.CompletePrayerSchedule
import com.mua.prayertracker.domain.model.ForbiddenTime
import com.mua.prayertracker.domain.model.ForbiddenTimeType
import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerTimeRange
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnit
import com.mua.prayertracker.domain.model.PrayerUnitCatalog
import com.mua.prayertracker.domain.model.PreferredPortion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Android-facing facade for prayer times: resolves the device or manual
 * location, asks [PrayerTimeEngine] for the day's times, and derives the
 * UI-facing schedule (time ranges, forbidden periods, next prayer).
 *
 * All calculation strategy lives in the `domain.calculation` package
 * (method/madhab/high-latitude as separate strategies); this object holds
 * no astronomy and no method-specific logic.
 *
 * Fiqh references for the derived windows:
 * - Islam Q&A 9940, "What Are the Times of the Five Daily Prayers?":
 *   https://islamqa.info/en/answers/9940
 * - Islam Q&A 306338, "Times when prayer is prohibited":
 *   https://islamqa.info/en/answers/306338
 */
object PrayerTimeProvider {

    private const val TAG = "PrayerTimeProvider"

    private fun logI(message: String) = Log.i(TAG, message)
    private fun logW(message: String) = Log.w(TAG, message)
    private fun logE(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
    private fun logD(message: String) = Log.d(TAG, message)

    // ==================== RESULT TYPES ====================

    sealed class PrayerTimesResult {
        data class Success(val times: Map<PrayerType, String>) : PrayerTimesResult()
        object PermissionDenied : PrayerTimesResult()
        object LocationUnavailable : PrayerTimesResult()
        data class PolarAnomalyError(val latitude: Double) : PrayerTimesResult()
        data class Error(val cause: Throwable) : PrayerTimesResult()
    }

    sealed class CompleteScheduleResult {
        data class Success(val schedule: CompletePrayerSchedule) : CompleteScheduleResult()
        object PermissionDenied : CompleteScheduleResult()
        object LocationUnavailable : CompleteScheduleResult()
        data class PolarAnomalyError(val latitude: Double) : CompleteScheduleResult()
        data class Error(val cause: Throwable) : CompleteScheduleResult()
    }

    // ==================== PUBLIC API ====================

    /**
     * Today's five prayer times, formatted "HH:mm", for the manual
     * location when given (no permission needed) or the device location.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    suspend fun getPrayerTimes(
        context: Context,
        parameters: CalculationParameters,
        overrideLocation: GeoPoint? = null
    ): PrayerTimesResult {
        return try {
            val location = resolveLocation(context, overrideLocation)
                ?: return locationFailure(context) { permission, _ ->
                    if (permission) PrayerTimesResult.PermissionDenied
                    else PrayerTimesResult.LocationUnavailable
                }

            val times = calculateToday(location, parameters)
            if (!times.isSolvable) {
                logW("Polar anomaly: unsolvable prayer times at latitude ${location.latitude}")
                return PrayerTimesResult.PolarAnomalyError(location.latitude)
            }

            val formatted = times.timesByType().mapValues { (_, hours) -> formatTime(hours) }
            logI("Prayer times for ${location.formatted()}: $formatted")
            PrayerTimesResult.Success(formatted)
        } catch (e: SecurityException) {
            logE("Security exception: ${e.message}", e)
            PrayerTimesResult.PermissionDenied
        } catch (e: Exception) {
            logE("Unexpected error: ${e.message}", e)
            PrayerTimesResult.Error(e)
        }
    }

    /**
     * The full day schedule: prayer validity windows, forbidden periods,
     * sunrise/sunset and Islamic midnight.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    suspend fun getCompletePrayerSchedule(
        context: Context,
        parameters: CalculationParameters,
        overrideLocation: GeoPoint? = null
    ): CompleteScheduleResult {
        return try {
            val location = resolveLocation(context, overrideLocation)
                ?: return locationFailure(context) { permission, _ ->
                    if (permission) CompleteScheduleResult.PermissionDenied
                    else CompleteScheduleResult.LocationUnavailable
                }

            val times = calculateToday(location, parameters)
            if (!times.isSolvable) {
                logW("Polar anomaly: unsolvable schedule at latitude ${location.latitude}")
                return CompleteScheduleResult.PolarAnomalyError(location.latitude)
            }

            CompleteScheduleResult.Success(buildSchedule(times))
        } catch (e: SecurityException) {
            logE("Security exception getting complete schedule: ${e.message}", e)
            CompleteScheduleResult.PermissionDenied
        } catch (e: Exception) {
            logE("Unexpected error getting complete schedule: ${e.message}", e)
            CompleteScheduleResult.Error(e)
        }
    }

    /**
     * Current device location, or null when permission is missing or the
     * location cannot be acquired. Used to show the device position and its
     * distance from a manually set location. Never throws.
     */
    @SuppressLint("MissingPermission")
    suspend fun getDeviceLocation(context: Context): GeoPoint? {
        return try {
            if (!hasLocationPermission(context)) {
                null
            } else {
                acquireLocation(context)
                    ?.let { GeoPoint(it.latitude, it.longitude) }
                    ?.takeIf { it.isValid }
            }
        } catch (e: Exception) {
            logW("Failed to get device location: ${e.message}")
            null
        }
    }

    /** Decimal hours to a clock string, rounded to the nearest minute. */
    fun formatTime(decimalHours: Double, format: String = "HH:mm"): String {
        if (decimalHours.isNaN()) return "--:--"
        val normalized = ((decimalHours % 24.0) + 24.0) % 24.0
        val totalMinutes = (normalized * 60.0).roundToInt() % (24 * 60)
        val hour = (totalMinutes / 60) % 24
        val minute = totalMinutes % 60

        return if (format == "HH:mm") {
            String.format("%02d:%02d", hour, minute)
        } else {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            SimpleDateFormat(format, Locale.getDefault()).format(calendar.time)
        }
    }

    fun getNextPrayer(
        currentHour: Int,
        currentMinute: Int,
        prayerTimes: Map<PrayerType, String>
    ): PrayerType? {
        val currentTimeMinutes = currentHour * 60 + currentMinute

        return prayerTimes.entries
            .mapNotNull { entry ->
                parseTime(entry.value)?.let { (hour, minute) ->
                    entry.key to (hour * 60 + minute)
                }
            }
            .filter { (_, timeInMinutes) -> timeInMinutes > currentTimeMinutes }
            .minByOrNull { (_, timeInMinutes) -> timeInMinutes }
            ?.first
    }

    /**
     * Get time remaining until next prayer.
     */
    fun getTimeUntilNextPrayer(
        currentHour: Int,
        currentMinute: Int,
        prayerTimes: Map<PrayerType, String>
    ): String {
        val nextPrayer = getNextPrayer(currentHour, currentMinute, prayerTimes)
            ?: return "All prayers completed"
        val prayerTime = prayerTimes[nextPrayer] ?: return ""

        val (targetHour, targetMinute) = parseTime(prayerTime) ?: return ""
        val diffMinutes = (targetHour * 60 + targetMinute) - (currentHour * 60 + currentMinute)
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }
    }

    /**
     * The five prayers with their trackable units, derived from
     * [PrayerUnitCatalog] (single source of truth for unit ids).
     */
    fun getPrayersWithUnits(prayerTimes: Map<PrayerType, String>): List<Prayer> {
        return PrayerType.entries.map { type ->
            Prayer(
                type = type,
                time = prayerTimes[type] ?: "--:--",
                units = PrayerUnitCatalog.groupsFor(type).flatMap { group ->
                    group.unitIds.mapIndexed { index, unitId ->
                        PrayerUnit(
                            id = unitId,
                            prayerType = type,
                            category = group.category,
                            rakatNumber = index + 1,
                            displayName = "${group.category.displayName} ${index + 1}"
                        )
                    }
                }
            )
        }
    }

    // ==================== LOCATION ====================

    private fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * A valid manual override wins and needs neither permission nor GPS;
     * otherwise the device location is acquired (null on denial/failure —
     * [locationFailure] decides which result that maps to).
     */
    @SuppressLint("MissingPermission")
    private suspend fun resolveLocation(context: Context, override: GeoPoint?): GeoPoint? {
        if (override != null && override.isValid) {
            logI("Using manually set location: ${override.formatted()}")
            return override
        }
        if (!hasLocationPermission(context)) {
            logW("Location permission denied")
            return null
        }
        return acquireLocation(context)?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private inline fun <T> locationFailure(
        context: Context,
        toResult: (permissionDenied: Boolean, unavailable: Boolean) -> T
    ): T {
        val denied = !hasLocationPermission(context)
        if (!denied) logW("Location unavailable")
        return toResult(denied, !denied)
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun acquireLocation(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val cached = getLastKnownLocation(fusedLocationClient)
        if (cached != null) {
            logD("Using cached location: ${cached.latitude}, ${cached.longitude}")
            return cached
        }

        logD("No cached location, requesting fresh location...")
        return try {
            withTimeout(10_000L) {
                requestFreshLocation(fusedLocationClient)
            }
        } catch (_: Exception) {
            logW("Failed to acquire location")
            null
        }
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun getLastKnownLocation(
        fusedLocationClient: FusedLocationProviderClient
    ): Location? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener { error ->
                logW("Last location failed: ${error.message}")
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun requestFreshLocation(
        fusedLocationClient: FusedLocationProviderClient
    ): Location? = suspendCancellableCoroutine { continuation ->
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                if (continuation.isActive) continuation.resume(result.lastLocation)
            }
        }

        continuation.invokeOnCancellation {
            fusedLocationClient.removeLocationUpdates(callback)
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                logW("Location request failed: ${error.message}")
                fusedLocationClient.removeLocationUpdates(callback)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    // ==================== ENGINE PLUMBING ====================

    /** Run the engine for today's date in the device timezone. */
    private fun calculateToday(
        location: GeoPoint,
        parameters: CalculationParameters
    ): DailyPrayerTimes {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        return PrayerTimeEngine.calculate(
            latitude = location.latitude,
            longitude = location.longitude,
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            timezoneOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0,
            parameters = parameters
        )
    }

    // ==================== SCHEDULE (RANGES + FORBIDDEN TIMES) ====================

    /**
     * Derive the UI schedule from one day's times — typed end to end
     * (this replaces the former Map<String, Any?> hand-off).
     */
    private fun buildSchedule(times: DailyPrayerTimes): CompletePrayerSchedule {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val ranges = buildPrayerRanges(times, currentMinutes)
        val forbidden = buildForbiddenTimes(times, currentMinutes)

        return CompletePrayerSchedule(
            prayerRanges = ranges,
            forbiddenTimes = forbidden,
            sunriseTime = formatTime(times.sunrise),
            sunsetTime = formatTime(times.sunset),
            midnightTime = formatTime(times.islamicMidnight),
            currentTimeRange = ranges.values.find { it.isCurrentlyActive },
            nextForbiddenTime = findNextForbiddenTime(forbidden, currentMinutes)
        )
    }

    /**
     * Each prayer's validity window (Islam Q&A 9940):
     * Fajr: dawn -> sunrise; Dhuhr: zenith -> Asr; Asr: shadow -> sunset;
     * Maghrib: sunset -> end of twilight (~90 min approximation);
     * Isha: nightfall -> Islamic midnight (the main opinion).
     */
    private fun buildPrayerRanges(
        times: DailyPrayerTimes,
        currentMinutes: Int
    ): Map<PrayerType, PrayerTimeRange> {
        // Red-twilight end approximation for the Maghrib window.
        val twilightEnd = times.maghrib + 1.5

        fun range(
            type: PrayerType,
            start: Double,
            end: Double,
            crossesMidnight: Boolean = false
        ): PrayerTimeRange {
            val durationMinutes = if (end > start) {
                ((end - start) * 60).toInt()
            } else {
                ((24.0 - start + end) * 60).toInt()
            }
            val active = if (crossesMidnight) {
                isTimeInRangeCrossingMidnight(currentMinutes, start, end)
            } else {
                isTimeInRange(currentMinutes, start, end)
            }
            return PrayerTimeRange(
                prayerType = type,
                startTimeFormatted = formatTime(start),
                endTimeFormatted = formatTime(end),
                startTimeHours = start,
                endTimeHours = end,
                durationMinutes = durationMinutes,
                isCurrentlyActive = active,
                preferredPortion = calculatePreferredPortion(currentMinutes, start, end)
            )
        }

        return mapOf(
            PrayerType.FAJR to range(PrayerType.FAJR, times.fajr, times.sunrise),
            PrayerType.DHUHR to range(PrayerType.DHUHR, times.dhuhr, times.asr),
            PrayerType.ASR to range(PrayerType.ASR, times.asr, times.sunset),
            PrayerType.MAGHRIB to range(PrayerType.MAGHRIB, times.maghrib, twilightEnd),
            PrayerType.ISHA to range(
                PrayerType.ISHA, times.isha, times.islamicMidnight, crossesMidnight = true
            )
        )
    }

    /**
     * The three daily forbidden (makruh) periods — during sunrise, at the
     * zenith just before Dhuhr, and during sunset (Islam Q&A 306338).
     */
    private fun buildForbiddenTimes(
        times: DailyPrayerTimes,
        currentMinutes: Int
    ): List<ForbiddenTime> {
        fun forbidden(
            type: ForbiddenTimeType,
            start: Double,
            end: Double,
            durationMinutes: Int,
            description: String
        ) = ForbiddenTime(
            type = type,
            startTimeFormatted = formatTime(start),
            endTimeFormatted = formatTime(end),
            startTimeHours = start,
            endTimeHours = end,
            durationMinutes = durationMinutes,
            description = description,
            isCurrentlyActive = isTimeInRange(currentMinutes, start, end)
        )

        return listOf(
            forbidden(
                ForbiddenTimeType.SUNRISE,
                start = times.sunrise,
                end = times.sunrise + 20 / 60.0,
                durationMinutes = 20,
                description = "Forbidden during sunrise - associated with sun worship practices"
            ),
            forbidden(
                ForbiddenTimeType.ZENITH,
                start = times.dhuhr - 5 / 60.0,
                end = times.dhuhr,
                durationMinutes = 5,
                description = "Forbidden at solar zenith - brief prohibition before Dhuhr"
            ),
            forbidden(
                ForbiddenTimeType.SUNSET,
                start = times.sunset - 20 / 60.0,
                end = times.sunset,
                durationMinutes = 20,
                description = "Forbidden during sunset - associated with sun worship practices"
            )
        )
    }

    private fun findNextForbiddenTime(
        forbiddenTimes: List<ForbiddenTime>,
        currentMinutes: Int
    ): ForbiddenTime? {
        return forbiddenTimes
            .filter { !it.isCurrentlyActive }
            .minByOrNull { forbidden ->
                val startMinutes = (forbidden.startTimeHours * 60).toInt()
                if (startMinutes < currentMinutes) startMinutes + 24 * 60 else startMinutes
            }
    }

    // ==================== TIME-OF-DAY HELPERS ====================

    private fun parseTime(time: String): Pair<Int, Int>? {
        val parts = time.split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return hour to minute
    }

    private fun isTimeInRange(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): Boolean {
        val startMinutes = (startHours * 60).toInt()
        val endMinutes = (endHours * 60).toInt()
        return currentMinutes in startMinutes until endMinutes
    }

    private fun isTimeInRangeCrossingMidnight(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): Boolean {
        val startMinutes = (startHours * 60).toInt()
        val endMinutes = (endHours * 60).toInt()

        return if (endMinutes > startMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    /**
     * Which portion of a prayer window the current time falls in; praying
     * early is preferred.
     */
    private fun calculatePreferredPortion(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): PreferredPortion {
        val startMinutes = (startHours * 60).toInt()
        var endMinutes = (endHours * 60).toInt()
        var current = currentMinutes

        // A window ending "before" it starts crosses midnight (e.g. Isha -> Islamic midnight)
        if (endMinutes <= startMinutes) {
            endMinutes += 24 * 60
            if (current < startMinutes) current += 24 * 60
        }

        val totalMinutes = endMinutes - startMinutes
        if (totalMinutes <= 0) return PreferredPortion.EARLY

        val progress = (current - startMinutes).toDouble() / totalMinutes.toDouble()

        return when {
            progress < 0.33 -> PreferredPortion.EARLY
            progress < 0.66 -> PreferredPortion.MIDDLE
            else -> PreferredPortion.LATE
        }
    }
}
