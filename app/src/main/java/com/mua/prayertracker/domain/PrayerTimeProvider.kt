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
import com.mua.prayertracker.domain.location.GeoPoint
import com.mua.prayertracker.domain.model.CompletePrayerSchedule
import com.mua.prayertracker.domain.model.ForbiddenTime
import com.mua.prayertracker.domain.model.ForbiddenTimeType
import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerCategory
import com.mua.prayertracker.domain.model.PrayerTimeRange
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnit
import com.mua.prayertracker.domain.model.PreferredPortion
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
import kotlin.math.round
import kotlin.math.roundToInt
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

    /**
     * Result type for complete prayer schedule including ranges and forbidden times.
     * This provides the full prayer timing information needed for the UI.
     *
     * References:
     * - Islam 365: When to Pray - Understanding the Five Daily Prayer Times
     * - Islam Question & Answer: What Are the Times of the Five Daily Prayers?
     */
    sealed class CompleteScheduleResult {
        data class Success(val schedule: CompletePrayerSchedule) : CompleteScheduleResult()
        object PermissionDenied : CompleteScheduleResult()
        object LocationUnavailable : CompleteScheduleResult()
        data class PolarAnomalyError(val latitude: Double) : CompleteScheduleResult()
        data class Error(val cause: Throwable) : CompleteScheduleResult()
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
     * Rounds to the nearest multiple of 360 (Astronomical Algorithms);
     * truncation would leave values like 350 or -340 out of range.
     */
    private fun closestAngle(angle: Double): Double {
        return if (angle >= -180 && angle <= 180) {
            angle
        } else {
            angle - 360.0 * round(angle / 360.0)
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
        // STEP 8.5: HIGH-LATITUDE FALLBACK
        // =====================================================
        // At high latitudes twilight may never reach the fajr/isha angle, which
        // makes the hour-angle equation unsolvable (NaN). When sunrise/sunset
        // still exist, substitute times derived from the high-latitude rule.
        var resolvedFajr = fajrHours
        var resolvedIsha = ishaHours
        if ((resolvedFajr.isNaN() || resolvedIsha.isNaN()) &&
            !sunriseHours.isNaN() && !sunsetHours.isNaN()
        ) {
            val rule = config.highLatitudeRule ?: HighLatitudeRule.MIDDLE_OF_NIGHT
            val sunrise = normalizeHours(sunriseHours)
            val sunset = normalizeHours(sunsetHours)
            val nightHours = (sunrise - sunset + 24.0) % 24.0

            val (fajrPortion, ishaPortion) = when (rule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5 to 0.5
                HighLatitudeRule.SEVENTH_OF_NIGHT -> 1.0 / 7.0 to 1.0 / 7.0
                HighLatitudeRule.TWILIGHT_ANGLE ->
                    config.method.fajrAngle / 60.0 to config.method.ishaAngle / 60.0
            }

            if (resolvedFajr.isNaN()) {
                resolvedFajr = sunrise - nightHours * fajrPortion
                logW("Fajr unsolvable at latitude $latitude; using $rule fallback: ${formatTime(resolvedFajr)}")
            }
            if (resolvedIsha.isNaN()) {
                resolvedIsha = sunset + nightHours * ishaPortion
                logW("Isha unsolvable at latitude $latitude; using $rule fallback: ${formatTime(resolvedIsha)}")
            }
        }

        // =====================================================
        // STEP 9: NORMALIZE AND APPLY OFFSETS
        // =====================================================
        logI("")
        logI("STEP 9: NORMALIZE AND APPLY OFFSETS")
        logI("----------------------------------------")

        val offsets = config.offsetsMinutes.mapValues { (_, minutes) -> minutes / 60.0 }

        val finalFajr = normalizeHours(resolvedFajr + (offsets[PrayerType.FAJR] ?: 0.0))
        val finalDhuhr = normalizeHours(dhuhrHours + (offsets[PrayerType.DHUHR] ?: 0.0))
        val finalAsr = normalizeHours(asrHours + (offsets[PrayerType.ASR] ?: 0.0))
        val finalMaghrib = normalizeHours(sunsetHours + (offsets[PrayerType.MAGHRIB] ?: 0.0))
        val finalIsha = normalizeHours(resolvedIsha + (offsets[PrayerType.ISHA] ?: 0.0))

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

    /** Normalize hours to [0, 24). NaN passes through so callers can detect it. */
    private fun normalizeHours(hours: Double): Double {
        if (hours.isNaN()) return hours
        var h = hours % 24.0
        if (h < 0) h += 24.0
        return h
    }

    fun formatTime(decimalHours: Double, format: String = "HH:mm"): String {
        if (decimalHours.isNaN()) return "--:--"
        val normalized = ((decimalHours % 24.0) + 24.0) % 24.0
        // Round to the nearest minute instead of truncating (e.g. 5.9999h is 06:00, not 05:59)
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
        config: CalculationConfig = defaultConfig,
        overrideLocation: GeoPoint? = null
    ): PrayerTimesResult {
        logI("getPrayerTimes() called")

        return try {
            val location: GeoPoint
            if (overrideLocation != null && overrideLocation.isValid) {
                // A manually set location needs neither permission nor GPS.
                logI("Using manually set location: ${overrideLocation.formatted()}")
                location = overrideLocation
            } else {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) {
                    logW("Location permission denied")
                    return PrayerTimesResult.PermissionDenied
                }

                logD("Location permission granted, acquiring location...")
                val deviceLocation = acquireLocation(context)
                    ?: return PrayerTimesResult.LocationUnavailable.also {
                        logW("Location unavailable")
                    }
                location = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
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

            if (rawTimes.values.any { it.isNaN() }) {
                logW("Polar anomaly: unsolvable prayer times at latitude ${location.latitude}")
                return PrayerTimesResult.PolarAnomalyError(location.latitude)
            }

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

    /**
     * Current device location, or null when permission is missing or the
     * location cannot be acquired. Used to show the device position and its
     * distance from a manually set location. Never throws.
     */
    @SuppressLint("MissingPermission")
    suspend fun getDeviceLocation(context: Context): GeoPoint? {
        return try {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
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

    // ==================== PRAYER TIME RANGES ====================

    /**
     * Calculate prayer time ranges for a given set of prayer times.
     * Each prayer has a start time (beginning of validity) and end time
     * (when the next prayer time begins or a specific limit is reached).
     *
     * Prayer Time Ranges (based on Islamic scholarship):
     * - FAJR: Dawn (start) → Sunrise (end)
     * - DHUHR: After zenith (start) → Asr time (end)
     * - ASR: Shadow length (start) → Sunset (end)
     * - MAGHRIB: Sunset (start) → Twilight end (end)
     * - ISHA: Darkness (start) → Midnight (end) [main opinion]
     *
     * @param prayerTimesRaw Map of prayer type to decimal hours
     * @param sunriseHours Sunrise time in decimal hours
     * @param sunsetHours Sunset time in decimal hours
     * @param currentHour Current hour for determining active range
     * @param currentMinute Current minute for determining active range
     * @return Map of prayer type to PrayerTimeRange
     *
     * References:
     * - Islam-QA: What Are the Times of the Five Daily Prayers?
     * - Hijri Guide: How to Calculate Prayer Times
     * - Fiqh Islamonline: Times of the Five Daily Prayers
     */
    fun calculatePrayerTimeRanges(
        prayerTimesRaw: Map<PrayerType, Double>,
        sunriseHours: Double,
        sunsetHours: Double,
        currentHour: Int,
        currentMinute: Int,
        timezoneOffsetHours: Double
    ): Map<PrayerType, PrayerTimeRange> {
        logI("========================================")
        logI("CALCULATING PRAYER TIME RANGES")
        logI("========================================")
        logI("Sunrise: ${formatTime(sunriseHours)} ($sunriseHours hours)")
        logI("Sunset: ${formatTime(sunsetHours)} ($sunsetHours hours)")
        logI("Current time: $currentHour:$currentMinute")
        logI("Timezone offset: $timezoneOffsetHours hours")
        logI("----------------------------------------")

        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Calculate twilight end time (approximately 90 minutes after sunset)
        // This varies by location and season, but ~90 min is a standard approximation
        val twilightDurationMinutes = 90
        val twilightEndHours = sunsetHours + twilightDurationMinutes / 60.0
        logI("Twilight ends approximately: ${formatTime(twilightEndHours)}")

        // Calculate Islamic midnight (halfway between Maghrib and Fajr)
        // This is used as the end time for Isha prayer
        val midnightHours = calculateIslamicMidnight(
            sunsetHours,
            prayerTimesRaw[PrayerType.FAJR] ?: sunriseHours,
            timezoneOffsetHours
        )
        logI("Islamic midnight: ${formatTime(midnightHours)}")

        // Build ranges for each prayer
        val ranges = mutableMapOf<PrayerType, PrayerTimeRange>()

        // FAJR RANGE: Dawn → Sunrise
        // Reference: Islam 365 - Fajr time ends at sunrise
        val fajrStart = prayerTimesRaw[PrayerType.FAJR] ?: sunriseHours - 1.5
        val fajrEnd = sunriseHours
        val fajrRangeMinutes = ((fajrEnd - fajrStart) * 60).toInt()
        val fajrActive = isTimeInRange(currentTimeMinutes, fajrStart, fajrEnd)
        ranges[PrayerType.FAJR] = PrayerTimeRange(
            prayerType = PrayerType.FAJR,
            startTimeFormatted = formatTime(fajrStart),
            endTimeFormatted = formatTime(fajrEnd),
            startTimeHours = fajrStart,
            endTimeHours = fajrEnd,
            durationMinutes = fajrRangeMinutes,
            isCurrentlyActive = fajrActive,
            preferredPortion = calculatePreferredPortion(currentTimeMinutes, fajrStart, fajrEnd)
        )
        logI("FAJR Range: ${formatTime(fajrStart)} → ${formatTime(fajrEnd)} (${fajrRangeMinutes} min, active: $fajrActive)")

        // DHUHR RANGE: After zenith → Asr time
        // Reference: Islam-QA - Dhuhr begins just after zenith, ends when Asr begins
        val dhuhrStart = prayerTimesRaw[PrayerType.DHUHR] ?: (sunriseHours + 6.0)
        val dhuhrEnd = prayerTimesRaw[PrayerType.ASR] ?: (sunsetHours - 2.0)
        val dhuhrRangeMinutes = ((dhuhrEnd - dhuhrStart) * 60).toInt()
        val dhuhrActive = isTimeInRange(currentTimeMinutes, dhuhrStart, dhuhrEnd)
        ranges[PrayerType.DHUHR] = PrayerTimeRange(
            prayerType = PrayerType.DHUHR,
            startTimeFormatted = formatTime(dhuhrStart),
            endTimeFormatted = formatTime(dhuhrEnd),
            startTimeHours = dhuhrStart,
            endTimeHours = dhuhrEnd,
            durationMinutes = dhuhrRangeMinutes,
            isCurrentlyActive = dhuhrActive,
            preferredPortion = calculatePreferredPortion(currentTimeMinutes, dhuhrStart, dhuhrEnd)
        )
        logI("DHUHR Range: ${formatTime(dhuhrStart)} → ${formatTime(dhuhrEnd)} (${dhuhrRangeMinutes} min, active: $dhuhrActive)")

        // ASR RANGE: Afternoon → Sunset
        // Reference: Islam-QA - Asr begins when shadow equals object length, ends at sunset
        // Note: Late Asr (close to sunset) is still valid but discouraged
        val asrStart = prayerTimesRaw[PrayerType.ASR] ?: (sunsetHours - 3.0)
        val asrEnd = sunsetHours
        val asrRangeMinutes = ((asrEnd - asrStart) * 60).toInt()
        val asrActive = isTimeInRange(currentTimeMinutes, asrStart, asrEnd)
        ranges[PrayerType.ASR] = PrayerTimeRange(
            prayerType = PrayerType.ASR,
            startTimeFormatted = formatTime(asrStart),
            endTimeFormatted = formatTime(asrEnd),
            startTimeHours = asrStart,
            endTimeHours = asrEnd,
            durationMinutes = asrRangeMinutes,
            isCurrentlyActive = asrActive,
            preferredPortion = calculatePreferredPortion(currentTimeMinutes, asrStart, asrEnd)
        )
        logI("ASR Range: ${formatTime(asrStart)} → ${formatTime(asrEnd)} (${asrRangeMinutes} min, active: $asrActive)")

        // MAGHRIB RANGE: Sunset → End of twilight
        // Reference: Islam-QA - Maghrib starts at sunset, ends when red twilight disappears
        // This is the SHORTEST window - typically ~90 minutes
        val maghribStart = prayerTimesRaw[PrayerType.MAGHRIB] ?: sunsetHours
        val maghribEnd = twilightEndHours
        val maghribRangeMinutes = ((maghribEnd - maghribStart) * 60).toInt()
        val maghribActive = isTimeInRange(currentTimeMinutes, maghribStart, maghribEnd)
        ranges[PrayerType.MAGHRIB] = PrayerTimeRange(
            prayerType = PrayerType.MAGHRIB,
            startTimeFormatted = formatTime(maghribStart),
            endTimeFormatted = formatTime(maghribEnd),
            startTimeHours = maghribStart,
            endTimeHours = maghribEnd,
            durationMinutes = maghribRangeMinutes,
            isCurrentlyActive = maghribActive,
            preferredPortion = calculatePreferredPortion(currentTimeMinutes, maghribStart, maghribEnd)
        )
        logI("MAGHRIB Range: ${formatTime(maghribStart)} → ${formatTime(maghribEnd)} (${maghribRangeMinutes} min, active: $maghribActive)")

        // ISHA RANGE: Night → Midnight (or Fajr)
        // Reference: Islam-QA - Isha ends at midnight (main opinion), some allow until Fajr
        val ishaStart = prayerTimesRaw[PrayerType.ISHA] ?: (sunsetHours + 2.0)
        val ishaEnd = midnightHours
        val ishaRangeMinutes = if (ishaEnd > ishaStart) {
            ((ishaEnd - ishaStart) * 60).toInt()
        } else {
            // Handles midnight crossing
            ((24.0 - ishaStart + ishaEnd) * 60).toInt()
        }
        val ishaActive = isTimeInRangeCrossingMidnight(currentTimeMinutes, ishaStart, ishaEnd)
        ranges[PrayerType.ISHA] = PrayerTimeRange(
            prayerType = PrayerType.ISHA,
            startTimeFormatted = formatTime(ishaStart),
            endTimeFormatted = formatTime(ishaEnd),
            startTimeHours = ishaStart,
            endTimeHours = ishaEnd,
            durationMinutes = ishaRangeMinutes,
            isCurrentlyActive = ishaActive,
            preferredPortion = calculatePreferredPortion(currentTimeMinutes, ishaStart, ishaEnd)
        )
        logI("ISHA Range: ${formatTime(ishaStart)} → ${formatTime(ishaEnd)} (${ishaRangeMinutes} min, active: $ishaActive)")

        logI("========================================")
        logI("PRAYER TIME RANGES CALCULATION COMPLETE")
        logI("========================================")
        logI("")

        return ranges
    }

    /**
     * Calculate Islamic midnight.
     * Midnight in Islamic time calculation is the midpoint between Maghrib and Fajr.
     *
     * Formula: midnight = (sunset + fajr_next_day) / 2
     *
     * Reference: Islam-QA - What Are the Times of the Five Daily Prayers?
     */
    private fun calculateIslamicMidnight(
        sunsetHours: Double,
        fajrHours: Double,
        timezoneOffsetHours: Double
    ): Double {
        logD("Calculating Islamic midnight from sunset=$sunsetHours, fajr=$fajrHours")

        // If Fajr is before sunset, it means it's the next day's Fajr
        // So we add 24 hours to Fajr for calculation
        var adjustedFajr = fajrHours
        if (adjustedFajr <= sunsetHours) {
            adjustedFajr += 24.0
            logD("Fajr adjusted to next day: $adjustedFajr")
        }

        // Calculate midpoint
        val midnightDecimal = (sunsetHours + adjustedFajr) / 2.0

        // Normalize to 0-24 range
        val midnightNormalized = if (midnightDecimal >= 24.0) midnightDecimal - 24.0 else midnightDecimal

        logD("Islamic midnight calculated: $midnightNormalized hours")
        return midnightNormalized
    }

    /**
     * Check if current time (in minutes) falls within a time range.
     */
    private fun isTimeInRange(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): Boolean {
        val startMinutes = (startHours * 60).toInt()
        val endMinutes = (endHours * 60).toInt()
        return currentMinutes in startMinutes until endMinutes
    }

    /**
     * Check if current time falls within a range that crosses midnight.
     */
    private fun isTimeInRangeCrossingMidnight(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): Boolean {
        val startMinutes = (startHours * 60).toInt()
        val endMinutes = (endHours * 60).toInt()

        return if (endMinutes > startMinutes) {
            // Normal range
            currentMinutes in startMinutes until endMinutes
        } else {
            // Crosses midnight
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    /**
     * Calculate which portion of the prayer time window we're in.
     * Early prayer is always best in Islam.
     *
     * Reference: Fiqh Islamonlone - Preferred vs valid time
     */
    private fun calculatePreferredPortion(
        currentMinutes: Int,
        startHours: Double,
        endHours: Double
    ): PreferredPortion {
        val startMinutes = (startHours * 60).toInt()
        var endMinutes = (endHours * 60).toInt()
        var current = currentMinutes

        // A window ending "before" it starts crosses midnight (e.g. Isha → Islamic midnight)
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

    // ==================== FORBIDDEN TIMES ====================

    /**
     * Calculate forbidden times when prayer is not allowed.
     *
     * There are THREE forbidden (makruh) times:
     * 1. DURING SUNRISE - ~20 minutes after sunrise
     * 2. AT ZENITH - very brief, just before Dhuhr starts
     * 3. DURING SUNSET - ~20 minutes before/after sunset
     *
     * These times are associated with sun worship practices in other religions,
     * so Muslims are instructed to avoid praying at these moments.
     *
     * Reference: Islam-QA - Times when prayer is prohibited
     *
     * @param sunriseHours Sunrise time in decimal hours
     * @param sunsetHours Sunset time in decimal hours
     * @param dhuhrHours Dhuhr start time in decimal hours
     * @param currentHour Current hour
     * @param currentMinute Current minute
     * @return List of ForbiddenTime objects
     */
    fun calculateForbiddenTimes(
        sunriseHours: Double,
        sunsetHours: Double,
        dhuhrHours: Double,
        currentHour: Int,
        currentMinute: Int
    ): List<ForbiddenTime> {
        logI("========================================")
        logI("CALCULATING FORBIDDEN TIMES")
        logI("========================================")
        logI("Sunrise: ${formatTime(sunriseHours)}")
        logI("Sunset: ${formatTime(sunsetHours)}")
        logI("Dhuhr: ${formatTime(dhuhrHours)}")
        logI("Current: $currentHour:$currentMinute")
        logI("----------------------------------------")

        val currentMinutes = currentHour * 60 + currentMinute
        val forbiddenTimes = mutableListOf<ForbiddenTime>()

        // 1. SUNRISE FORBIDDEN TIME
        // Starts at sunrise, ends approximately 20 minutes after
        // Reference: Islam 365 - Forbidden times for prayer
        val sunriseForbiddenDurationMinutes = 20
        val sunriseEndHours = sunriseHours + sunriseForbiddenDurationMinutes / 60.0
        val sunriseForbiddenActive = isTimeInRange(
            currentMinutes,
            sunriseHours,
            sunriseEndHours
        )
        val sunriseForbidden = ForbiddenTime(
            type = ForbiddenTimeType.SUNRISE,
            startTimeFormatted = formatTime(sunriseHours),
            endTimeFormatted = formatTime(sunriseEndHours),
            startTimeHours = sunriseHours,
            endTimeHours = sunriseEndHours,
            durationMinutes = sunriseForbiddenDurationMinutes,
            description = "Forbidden during sunrise - associated with sun worship practices",
            isCurrentlyActive = sunriseForbiddenActive
        )
        forbiddenTimes.add(sunriseForbidden)
        logI("SUNRISE Forbidden: ${formatTime(sunriseHours)} → ${formatTime(sunriseEndHours)} (${sunriseForbiddenDurationMinutes} min, active: $sunriseForbiddenActive)")

        // 2. ZENITH FORBIDDEN TIME (Solar Noon Prohibition)
        // Very brief period when sun is exactly at highest point
        // Just before Dhuhr time begins
        // Reference: Islam-QA - Brief prohibition at solar noon
        val zenithDurationMinutes = 5  // Very brief - typically 1-5 minutes
        val zenithStartHours = dhuhrHours - zenithDurationMinutes / 60.0
        val zenithEndHours = dhuhrHours
        val zenithForbiddenActive = isTimeInRange(
            currentMinutes,
            zenithStartHours,
            zenithEndHours
        )
        val zenithForbidden = ForbiddenTime(
            type = ForbiddenTimeType.ZENITH,
            startTimeFormatted = formatTime(zenithStartHours),
            endTimeFormatted = formatTime(zenithEndHours),
            startTimeHours = zenithStartHours,
            endTimeHours = zenithEndHours,
            durationMinutes = zenithDurationMinutes,
            description = "Forbidden at solar zenith - brief prohibition before Dhuhr",
            isCurrentlyActive = zenithForbiddenActive
        )
        forbiddenTimes.add(zenithForbidden)
        logI("ZENITH Forbidden: ${formatTime(zenithStartHours)} → ${formatTime(zenithEndHours)} (${zenithDurationMinutes} min, active: $zenithForbiddenActive)")

        // 3. SUNSET FORBIDDEN TIME
        // Starts approximately 20 minutes before sunset, ends at Maghrib
        // Reference: Islam 365 - Forbidden during sunset
        val sunsetForbiddenDurationMinutes = 20
        val sunsetStartHours = sunsetHours - sunsetForbiddenDurationMinutes / 60.0
        val sunsetForbiddenActive = isTimeInRange(
            currentMinutes,
            sunsetStartHours,
            sunsetHours
        )
        val sunsetForbidden = ForbiddenTime(
            type = ForbiddenTimeType.SUNSET,
            startTimeFormatted = formatTime(sunsetStartHours),
            endTimeFormatted = formatTime(sunsetHours),
            startTimeHours = sunsetStartHours,
            endTimeHours = sunsetHours,
            durationMinutes = sunsetForbiddenDurationMinutes,
            description = "Forbidden during sunset - associated with sun worship practices",
            isCurrentlyActive = sunsetForbiddenActive
        )
        forbiddenTimes.add(sunsetForbidden)
        logI("SUNSET Forbidden: ${formatTime(sunsetStartHours)} → ${formatTime(sunsetHours)} (${sunsetForbiddenDurationMinutes} min, active: $sunsetForbiddenActive)")

        logI("========================================")
        logI("FORBIDDEN TIMES CALCULATION COMPLETE")
        logI("Total forbidden periods: ${forbiddenTimes.size}")
        logI("========================================")
        logI("")

        return forbiddenTimes
    }

    /**
     * Calculate the complete prayer schedule with all ranges and forbidden times.
     *
     * This is the main entry point for getting comprehensive prayer timing data.
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param year Year
     * @param month Month (1-12)
     * @param day Day of month
     * @param timezoneOffsetHours Timezone offset in hours
     * @param config Calculation configuration
     * @return Map containing prayer times, ranges, and forbidden times
     */
    fun calculateCompletePrayerSchedule(
        latitude: Double,
        longitude: Double,
        year: Int,
        month: Int,
        day: Int,
        timezoneOffsetHours: Double,
        config: CalculationConfig = defaultConfig
    ): Map<String, Any?> {
        logI("========================================")
        logI("CALCULATING COMPLETE PRAYER SCHEDULE")
        logI("========================================")

        // Get base prayer times
        val prayerTimesRaw = calculatePrayerTimes(
            latitude = latitude,
            longitude = longitude,
            year = year,
            month = month,
            day = day,
            timezoneOffsetHours = timezoneOffsetHours,
            config = config
        )

        // Calculate sunrise and sunset
        val (sunriseHours, sunsetHours) = calculateSunriseSunset(
            latitude, longitude, year, month, day, timezoneOffsetHours, config
        )

        // Get current time for determining active ranges
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        logI("Current time for range calculation: $currentHour:$currentMinute")

        // Calculate prayer time ranges
        val prayerRanges = calculatePrayerTimeRanges(
            prayerTimesRaw = prayerTimesRaw,
            sunriseHours = sunriseHours,
            sunsetHours = sunsetHours,
            currentHour = currentHour,
            currentMinute = currentMinute,
            timezoneOffsetHours = timezoneOffsetHours
        )

        // Calculate forbidden times
        val dhuhrHours = prayerTimesRaw[PrayerType.DHUHR] ?: (sunriseHours + 6.0)
        val forbiddenTimes = calculateForbiddenTimes(
            sunriseHours = sunriseHours,
            sunsetHours = sunsetHours,
            dhuhrHours = dhuhrHours,
            currentHour = currentHour,
            currentMinute = currentMinute
        )

        // Calculate Islamic midnight
        val fajrHours = prayerTimesRaw[PrayerType.FAJR] ?: sunriseHours
        val midnightHours = calculateIslamicMidnight(sunsetHours, fajrHours, timezoneOffsetHours)

        // Find currently active prayer range
        val currentRange = prayerRanges.values.find { it.isCurrentlyActive }

        // Find next forbidden time
        val nextForbidden = findNextForbiddenTime(forbiddenTimes, currentHour, currentMinute)

        logI("Current active range: ${currentRange?.prayerType?.displayName ?: "None"}")
        logI("Next forbidden: ${nextForbidden?.type?.displayName ?: "None"}")
        logI("========================================")
        logI("COMPLETE SCHEDULE CALCULATION FINISHED")
        logI("========================================")
        logI("")

        return mapOf(
            "prayerTimesRaw" to prayerTimesRaw,
            "prayerRanges" to prayerRanges,
            "forbiddenTimes" to forbiddenTimes,
            "sunriseTime" to formatTime(sunriseHours),
            "sunsetTime" to formatTime(sunsetHours),
            "midnightTime" to formatTime(midnightHours),
            "currentRange" to currentRange,
            "nextForbidden" to nextForbidden
        )
    }

    /**
     * Calculate sunrise and sunset times.
     */
    private fun calculateSunriseSunset(
        latitude: Double,
        longitude: Double,
        year: Int,
        month: Int,
        day: Int,
        timezoneOffsetHours: Double,
        config: CalculationConfig
    ): Pair<Double, Double> {
        logD("Calculating sunrise/sunset for $year-$month-$day")

        val julianDate = julianDay(year, month, day, 0.0)
        val julianDatePrev = julianDate - 1.0
        val julianDateNext = julianDate + 1.0

        val solarPrev = calculateSolarCoordinates(julianDatePrev)
        val solar = calculateSolarCoordinates(julianDate)
        val solarNext = calculateSolarCoordinates(julianDateNext)

        val approximateTrans = approximateTransit(longitude, solar.apparentSiderealTime, solar.rightAscension)

        // Standard sunrise altitude (includes refraction)
        val solarAltitude = -50.0 / 60.0  // -0.833 degrees
        val elevationCorrection = if (config.elevationMeters > 0) {
            -0.0347 * kotlin.math.sqrt(config.elevationMeters)
        } else 0.0
        val h0 = solarAltitude + elevationCorrection

        val sunriseTransit = correctedHourAngle(
            m0 = approximateTrans,
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
            m0 = approximateTrans,
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

        // Normalize to [0, 24): the UTC event can fall on an adjacent UTC day,
        // and downstream range checks compare against a [0, 24h) clock.
        val sunriseHours = normalizeHours(sunriseTransit + timezoneOffsetHours)
        val sunsetHours = normalizeHours(sunsetTransit + timezoneOffsetHours)

        logD("Sunrise: ${formatTime(sunriseHours)}, Sunset: ${formatTime(sunsetHours)}")

        return Pair(sunriseHours, sunsetHours)
    }

    /**
     * Find the next upcoming forbidden time.
     */
    private fun findNextForbiddenTime(
        forbiddenTimes: List<ForbiddenTime>,
        currentHour: Int,
        currentMinute: Int
    ): ForbiddenTime? {
        val currentMinutes = currentHour * 60 + currentMinute

        return forbiddenTimes
            .filter { !it.isCurrentlyActive }
            .mapNotNull { forbidden ->
                val startMinutes = (forbidden.startTimeHours * 60).toInt()
                // Handle times that might be after midnight
                val adjustedStart = if (startMinutes < currentMinutes) {
                    startMinutes + 24 * 60
                } else {
                    startMinutes
                }
                forbidden to adjustedStart
            }
            .minByOrNull { it.second }
            ?.first
    }

    // ==================== COMPLETE SCHEDULE SUSPEND FUNCTION ====================

    /**
     * Suspend function to get complete prayer schedule with location.
     *
     * References for Islamic prayer time calculations:
     * - Islam 365: When to Pray - Understanding the Five Daily Prayer Times
     * - Islam Question & Answer: What Are the Times of the Five Daily Prayers?
     * - Hijri Guide: How to Calculate Prayer Times: Detailed Explanation
     * - Fiqh Islamonline: Times of the Five Daily Prayers
     *
     * @param context Android context for location access
     * @param config Calculation configuration
     * @return CompleteScheduleResult with prayer schedule or error
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    suspend fun getCompletePrayerSchedule(
        context: Context,
        config: CalculationConfig = defaultConfig,
        overrideLocation: GeoPoint? = null
    ): CompleteScheduleResult {
        logI("getCompletePrayerSchedule() called")

        return try {
            val location: GeoPoint
            if (overrideLocation != null && overrideLocation.isValid) {
                // A manually set location needs neither permission nor GPS.
                logI("Using manually set location for complete schedule: ${overrideLocation.formatted()}")
                location = overrideLocation
            } else {
                // Check permissions
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) {
                    logW("Location permission denied for complete schedule")
                    return CompleteScheduleResult.PermissionDenied
                }

                // Acquire location
                val deviceLocation = acquireLocation(context)
                    ?: return CompleteScheduleResult.LocationUnavailable.also {
                        logW("Location unavailable for complete schedule")
                    }
                location = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
            }

            logI("Location acquired for complete schedule: ${location.latitude}, ${location.longitude}")

            // Get date and timezone
            val calendar = Calendar.getInstance(TimeZone.getDefault())
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val timezoneOffset = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0

            logI("Date for complete schedule: $year-$month-$day")
            logI("Timezone offset: $timezoneOffset hours")

            // Calculate complete schedule
            val result = calculateCompletePrayerSchedule(
                latitude = location.latitude,
                longitude = location.longitude,
                year = year,
                month = month,
                day = day,
                timezoneOffsetHours = timezoneOffset,
                config = config
            )

            @Suppress("UNCHECKED_CAST")
            val prayerTimesRaw = result["prayerTimesRaw"] as Map<PrayerType, Double>

            if (prayerTimesRaw.values.any { it.isNaN() }) {
                logW("Polar anomaly: unsolvable schedule at latitude ${location.latitude}")
                return CompleteScheduleResult.PolarAnomalyError(location.latitude)
            }

            @Suppress("UNCHECKED_CAST")
            val prayerRanges = result["prayerRanges"] as Map<PrayerType, PrayerTimeRange>
            val forbiddenTimes = result["forbiddenTimes"] as List<ForbiddenTime>
            val sunriseTime = result["sunriseTime"] as String
            val sunsetTime = result["sunsetTime"] as String
            val midnightTime = result["midnightTime"] as String
            val currentRange = result["currentRange"] as PrayerTimeRange?
            val nextForbidden = result["nextForbidden"] as ForbiddenTime?

            val schedule = CompletePrayerSchedule(
                prayerRanges = prayerRanges,
                forbiddenTimes = forbiddenTimes,
                sunriseTime = sunriseTime,
                sunsetTime = sunsetTime,
                midnightTime = midnightTime,
                currentTimeRange = currentRange,
                nextForbiddenTime = nextForbidden
            )

            logI("Complete prayer schedule calculated successfully")
            logI("Prayer ranges: ${prayerRanges.size}")
            logI("Forbidden times: ${forbiddenTimes.size}")
            logI("Current range: ${currentRange?.prayerType?.displayName ?: "None"}")
            logI("Next forbidden: ${nextForbidden?.type?.displayName ?: "None"}")

            CompleteScheduleResult.Success(schedule)
        } catch (e: SecurityException) {
            logE("Security exception getting complete schedule: ${e.message}", e)
            CompleteScheduleResult.PermissionDenied
        } catch (e: Exception) {
            logE("Unexpected error getting complete schedule: ${e.message}", e)
            CompleteScheduleResult.Error(e)
        }
    }
}
