package com.mua.prayertracker.domain.calculation

import com.mua.prayertracker.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [PrayerTimeEngine]. Expected windows follow the method
 * definitions in the PrayTimes wiki and the Adhan parameters guide:
 * - https://praytimes.org/wiki/Calculation_Methods
 * - https://github.com/batoulapps/adhan-js/blob/master/METHODS.md
 */
class PrayerTimeEngineTest {

    // Dhaka, Bangladesh (UTC+6) on an ordinary mid-July day.
    private fun dhaka(parameters: CalculationParameters) = PrayerTimeEngine.calculate(
        latitude = 23.8103,
        longitude = 90.4125,
        year = 2026,
        month = 7,
        day = 14,
        timezoneOffsetHours = 6.0,
        parameters = parameters,
    )

    // London, UK (UTC+1) at the June solstice: astronomical twilight never
    // ends, so 18-degree Fajr/Isha are unsolvable without a rule fallback.
    private fun londonSolstice(parameters: CalculationParameters) = PrayerTimeEngine.calculate(
        latitude = 51.5074,
        longitude = -0.1278,
        year = 2026,
        month = 6,
        day = 21,
        timezoneOffsetHours = 1.0,
        parameters = parameters,
    )

    private val karachiHanafi =
        CalculationMethod.KARACHI.parameters().copy(madhab = Madhab.HANAFI)

    @Test
    fun `ordinary day produces solvable, ordered times`() {
        val times = dhaka(karachiHanafi)

        assertTrue(times.isSolvable)
        assertTrue("fajr before sunrise", times.fajr < times.sunrise)
        assertTrue("sunrise before dhuhr", times.sunrise < times.dhuhr)
        assertTrue("dhuhr before asr", times.dhuhr < times.asr)
        assertTrue("asr before maghrib", times.asr < times.maghrib)
        assertTrue("maghrib before isha", times.maghrib < times.isha)
    }

    @Test
    fun `dhaka times fall in plausible local windows`() {
        val times = dhaka(karachiHanafi)

        // Mid-July in Dhaka: dawn around 4am, solar noon around midday,
        // sunset shortly after half past six.
        assertTrue("fajr ${times.fajr}", times.fajr in 3.0..5.0)
        assertTrue("sunrise ${times.sunrise}", times.sunrise in 5.0..6.5)
        assertTrue("dhuhr ${times.dhuhr}", times.dhuhr in 11.5..12.75)
        assertTrue("maghrib ${times.maghrib}", times.maghrib in 18.0..19.25)
        assertTrue("isha ${times.isha}", times.isha in 19.25..21.5)
    }

    @Test
    fun `hanafi asr is later than shafi asr`() {
        // Hanafi waits for a shadow of twice the object's length, so Asr
        // always begins later than the standard (factor 1) time.
        val hanafi = dhaka(karachiHanafi)
        val shafi = dhaka(CalculationMethod.KARACHI.parameters().copy(madhab = Madhab.SHAFI))

        assertTrue(hanafi.asr > shafi.asr)
        // Both remain inside the afternoon.
        assertTrue(shafi.asr > shafi.dhuhr && hanafi.asr < hanafi.maghrib)
    }

    @Test
    fun `makkah isha is a fixed ninety minute interval after maghrib`() {
        val times = dhaka(CalculationMethod.MAKKAH.parameters())
        assertEquals(1.5, times.isha - times.maghrib, 1e-9)
    }

    @Test
    fun `tehran maghrib uses a depression angle and falls after sunset`() {
        val tehran = dhaka(CalculationMethod.TEHRAN.parameters())
        val sunsetBased = dhaka(CalculationMethod.KARACHI.parameters())

        // 4.5 degrees below the horizon is reached minutes after sunset.
        assertTrue(tehran.maghrib > sunsetBased.maghrib)
        assertTrue(tehran.maghrib - sunsetBased.maghrib < 1.0)
    }

    @Test
    fun `offsets shift individual prayers by the configured minutes`() {
        val base = dhaka(karachiHanafi)
        val shifted = dhaka(
            karachiHanafi.copy(
                offsetsMinutes = mapOf(PrayerType.FAJR to 30, PrayerType.ISHA to -12)
            )
        )

        assertEquals(0.5, shifted.fajr - base.fajr, 1e-9)
        assertEquals(-0.2, shifted.isha - base.isha, 1e-9)
        assertEquals(base.dhuhr, shifted.dhuhr, 1e-9)
    }

    @Test
    fun `unsolvable twilight falls back to middle of the night without a rule`() {
        val times = londonSolstice(CalculationMethod.KARACHI.parameters())

        assertTrue(times.isSolvable)
        val night = SolarMath.normalizeHours(times.sunrise - times.maghrib)
        val fajrOffset = SolarMath.normalizeHours(times.sunrise - times.fajr)
        val ishaOffset = SolarMath.normalizeHours(times.isha - times.maghrib)
        assertEquals(night / 2.0, fajrOffset, 1e-6)
        assertEquals(night / 2.0, ishaOffset, 1e-6)
    }

    @Test
    fun `seventh of night rule bounds fajr and isha to one seventh`() {
        val times = londonSolstice(
            CalculationMethod.KARACHI.parameters()
                .copy(highLatitudeRule = HighLatitudeRule.SEVENTH_OF_NIGHT)
        )

        assertTrue(times.isSolvable)
        val night = SolarMath.normalizeHours(times.sunrise - times.maghrib)
        val fajrOffset = SolarMath.normalizeHours(times.sunrise - times.fajr)
        val ishaOffset = SolarMath.normalizeHours(times.isha - times.maghrib)
        assertTrue(fajrOffset <= night / 7.0 + 1e-6)
        assertTrue(ishaOffset <= night / 7.0 + 1e-6)
    }

    @Test
    fun `twilight angle rule allows angle divided by sixty of the night`() {
        val parameters = CalculationMethod.ISNA.parameters()
            .copy(highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE)
        val times = londonSolstice(parameters)

        assertTrue(times.isSolvable)
        val night = SolarMath.normalizeHours(times.sunrise - times.maghrib)
        val ishaOffset = SolarMath.normalizeHours(times.isha - times.maghrib)
        assertTrue(ishaOffset <= night * (parameters.ishaAngle / 60.0) + 1e-6)
    }

    @Test
    fun `polar day is reported as unsolvable instead of fabricating times`() {
        // Tromso, Norway at the June solstice: the sun never sets.
        val times = PrayerTimeEngine.calculate(
            latitude = 69.6492,
            longitude = 18.9553,
            year = 2026,
            month = 6,
            day = 21,
            timezoneOffsetHours = 2.0,
            parameters = CalculationMethod.KARACHI.parameters(),
        )

        assertFalse(times.isSolvable)
    }

    @Test
    fun `islamic midnight lies between maghrib and the following fajr`() {
        val times = dhaka(karachiHanafi)
        val nightLength = SolarMath.normalizeHours(times.fajr - times.maghrib)
        val midnightOffset = SolarMath.normalizeHours(times.islamicMidnight - times.maghrib)

        assertEquals(nightLength / 2.0, midnightOffset, 1e-6)
    }
}
