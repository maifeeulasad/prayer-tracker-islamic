package com.mua.prayertracker.domain.calculation

import com.mua.prayertracker.domain.model.PrayerType

/**
 * One day's computed times in local decimal hours [0, 24), the typed
 * result of [PrayerTimeEngine.calculate] (replacing the former untyped
 * Map<String, Any?> hand-off).
 *
 * A NaN value means that event does not occur on this day at this
 * latitude (polar day/night); [isSolvable] is false in that case and
 * callers surface it as a polar anomaly rather than showing garbage.
 */
data class DailyPrayerTimes(
    val fajr: Double,
    val sunrise: Double,
    val dhuhr: Double,
    val asr: Double,
    /** Actual sunset; equals [maghrib] unless the method uses a Maghrib angle. */
    val sunset: Double,
    val maghrib: Double,
    val isha: Double,
    /**
     * Islamic midnight: the midpoint of the night between Maghrib and
     * (the next day's) Fajr — the end of Isha's preferred window.
     * Reference: Islam Q&A, "What Are the Times of the Five Daily
     * Prayers?" https://islamqa.info/en/answers/9940
     */
    val islamicMidnight: Double,
) {
    /** True when every prayer time exists on this day. */
    val isSolvable: Boolean
        get() = !fajr.isNaN() && !sunrise.isNaN() && !dhuhr.isNaN() &&
                !asr.isNaN() && !sunset.isNaN() && !maghrib.isNaN() && !isha.isNaN()

    /** The five prayer times keyed by [PrayerType]. */
    fun timesByType(): Map<PrayerType, Double> = mapOf(
        PrayerType.FAJR to fajr,
        PrayerType.DHUHR to dhuhr,
        PrayerType.ASR to asr,
        PrayerType.MAGHRIB to maghrib,
        PrayerType.ISHA to isha,
    )
}
