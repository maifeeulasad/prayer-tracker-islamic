package com.mua.prayertracker.domain.model

import com.mua.prayertracker.domain.PrayerTimeProvider

/**
 * User-configurable parameters for prayer time calculation.
 */
data class PrayerCalculationSettings(
    val method: PrayerTimeProvider.CalculationMethod = PrayerTimeProvider.CalculationMethod.KARACHI,
    val madhab: PrayerTimeProvider.Madhab = PrayerTimeProvider.Madhab.HANAFI,
    val highLatitudeRule: PrayerTimeProvider.HighLatitudeRule? = null,
    val elevationMeters: Double = 0.0,
    val fajrOffsetMinutes: Int = 0,
    val dhuhrOffsetMinutes: Int = 0,
    val asrOffsetMinutes: Int = 0,
    val maghribOffsetMinutes: Int = 0,
    val ishaOffsetMinutes: Int = 0
) {
    fun toCalculationConfig(): PrayerTimeProvider.CalculationConfig {
        return PrayerTimeProvider.CalculationConfig(
            method = method,
            madhab = madhab,
            highLatitudeRule = highLatitudeRule,
            elevationMeters = elevationMeters,
            offsetsMinutes = mapOf(
                PrayerType.FAJR to fajrOffsetMinutes,
                PrayerType.DHUHR to dhuhrOffsetMinutes,
                PrayerType.ASR to asrOffsetMinutes,
                PrayerType.MAGHRIB to maghribOffsetMinutes,
                PrayerType.ISHA to ishaOffsetMinutes
            )
        )
    }
}
