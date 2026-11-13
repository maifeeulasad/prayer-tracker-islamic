package com.mua.prayertracker.domain.calculation

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Pure solar astronomy used by [PrayerTimeEngine]: Julian dates, solar
 * coordinates, transit and hour-angle solutions. No Android dependencies,
 * so everything here is testable on the JVM.
 *
 * References:
 * - Jean Meeus, "Astronomical Algorithms", 2nd ed. (Willmann-Bell, 1998):
 *   Julian Day p. 60; interpolation p. 24; apparent solar coordinates
 *   ch. 25; transit and rising/setting corrections p. 102; altitude p. 93.
 * - The structure follows the reference implementation of these formulas
 *   in the Adhan library family: https://github.com/batoulapps/adhan-js
 */
internal object SolarMath {

    /**
     * Solar coordinates including declination, right ascension, and
     * apparent sidereal time.
     */
    data class SolarCoordinates(
        val declination: Double,
        val rightAscension: Double,
        val apparentSiderealTime: Double,
    )

    /** Degrees to radians. */
    fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    /** Radians to degrees. */
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI

    /** Normalize an angle to [0, 360). */
    fun unwindAngle(angle: Double): Double = normalizeWithBound(angle, 360.0)

    /** Normalize a value to [0, max). */
    fun normalizeWithBound(value: Double, max: Double): Double =
        value - max * floor(value / max)

    /** Normalize hours to [0, 24). NaN passes through so callers can detect it. */
    fun normalizeHours(hours: Double): Double {
        if (hours.isNaN()) return hours
        var h = hours % 24.0
        if (h < 0) h += 24.0
        return h
    }

    /**
     * Closest angle to zero, in (-180, 180]: the angle minus the nearest
     * multiple of 360 (Meeus p. 102 uses this for the transit correction).
     */
    fun closestAngle(angle: Double): Double =
        if (angle in -180.0..180.0) angle else angle - 360.0 * (angle / 360.0).roundToLong()

    /**
     * Julian Day from a Gregorian date, including fractional hours.
     * Formula from Astronomical Algorithms p. 60.
     */
    fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        val y = if (month > 2) year else year - 1
        val m = if (month > 2) month else month + 12
        val d = day + hours / 24.0
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        val i0 = floor(365.25 * (y + 4716))
        val i1 = floor(30.6001 * (m + 1))
        return i0 + i1 + d + b - 1524.5
    }

    /** Julian century from the J2000 epoch. */
    fun julianCentury(julianDay: Double): Double = (julianDay - 2451545.0) / 36525.0

    /**
     * Apparent solar coordinates for a Julian Day, with nutation applied
     * (Astronomical Algorithms ch. 22 and 25).
     */
    fun solarCoordinates(julianDay: Double): SolarCoordinates {
        val t = julianCentury(julianDay)

        // Mean solar longitude
        val l0 = unwindAngle(280.4664567 + 36000.76983 * t + 0.0003032 * t.pow(2))

        // Mean lunar longitude (for nutation)
        val lp = unwindAngle(218.3165 + 481267.8813 * t)

        // Ascending lunar node
        val omega =
            unwindAngle(125.04452 - 1934.136261 * t + 0.0020708 * t.pow(2) - t.pow(3) / 450000)

        // Solar equation of center
        val m = unwindAngle(357.52911 + 35999.05029 * t - 0.0001537 * t.pow(2))
        val mRad = toRadians(m)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t.pow(2)) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2.0 * mRad) +
                0.000289 * sin(3.0 * mRad)

        // True and apparent solar longitude
        val trueLongitude = l0 + c
        val lambda = unwindAngle(trueLongitude - 0.00569 - 0.00478 * sin(toRadians(omega)))

        // Mean sidereal time
        val jd = t * 36525.0 + 2451545.0
        val theta0 = unwindAngle(
            280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t.pow(2) - t.pow(3) / 38710000.0
        )

        // Nutation in longitude
        val deltaPsi = -17.2 / 3600.0 * sin(toRadians(omega)) +
                1.32 / 3600.0 * sin(2.0 * toRadians(l0)) +
                0.23 / 3600.0 * sin(2.0 * toRadians(lp)) +
                0.21 / 3600.0 * sin(2.0 * toRadians(omega))

        // Nutation in obliquity
        val deltaEpsilon = 9.2 / 3600.0 * cos(toRadians(omega)) +
                0.57 / 3600.0 * cos(2.0 * toRadians(l0)) +
                0.10 / 3600.0 * cos(2.0 * toRadians(lp)) +
                0.09 / 3600.0 * cos(2.0 * toRadians(omega))

        // Mean and apparent obliquity
        val epsilon0 =
            23.439291 - 0.013004167 * t - 0.0000001639 * t.pow(2) + 0.0000005036 * t.pow(3)
        val epsilonApparent = epsilon0 + 0.00256 * cos(toRadians(omega))

        val lambdaRad = toRadians(lambda)
        val declination = toDegrees(asin(sin(toRadians(epsilonApparent)) * sin(lambdaRad)))
        val rightAscension =
            unwindAngle(toDegrees(atan2(cos(toRadians(epsilonApparent)) * sin(lambdaRad), cos(lambdaRad))))

        // Apparent sidereal time with nutation
        val apparentSiderealTime =
            theta0 + deltaPsi * 3600.0 * cos(toRadians(epsilon0 + deltaEpsilon)) / 3600.0

        return SolarCoordinates(
            declination = declination,
            rightAscension = rightAscension,
            apparentSiderealTime = apparentSiderealTime,
        )
    }

    /**
     * Approximate transit (solar noon) as a fraction of the UTC day.
     * Astronomical Algorithms p. 102.
     */
    fun approximateTransit(longitude: Double, siderealTime: Double, rightAscension: Double): Double {
        val lw = -longitude
        return normalizeWithBound((rightAscension + lw - siderealTime) / 360.0, 1.0)
    }

    /**
     * Corrected transit time in UTC hours, by iterative refinement.
     * Astronomical Algorithms p. 102.
     */
    fun correctedTransit(
        approximateTransit: Double,
        longitude: Double,
        siderealTime: Double,
        rightAscension: Double,
        prevRightAscension: Double,
        nextRightAscension: Double,
    ): Double {
        val lw = -longitude
        val theta = unwindAngle(siderealTime + 360.985647 * approximateTransit)
        val alpha = unwindAngle(
            interpolateAngles(rightAscension, prevRightAscension, nextRightAscension, approximateTransit)
        )
        val h = closestAngle(theta - lw - alpha)
        val deltaM = h / -360.0
        return (approximateTransit + deltaM) * 24.0
    }

    /**
     * UTC hours when the sun's centre reaches altitude [angle] degrees,
     * before ([afterTransit] = false) or after transit. NaN when the sun
     * never reaches that altitude on this day (polar day/night or twilight
     * that never ends) — callers decide the fallback.
     * Astronomical Algorithms p. 102.
     */
    fun correctedHourAngle(
        approximateTransit: Double,
        angle: Double,
        latitude: Double,
        longitude: Double,
        afterTransit: Boolean,
        coordinates: SolarCoordinates,
        prevCoordinates: SolarCoordinates,
        nextCoordinates: SolarCoordinates,
    ): Double {
        val lw = -longitude

        val term1 = sin(toRadians(angle)) -
                sin(toRadians(latitude)) * sin(toRadians(coordinates.declination))
        val term2 = cos(toRadians(latitude)) * cos(toRadians(coordinates.declination))
        if (term2 == 0.0) return Double.NaN

        // acos outside [-1, 1] yields NaN, which propagates deliberately.
        val h0 = toDegrees(acos(term1 / term2))
        val m = if (afterTransit) approximateTransit + h0 / 360.0 else approximateTransit - h0 / 360.0

        // Refine using sidereal time
        val theta = unwindAngle(coordinates.apparentSiderealTime + 360.985647 * m)
        val alpha = unwindAngle(
            interpolateAngles(
                coordinates.rightAscension,
                prevCoordinates.rightAscension,
                nextCoordinates.rightAscension,
                m,
            )
        )
        val delta = interpolate(
            coordinates.declination,
            prevCoordinates.declination,
            nextCoordinates.declination,
            m,
        )
        val h = theta - lw - alpha
        val altitude = altitudeOfCelestialBody(latitude, delta, h)

        val term3 = altitude - angle
        val term4 = 360.0 * cos(toRadians(delta)) * cos(toRadians(latitude)) * sin(toRadians(h))
        if (term4 == 0.0) return m * 24.0

        return (m + term3 / term4) * 24.0
    }

    /**
     * Interpolate a value given equidistant previous and next values.
     * Astronomical Algorithms p. 24.
     */
    fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        val c = b - a
        return y2 + n / 2.0 * (a + b + n * c)
    }

    /**
     * Interpolate angles accounting for 360-degree unwinding.
     * Astronomical Algorithms p. 24.
     */
    fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = unwindAngle(y2 - y1)
        val b = unwindAngle(y3 - y2)
        val c = b - a
        return y2 + n / 2.0 * (a + b + n * c)
    }

    /**
     * Altitude of a celestial body for a latitude, declination and hour
     * angle. Astronomical Algorithms p. 93.
     */
    fun altitudeOfCelestialBody(latitude: Double, declination: Double, hourAngle: Double): Double {
        val term1 = sin(toRadians(latitude)) * sin(toRadians(declination))
        val term2 = cos(toRadians(latitude)) * cos(toRadians(declination)) * cos(toRadians(hourAngle))
        return toDegrees(asin(term1 + term2))
    }
}
