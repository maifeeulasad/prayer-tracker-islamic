package com.mua.prayertracker.domain

import android.Manifest
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
import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerCategory
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Provides accurate prayer times based on location using astronomical calculations.
 * Implements the standard Islamic prayer time calculation algorithms.
 *
 */
object PrayerTimeProvider {

    // Logging tag
    private const val TAG = "PrayerTimeProvider"

    // Enable/disable logging (set to true for debugging)
    private const val ENABLE_LOGGING = true

    // ==================== LOGGING HELPERS ====================

    private fun logD(message: String) {
        if (ENABLE_LOGGING) {
            Log.d(TAG, message)
        }
    }

    private fun logE(message: String, throwable: Throwable? = null) {
        if (ENABLE_LOGGING) {
            Log.e(TAG, message, throwable)
        }
    }

    private fun logI(message: String) {
        if (ENABLE_LOGGING) {
            Log.i(TAG, message)
        }
    }

    private fun logW(message: String) {
        if (ENABLE_LOGGING) {
            Log.w(TAG, message)
        }
    }

    // ==================== ENUMS AND CONFIG ====================

    enum class CalculationMethod(
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaIntervalMinutes: Int = 0
    ) {
        KARACHI(fajrAngle = 18.0, ishaAngle = 18.0),
        MUSLIM_WORLD_LEAGUE(fajrAngle = 18.0, ishaAngle = 17.0),
        ISNA(fajrAngle = 15.0, ishaAngle = 15.0),
        EGYPT(fajrAngle = 19.5, ishaAngle = 17.5),
        MAKKAH(fajrAngle = 18.5, ishaAngle = 0.0, ishaIntervalMinutes = 90),
        TEHRAN(fajrAngle = 17.7, ishaAngle = 14.0)
    }

    enum class Madhab(val shadowFactor: Int) {
        SHAFI(shadowFactor = 1),
        HANAFI(shadowFactor = 2)
    }

    enum class HighLatitudeRule {
        MIDDLE_OF_NIGHT,
        SEVENTH_OF_NIGHT,
        TWILIGHT_ANGLE
    }

    data class CalculationConfig(
        val method: CalculationMethod = CalculationMethod.KARACHI,
        val madhab: Madhab = Madhab.HANAFI,
        val highLatitudeRule: HighLatitudeRule? = null,
        val elevationMeters: Double = 0.0,
        val offsetsMinutes: Map<PrayerType, Int> = emptyMap()
    )

    sealed class PrayerTimesResult {
        data class Success(val times: Map<PrayerType, String>) : PrayerTimesResult()
        object PermissionDenied : PrayerTimesResult()
        object LocationUnavailable : PrayerTimesResult()
        data class PolarAnomalyError(val latitude: Double) : PrayerTimesResult()
        data class Error(val cause: Throwable) : PrayerTimesResult()
    }

    private val defaultConfig = CalculationConfig()

    // ==================== SOLAR COORDINATES ====================

    /**
     * Solar coordinates including declination, right ascension, and apparent sidereal time.
     */
    private data class SolarCoordinates(
        val declination: Double,
        val rightAscension: Double,
        val apparentSiderealTime: Double
    )

    /**
     * Calculate Julian Day from Gregorian date.
     * This returns the JD including fractional hours.
     * Formula from Astronomical Algorithms page 60.
     */
    private fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        val Y = if (month > 2) year else year - 1
        val M = if (month > 2) month else month + 12
        val D = day + hours / 24.0
        val A = floor(Y / 100.0)
        val B = 2.0 - A + floor(A / 4.0)
        val i0 = floor(365.25 * (Y + 4716))
        val i1 = floor(30.6001 * (M + 1))
        return i0 + i1 + D + B - 1524.5
    }

    /**
     * Julian century from epoch.
     */
    private fun julianCentury(jd: Double): Double {
        return (jd - 2451545.0) / 36525.0
    }

    /**
     * Calculate solar coordinates for a given Julian Day.
     */
    private fun calculateSolarCoordinates(julianDay: Double): SolarCoordinates {
        val T = julianCentury(julianDay)

        // Mean solar longitude
        val L0 = unwindAngle(280.4664567 + 36000.76983 * T + 0.0003032 * T.pow(2))

        // Mean lunar longitude (for nutation)
        val Lp = unwindAngle(218.3165 + 481267.8813 * T)

        // Ascending lunar node
        val Ω = unwindAngle(125.04452 - 1934.136261 * T + 0.0020708 * T.pow(2) - T.pow(3) / 450000)

        // Solar equation of center
        val M = unwindAngle(357.52911 + 35999.05029 * T - 0.0001537 * T.pow(2))
        val Mrad = toRadians(M)
        val C = (1.914602 - 0.004817 * T - 0.000014 * T.pow(2)) * sin(Mrad) +
                (0.019993 - 0.000101 * T) * sin(2.0 * Mrad) +
                0.000289 * sin(3.0 * Mrad)

        // True longitude
        val trueLongitude = L0 + C

        // Apparent solar longitude
        val λ = unwindAngle(trueLongitude - 0.00569 - 0.00478 * sin(toRadians(Ω)))

        // Mean sidereal time
        val JD = T * 36525.0 + 2451545.0
        val θ0 = unwindAngle(280.46061837 + 360.98564736629 * (JD - 2451545.0) + 0.000387933 * T.pow(2) - T.pow(3) / 38710000.0)

        // Nutation in longitude
        val ΔΨ = -17.2 / 3600.0 * sin(toRadians(Ω)) +
                1.32 / 3600.0 * sin(2.0 * toRadians(L0)) +
                0.23 / 3600.0 * sin(2.0 * toRadians(Lp)) +
                0.21 / 3600.0 * sin(2.0 * toRadians(Ω))

        // Nutation in obliquity
        val Δε = 9.2 / 3600.0 * cos(toRadians(Ω)) +
                0.57 / 3600.0 * cos(2.0 * toRadians(L0)) +
                0.10 / 3600.0 * cos(2.0 * toRadians(Lp)) +
                0.09 / 3600.0 * cos(2.0 * toRadians(Ω))

        // Mean obliquity
        val ε0 = 23.439291 - 0.013004167 * T - 0.0000001639 * T.pow(2) + 0.0000005036 * T.pow(3)

        // Apparent obliquity
        val εapp = ε0 + 0.00256 * cos(toRadians(Ω))

        // Declination
        val λRad = toRadians(λ)
        val declination = toDegrees(asin(sin(εapp * PI / 180.0) * sin(λRad)))

        // Right ascension
        val rightAscension = unwindAngle(toDegrees(atan2(cos(εapp * PI / 180.0) * sin(λRad), cos(λRad))))

        // Apparent sidereal time with nutation
        val apparentSiderealTime = θ0 + ΔΨ * 3600.0 * cos(toRadians(ε0 + Δε)) / 3600.0

        return SolarCoordinates(
            declination = declination,
            rightAscension = rightAscension,
            apparentSiderealTime = apparentSiderealTime
        )
    }

    // ==================== ASTRONOMICAL CALCULATIONS ====================

    /**
     * Approximate transit time.
     * Equation from Astronomical Algorithms, Jean Meeus page 102.
     */
    private fun approximateTransit(longitude: Double, siderealTime: Double, rightAscension: Double): Double {
        val Lw = -longitude
        return normalizeWithBound((rightAscension + Lw - siderealTime) / 360.0, 1.0)
    }

    /**
     * Normalize angle to [0, max).
     */
    private fun normalizeWithBound(value: Double, max: Double): Double {
        return value - max * floor(value / max)
    }

    /**
     * Corrected transit time using iterative refinement.
     * Equation from Astronomical Algorithms, Jean Meeus page 102.
     */
    private fun correctedTransit(
        m0: Double,
        longitude: Double,
        siderealTime: Double,
        rightAscension: Double,
        prevRightAscension: Double,
        nextRightAscension: Double
    ): Double {
        val Lw = -longitude
        val θ = unwindAngle(siderealTime + 360.985647 * m0)
        val α = unwindAngle(interpolateAngles(rightAscension, prevRightAscension, nextRightAscension, m0))
        val H = closestAngle(θ - Lw - α)
        val Δm = H / -360.0
        return (m0 + Δm) * 24.0
    }

    /**
     * Corrected hour angle for sunrise/sunset/prayer times.
     * Uses iterative refinement for accuracy.
     * Equation from Astronomical Algorithms, Jean Meeus page 102.
     */
    private fun correctedHourAngle(
        m0: Double,
        h0: Double,
        latitude: Double,
        longitude: Double,
        afterTransit: Boolean,
        siderealTime: Double,
        rightAscension: Double,
        prevRightAscension: Double,
        nextRightAscension: Double,
        declination: Double,
        prevDeclination: Double,
        nextDeclination: Double
    ): Double {
        val Lw = -longitude

        // Initial hour angle estimate
        val term1 = sin(toRadians(h0)) - sin(toRadians(latitude)) * sin(toRadians(declination))
        val term2 = cos(toRadians(latitude)) * cos(toRadians(declination))

        if (term2 == 0.0) return 0.0

        val H0 = toDegrees(acos(term1 / term2))
        val m = if (afterTransit) m0 + H0 / 360.0 else m0 - H0 / 360.0

        // Refine using sidereal time
        val θ = unwindAngle(siderealTime + 360.985647 * m)
        val α = unwindAngle(interpolateAngles(rightAscension, prevRightAscension, nextRightAscension, m))
        val δ = interpolate(declination, prevDeclination, nextDeclination, m)
        val H = θ - Lw - α

        // Calculate actual altitude
        val h = altitudeOfCelestialBody(latitude, δ, H)

        // Correction term
        val term3 = h - h0
        val term4 = 360.0 * cos(toRadians(δ)) * cos(toRadians(latitude)) * sin(toRadians(H))

        if (term4 == 0.0) return m * 24.0

        val Δm = term3 / term4
        return (m + Δm) * 24.0
    }

    /**
     * Interpolate value given equidistant previous and next values.
     * Equation from Astronomical Algorithms page 24.
     */
    private fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        val c = b - a
        return y2 + n / 2.0 * (a + b + n * c)
    }

    /**
     * Interpolate angles accounting for unwinding.
     * Equation from Astronomical Algorithms page 24.
     */
    private fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = unwindAngle(y2 - y1)
        val b = unwindAngle(y3 - y2)
        val c = b - a
        return y2 + n / 2.0 * (a + b + n * c)
    }

    /**
     * Altitude of celestial body.
     * Equation from Astronomical Algorithms page 93.
     */
    private fun altitudeOfCelestialBody(latitude: Double, declination: Double, hourAngle: Double): Double {
        val term1 = sin(toRadians(latitude)) * sin(toRadians(declination))
        val term2 = cos(toRadians(latitude)) * cos(toRadians(declination)) * cos(toRadians(hourAngle))
        return toDegrees(asin(term1 + term2))
    }

    /**
     * Get closest angle in range [-180, 180].
     */
    private fun closestAngle(angle: Double): Double {
        return if (angle >= -180 && angle <= 180) {
            angle
        } else {
            angle - 360.0 * (angle / 360.0).toInt()
        }
    }

    // ==================== PRAYER TIME CALCULATION ====================

    /**
     * Calculate prayer times for a given location and date.
     */
    fun calculatePrayerTimes(
        latitude: Double,
        longitude: Double,
        year: Int,
        month: Int,
        day: Int,
        timezoneOffsetHours: Double,
        config: CalculationConfig = defaultConfig
    ): Map<PrayerType, Double> {
        // =====================================================
        // STEP 1: INPUT PARAMETERS LOGGING
        // =====================================================
        logI("========================================")
        logI("INPUT PARAMETERS:")
        logI("  Latitude: $latitude")
        logI("  Longitude: $longitude")
        logI("  Date: $year-$month-$day")
        logI("  Timezone Offset: $timezoneOffsetHours hours")
        logI("  Method: ${config.method}")
        logI("  Madhab: ${config.madhab}")
        logI("  Fajr Angle: ${config.method.fajrAngle}")
        logI("  Isha Angle: ${config.method.ishaAngle}")
        logI("========================================")

        // =====================================================
        // STEP 2: CALCULATE JULIAN DAY AND SOLAR COORDINATES
        // =====================================================
        logI("")
        logI("STEP 2: JULIAN DAY AND SOLAR COORDINATES")
        logI("----------------------------------------")

        // Julian Day at midnight for this date (in local timezone)
        // The adhan algorithm uses JD at midnight, solar coordinates are interpolated
        val julianDate = julianDay(year, month, day, 0.0)
        val julianDatePrev = julianDate - 1.0
        val julianDateNext = julianDate + 1.0

        logI("Julian Date: $julianDate")
        logI("Julian Date Prev: $julianDatePrev")
        logI("Julian Date Next: $julianDateNext")

        // Solar coordinates for interpolation
        val solarPrev = calculateSolarCoordinates(julianDatePrev)
        val solar = calculateSolarCoordinates(julianDate)
        val solarNext = calculateSolarCoordinates(julianDateNext)

        logI("Solar Declination: ${solar.declination} degrees")
        logI("Solar Right Ascension: ${solar.rightAscension} degrees")
        logI("Apparent Sidereal Time: ${solar.apparentSiderealTime} degrees")

        // =====================================================
        // STEP 3: CALCULATE APPROXIMATE TRANSIT
        // =====================================================
        logI("")
        logI("STEP 3: APPROXIMATE TRANSIT")
        logI("----------------------------------------")

        val approximateTransit = approximateTransit(longitude, solar.apparentSiderealTime, solar.rightAscension)
        logI("Approximate Transit: $approximateTransit")

        // =====================================================
        // STEP 4: CALCULATE SUNRISE AND SUNSET
        // =====================================================
        logI("")
        logI("STEP 4: SUNRISE AND SUNSET")
        logI("----------------------------------------")

        // Standard sunrise altitude (includes refraction)
        val solarAltitude = -50.0 / 60.0  // -0.833 degrees
        val elevationCorrection = if (config.elevationMeters > 0) {
            -0.0347 * kotlin.math.sqrt(config.elevationMeters)
        } else 0.0
        val h0 = solarAltitude + elevationCorrection

        logI("Sunrise altitude: $h0 degrees")

        val sunriseTransit = correctedHourAngle(
            m0 = approximateTransit,
            h0 = h0,
            latitude = latitude,
            longitude = longitude,
            afterTransit = false,
            siderealTime = solar.apparentSiderealTime,
            rightAscension = solar.rightAscension,
            prevRightAscension = solarPrev.rightAscension,
            nextRightAscension = solarNext.rightAscension,
            declination = solar.declination,
            prevDeclination = solarPrev.declination,
            nextDeclination = solarNext.declination
        )

        val sunsetTransit = correctedHourAngle(
            m0 = approximateTransit,
            h0 = h0,
            latitude = latitude,
            longitude = longitude,
            afterTransit = true,
            siderealTime = solar.apparentSiderealTime,
            rightAscension = solar.rightAscension,
            prevRightAscension = solarPrev.rightAscension,
            nextRightAscension = solarNext.rightAscension,
            declination = solar.declination,
            prevDeclination = solarPrev.declination,
            nextDeclination = solarNext.declination
        )

        logI("Sunrise transit: $sunriseTransit hours")
        logI("Sunset transit: $sunsetTransit hours")

        // Convert to local time
        val sunriseHours = sunriseTransit + timezoneOffsetHours
        val sunsetHours = sunsetTransit + timezoneOffsetHours

        logI("Sunrise (local): $sunriseHours hours = ${formatTime(sunriseHours)}")
        logI("Sunset (local): $sunsetHours hours = ${formatTime(sunsetHours)}")

        // =====================================================
        // STEP 5: CALCULATE DHUHR
        // =====================================================
        logI("")
        logI("STEP 5: DHUHR")
        logI("----------------------------------------")

        val dhuhrTransit = correctedTransit(
            m0 = approximateTransit,
            longitude = longitude,
            siderealTime = solar.apparentSiderealTime,
            rightAscension = solar.rightAscension,
            prevRightAscension = solarPrev.rightAscension,
            nextRightAscension = solarNext.rightAscension
        )

        val dhuhrHours = dhuhrTransit + timezoneOffsetHours
        logI("Dhuhr transit: $dhuhrTransit hours")
        logI("Dhuhr (local): $dhuhrHours hours = ${formatTime(dhuhrHours)}")

        // =====================================================
        // STEP 6: CALCULATE ASR
        // =====================================================
        logI("")
        logI("STEP 6: ASR")
        logI("----------------------------------------")

        val shadowFactor = config.madhab.shadowFactor.toDouble()

        // Calculate Asr angle
        val tangent = abs(latitude - solar.declination)
        val inverse = shadowFactor + tan(toRadians(tangent))
        val asrAngle = toDegrees(atan(1.0 / inverse))

        logI("Asr angle: $asrAngle degrees")

        val asrTransit = correctedHourAngle(
            m0 = approximateTransit,
            h0 = asrAngle,
            latitude = latitude,
            longitude = longitude,
            afterTransit = true,
            siderealTime = solar.apparentSiderealTime,
            rightAscension = solar.rightAscension,
            prevRightAscension = solarPrev.rightAscension,
            nextRightAscension = solarNext.rightAscension,
            declination = solar.declination,
            prevDeclination = solarPrev.declination,
            nextDeclination = solarNext.declination
        )

        val asrHours = asrTransit + timezoneOffsetHours
        logI("Asr transit: $asrTransit hours")
        logI("Asr (local): $asrHours hours = ${formatTime(asrHours)}")

        // =====================================================
        // STEP 7: CALCULATE FAJR
        // =====================================================
        logI("")
        logI("STEP 7: FAJR")
        logI("----------------------------------------")

        val fajrAngle = -config.method.fajrAngle
        logI("Fajr angle: $fajrAngle degrees")

        val fajrTransit = correctedHourAngle(
            m0 = approximateTransit,
            h0 = fajrAngle,
            latitude = latitude,
            longitude = longitude,
            afterTransit = false,
            siderealTime = solar.apparentSiderealTime,
            rightAscension = solar.rightAscension,
            prevRightAscension = solarPrev.rightAscension,
            nextRightAscension = solarNext.rightAscension,
            declination = solar.declination,
            prevDeclination = solarPrev.declination,
            nextDeclination = solarNext.declination
        )

        val fajrHours = fajrTransit + timezoneOffsetHours
        logI("Fajr transit: $fajrTransit hours")
        logI("Fajr (local): $fajrHours hours = ${formatTime(fajrHours)}")

        // =====================================================
        // STEP 8: CALCULATE ISHA
        // =====================================================
        logI("")
        logI("STEP 8: ISHA")
        logI("----------------------------------------")

        var ishaHours: Double

        if (config.method.ishaIntervalMinutes > 0) {
            // Using interval method
            ishaHours = sunsetHours + config.method.ishaIntervalMinutes / 60.0
            logI("Isha using interval: ${config.method.ishaIntervalMinutes} minutes after Maghrib")
        } else {
            val ishaAngle = -config.method.ishaAngle
            logI("Isha angle: $ishaAngle degrees")

            val ishaTransit = correctedHourAngle(
                m0 = approximateTransit,
                h0 = ishaAngle,
                latitude = latitude,
                longitude = longitude,
                afterTransit = true,
                siderealTime = solar.apparentSiderealTime,
                rightAscension = solar.rightAscension,
                prevRightAscension = solarPrev.rightAscension,
                nextRightAscension = solarNext.rightAscension,
                declination = solar.declination,
                prevDeclination = solarPrev.declination,
                nextDeclination = solarNext.declination
            )

            ishaHours = ishaTransit + timezoneOffsetHours
            logI("Isha transit: $ishaTransit hours")
            logI("Isha (local): $ishaHours hours = ${formatTime(ishaHours)}")
        }

        // =====================================================
        // STEP 9: NORMALIZE AND APPLY OFFSETS
        // =====================================================
        logI("")
        logI("STEP 9: NORMALIZE AND APPLY OFFSETS")
        logI("----------------------------------------")

        // Normalize to valid range [0, 24)
        fun normalize(hours: Double): Double {
            var h = hours % 24.0
            if (h < 0) h += 24.0
            return h
        }

        val offsets = config.offsetsMinutes.mapValues { (_, minutes) -> minutes / 60.0 }

        val finalFajr = normalize(fajrHours + (offsets[PrayerType.FAJR] ?: 0.0))
        val finalDhuhr = normalize(dhuhrHours + (offsets[PrayerType.DHUHR] ?: 0.0))
        val finalAsr = normalize(asrHours + (offsets[PrayerType.ASR] ?: 0.0))
        val finalMaghrib = normalize(sunsetHours + (offsets[PrayerType.MAGHRIB] ?: 0.0))
        val finalIsha = normalize(ishaHours + (offsets[PrayerType.ISHA] ?: 0.0))

        logI("Final Times:")
        logI("  Fajr: ${formatTime(finalFajr)}")
        logI("  Dhuhr: ${formatTime(finalDhuhr)}")
        logI("  Asr: ${formatTime(finalAsr)}")
        logI("  Maghrib: ${formatTime(finalMaghrib)}")
        logI("  Isha: ${formatTime(finalIsha)}")
        logI("========================================")
        logI("")

        return mapOf(
            PrayerType.FAJR to finalFajr,
            PrayerType.DHUHR to finalDhuhr,
            PrayerType.ASR to finalAsr,
            PrayerType.MAGHRIB to finalMaghrib,
            PrayerType.ISHA to finalIsha
        )
    }

    // ==================== UTILITY FUNCTIONS ====================

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
    private fun toDegrees(radians: Double): Double = radians * 180.0 / PI
    private fun unwindAngle(angle: Double): Double = normalizeWithBound(angle, 360.0)

    fun formatTime(decimalHours: Double, format: String = "HH:mm"): String {
        val normalized = ((decimalHours % 24.0) + 24.0) % 24.0
        val totalMinutes = (normalized * 60.0).toInt()
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
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.format(calendar.time)
        }
    }

    fun getNextPrayer(
        currentHour: Int,
        currentMinute: Int,
        prayerTimes: Map<PrayerType, String>
    ): PrayerType? {
        val currentTimeMinutes = currentHour * 60 + currentMinute

        val upcomingPrayers = prayerTimes.entries
            .mapNotNull { entry ->
                parseTime(entry.value)?.let { (hour, minute) ->
                    entry.key to (hour * 60 + minute)
                }
            }
            .filter { (_, timeInMinutes) -> timeInMinutes > currentTimeMinutes }
            .sortedBy { (_, timeInMinutes) -> timeInMinutes }

        return upcomingPrayers.firstOrNull()?.first
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
        val currentMinutes = currentHour * 60 + currentMinute
        val targetMinutes = targetHour * 60 + targetMinute

        val diffMinutes = targetMinutes - currentMinutes
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }
    }
    
    /**
     * Get all prayers with their units.
     */
    fun getPrayersWithUnits(prayerTimes: Map<PrayerType, String>): List<Prayer> {
        return listOf(
            Prayer(
                type = PrayerType.FAJR,
                time = prayerTimes[PrayerType.FAJR] ?: "--:--",
                units = listOf(
                    PrayerUnit("fajr_sunnat_1", PrayerType.FAJR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("fajr_sunnat_2", PrayerType.FAJR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("fajr_fard_1", PrayerType.FAJR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("fajr_fard_2", PrayerType.FAJR, PrayerCategory.FARD, 2, "Fard 2")
                )
            ),
            Prayer(
                type = PrayerType.DHUHR,
                time = prayerTimes[PrayerType.DHUHR] ?: "--:--",
                units = listOf(
                    PrayerUnit("dhuhr_sunnat_pre_1", PrayerType.DHUHR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("dhuhr_sunnat_pre_2", PrayerType.DHUHR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("dhuhr_sunnat_pre_3", PrayerType.DHUHR, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("dhuhr_sunnat_pre_4", PrayerType.DHUHR, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("dhuhr_fard_1", PrayerType.DHUHR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("dhuhr_fard_2", PrayerType.DHUHR, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("dhuhr_fard_3", PrayerType.DHUHR, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("dhuhr_fard_4", PrayerType.DHUHR, PrayerCategory.FARD, 4, "Fard 4"),
                    PrayerUnit("dhuhr_sunnat_post_1", PrayerType.DHUHR, PrayerCategory.SUNNAT, 1, "Sunnat Post 1"),
                    PrayerUnit("dhuhr_sunnat_post_2", PrayerType.DHUHR, PrayerCategory.SUNNAT, 2, "Sunnat Post 2")
                )
            ),
            Prayer(
                type = PrayerType.ASR,
                time = prayerTimes[PrayerType.ASR] ?: "--:--",
                units = listOf(
                    PrayerUnit("asr_sunnat_1", PrayerType.ASR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("asr_sunnat_2", PrayerType.ASR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("asr_sunnat_3", PrayerType.ASR, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("asr_sunnat_4", PrayerType.ASR, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("asr_fard_1", PrayerType.ASR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("asr_fard_2", PrayerType.ASR, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("asr_fard_3", PrayerType.ASR, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("asr_fard_4", PrayerType.ASR, PrayerCategory.FARD, 4, "Fard 4")
                )
            ),
            Prayer(
                type = PrayerType.MAGHRIB,
                time = prayerTimes[PrayerType.MAGHRIB] ?: "--:--",
                units = listOf(
                    PrayerUnit("maghrib_fard_1", PrayerType.MAGHRIB, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("maghrib_fard_2", PrayerType.MAGHRIB, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("maghrib_fard_3", PrayerType.MAGHRIB, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("maghrib_sunnat_1", PrayerType.MAGHRIB, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("maghrib_sunnat_2", PrayerType.MAGHRIB, PrayerCategory.SUNNAT, 2, "Sunnat 2")
                )
            ),
            Prayer(
                type = PrayerType.ISHA,
                time = prayerTimes[PrayerType.ISHA] ?: "--:--",
                units = listOf(
                    PrayerUnit("isha_sunnat_pre_1", PrayerType.ISHA, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("isha_sunnat_pre_2", PrayerType.ISHA, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("isha_sunnat_pre_3", PrayerType.ISHA, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("isha_sunnat_pre_4", PrayerType.ISHA, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("isha_fard_1", PrayerType.ISHA, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("isha_fard_2", PrayerType.ISHA, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("isha_fard_3", PrayerType.ISHA, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("isha_fard_4", PrayerType.ISHA, PrayerCategory.FARD, 4, "Fard 4"),
                    PrayerUnit("isha_sunnat_post_1", PrayerType.ISHA, PrayerCategory.SUNNAT, 1, "Sunnat Post 1"),
                    PrayerUnit("isha_sunnat_post_2", PrayerType.ISHA, PrayerCategory.SUNNAT, 2, "Sunnat Post 2"),
                    PrayerUnit("isha_witr_1", PrayerType.ISHA, PrayerCategory.WITR, 1, "Witr 1"),
                    PrayerUnit("isha_witr_2", PrayerType.ISHA, PrayerCategory.WITR, 2, "Witr 2"),
                    PrayerUnit("isha_witr_3", PrayerType.ISHA, PrayerCategory.WITR, 3, "Witr 3")
                )
            )
        )
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    suspend fun getPrayerTimes(
        context: Context,
        config: CalculationConfig = defaultConfig
    ): PrayerTimesResult {
        logI("getPrayerTimes() called")

        return try {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                logW("Location permission denied")
                return PrayerTimesResult.PermissionDenied
            }

            logD("Location permission granted, acquiring location...")
            val location = acquireLocation(context)
                ?: return PrayerTimesResult.LocationUnavailable.also {
                    logW("Location unavailable")
                }

            logI("Location acquired: ${location.latitude}, ${location.longitude}")

            val calendar = Calendar.getInstance(TimeZone.getDefault())
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val timezoneOffset = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0

            logI("System timezone: ${calendar.timeZone.id}")
            logI("Timezone offset from system: $timezoneOffset hours")
            logI("Date: $year-$month-$day")

            val rawTimes = calculatePrayerTimes(
                latitude = location.latitude,
                longitude = location.longitude,
                year = year,
                month = month,
                day = day,
                timezoneOffsetHours = timezoneOffset,
                config = config
            )

            val formatted = rawTimes.mapValues { (_, decimalHours) ->
                formatTime(decimalHours, "HH:mm")
            }

            logI("Formatted prayer times: $formatted")

            PrayerTimesResult.Success(formatted)
        } catch (e: SecurityException) {
            logE("Security exception: ${e.message}", e)
            PrayerTimesResult.PermissionDenied
        } catch (e: Exception) {
            logE("Unexpected error: ${e.message}", e)
            PrayerTimesResult.Error(e)
        }
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun acquireLocation(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        logD("Acquiring location...")

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
                logD("Last location success: ${location?.latitude}, ${location?.longitude}")
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
                logD("Fresh location result: ${result.lastLocation?.latitude}, ${result.lastLocation?.longitude}")
                fusedLocationClient.removeLocationUpdates(this)
                if (continuation.isActive) continuation.resume(result.lastLocation)
            }
        }

        continuation.invokeOnCancellation {
            logD("Location request cancelled")
            fusedLocationClient.removeLocationUpdates(callback)
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                logW("Location request failed: ${error.message}")
                fusedLocationClient.removeLocationUpdates(callback)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    private fun parseTime(time: String): Pair<Int, Int>? {
        val parts = time.split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return hour to minute
    }
}
