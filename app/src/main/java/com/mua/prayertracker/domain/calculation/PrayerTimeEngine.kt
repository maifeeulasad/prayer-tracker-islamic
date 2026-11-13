package com.mua.prayertracker.domain.calculation

import com.mua.prayertracker.domain.model.PrayerType
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Computes one day's prayer times from coordinates, date, timezone and
 * [CalculationParameters]. Pure Kotlin (no Android types), so the whole
 * engine is covered by JVM unit tests.
 *
 * Pipeline (each step delegates to exactly one strategy, so the former
 * method x madhab x latitude special-casing cannot reappear):
 *   1. Solar coordinates for the day and its neighbours ([SolarMath]).
 *   2. Dhuhr from the corrected solar transit.
 *   3. Sunrise/sunset at -0.833° (refraction) with elevation correction.
 *   4. Asr from the madhab's shadow factor ([Madhab]).
 *   5. Fajr/Maghrib/Isha from the authority's angles or interval
 *      ([CalculationParameters], built by [CalculationMethod]).
 *   6. High-latitude bounding of Fajr/Isha ([HighLatitudeRule]).
 *   7. Islamic midnight, user offsets, and normalization to [0, 24).
 *
 * References:
 * - PrayTimes, "Prayer Times Calculation" (definitions of each time and
 *   the Asr shadow formula): https://praytimes.org/wiki/Calculation_Methods
 * - Adhan parameters guide (angles, intervals, high-latitude rules):
 *   https://github.com/batoulapps/adhan-js/blob/master/METHODS.md
 * - Jean Meeus, "Astronomical Algorithms", 2nd ed. — via [SolarMath].
 * - Islam Q&A 9940 (fiqh time windows, Islamic midnight):
 *   https://islamqa.info/en/answers/9940
 */
object PrayerTimeEngine {

    /** Standard sun altitude at sunrise/sunset: refraction + solar radius. */
    private const val SUNRISE_SUNSET_ANGLE = -50.0 / 60.0

    fun calculate(
        latitude: Double,
        longitude: Double,
        year: Int,
        month: Int,
        day: Int,
        timezoneOffsetHours: Double,
        parameters: CalculationParameters,
    ): DailyPrayerTimes {
        // Step 1: solar coordinates for interpolation across the day.
        val julianDate = SolarMath.julianDay(year, month, day)
        val solar = SolarMath.solarCoordinates(julianDate)
        val solarPrev = SolarMath.solarCoordinates(julianDate - 1.0)
        val solarNext = SolarMath.solarCoordinates(julianDate + 1.0)

        val approximateTransit =
            SolarMath.approximateTransit(longitude, solar.apparentSiderealTime, solar.rightAscension)

        fun hourAngleTime(angle: Double, afterTransit: Boolean): Double =
            SolarMath.normalizeHours(
                SolarMath.correctedHourAngle(
                    approximateTransit = approximateTransit,
                    angle = angle,
                    latitude = latitude,
                    longitude = longitude,
                    afterTransit = afterTransit,
                    coordinates = solar,
                    prevCoordinates = solarPrev,
                    nextCoordinates = solarNext,
                ) + timezoneOffsetHours
            )

        // Step 2: Dhuhr — the sun passing the zenith (solar transit).
        val dhuhr = SolarMath.normalizeHours(
            SolarMath.correctedTransit(
                approximateTransit = approximateTransit,
                longitude = longitude,
                siderealTime = solar.apparentSiderealTime,
                rightAscension = solar.rightAscension,
                prevRightAscension = solarPrev.rightAscension,
                nextRightAscension = solarNext.rightAscension,
            ) + timezoneOffsetHours
        )

        // Step 3: sunrise/sunset with elevation correction.
        val elevationCorrection =
            if (parameters.elevationMeters > 0) -0.0347 * sqrt(parameters.elevationMeters) else 0.0
        val horizonAngle = SUNRISE_SUNSET_ANGLE + elevationCorrection
        val sunrise = hourAngleTime(horizonAngle, afterTransit = false)
        val sunset = hourAngleTime(horizonAngle, afterTransit = true)

        // Step 4: Asr — shadow factor of the madhab (PrayTimes formula:
        // altitude = arccot(factor + tan(|latitude - declination|))).
        val asrAngle = SolarMath.toDegrees(
            atan(
                1.0 / (parameters.madhab.shadowFactor +
                        tan(SolarMath.toRadians(abs(latitude - solar.declination))))
            )
        )
        val asr = hourAngleTime(asrAngle, afterTransit = true)

        // Step 5: twilight-based times of the calculation authority.
        var fajr = hourAngleTime(-parameters.fajrAngle, afterTransit = false)

        // Maghrib is sunset unless the authority uses a depression angle
        // (Tehran: 4.5° below the horizon).
        var maghrib = if (parameters.maghribAngle > 0.0) {
            hourAngleTime(-parameters.maghribAngle, afterTransit = true)
        } else {
            sunset
        }
        if (maghrib.isNaN()) maghrib = sunset

        var isha = if (parameters.ishaIntervalMinutes > 0) {
            SolarMath.normalizeHours(maghrib + parameters.ishaIntervalMinutes / 60.0)
        } else {
            hourAngleTime(-parameters.ishaAngle, afterTransit = true)
        }

        // Step 6: high-latitude bounding. With a rule set, Fajr/Isha are
        // kept inside the rule's night fraction even when solvable; with
        // no rule, MIDDLE_OF_NIGHT still substitutes unsolvable times so
        // users near the poles get usable values instead of NaN.
        if (!sunrise.isNaN() && !sunset.isNaN()) {
            val night = SolarMath.normalizeHours(sunrise - sunset)
            val rule = parameters.highLatitudeRule
            val portions = (rule ?: HighLatitudeRule.MIDDLE_OF_NIGHT).nightPortions(parameters)
            val clampSolvable = rule != null

            val maxFajrOffset = night * portions.fajrFraction
            val fajrOffset = SolarMath.normalizeHours(sunrise - fajr)
            if (fajr.isNaN() || (clampSolvable && fajrOffset > maxFajrOffset)) {
                fajr = SolarMath.normalizeHours(sunrise - maxFajrOffset)
            }

            // A fixed interval is an explicit authority instruction, so it
            // is never re-bounded (matching the Adhan behaviour for
            // interval-based Isha).
            if (parameters.ishaIntervalMinutes <= 0) {
                val maxIshaOffset = night * portions.ishaFraction
                val ishaOffset = SolarMath.normalizeHours(isha - sunset)
                if (isha.isNaN() || (clampSolvable && ishaOffset > maxIshaOffset)) {
                    isha = SolarMath.normalizeHours(sunset + maxIshaOffset)
                }
            }
        }

        // Step 7a: Islamic midnight — midpoint between Maghrib and Fajr
        // (today's Fajr approximates tomorrow's, which differs by ~1 min).
        val islamicMidnight = if (!maghrib.isNaN() && !fajr.isNaN()) {
            SolarMath.normalizeHours(maghrib + SolarMath.normalizeHours(fajr - maghrib) / 2.0)
        } else {
            Double.NaN
        }

        // Step 7b: user offsets, then final normalization.
        fun withOffset(hours: Double, type: PrayerType): Double =
            SolarMath.normalizeHours(hours + (parameters.offsetsMinutes[type] ?: 0) / 60.0)

        return DailyPrayerTimes(
            fajr = withOffset(fajr, PrayerType.FAJR),
            sunrise = sunrise,
            dhuhr = withOffset(dhuhr, PrayerType.DHUHR),
            asr = withOffset(asr, PrayerType.ASR),
            sunset = sunset,
            maghrib = withOffset(maghrib, PrayerType.MAGHRIB),
            isha = withOffset(isha, PrayerType.ISHA),
            islamicMidnight = islamicMidnight,
        )
    }
}
