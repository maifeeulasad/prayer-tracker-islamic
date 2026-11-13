package com.mua.prayertracker.domain.model

import com.mua.prayertracker.domain.calculation.CalculationMethod
import com.mua.prayertracker.domain.calculation.CalculationParameters
import com.mua.prayertracker.domain.calculation.HighLatitudeRule
import com.mua.prayertracker.domain.calculation.Madhab

/**
 * User-configurable choices for prayer time calculation. Combines into
 * [CalculationParameters] by taking the chosen authority's published base
 * values and layering the user's own strategy choices on top.
 */
data class PrayerCalculationSettings(
    val method: CalculationMethod = CalculationMethod.KARACHI,
    val madhab: Madhab = Madhab.HANAFI,
    val highLatitudeRule: HighLatitudeRule? = null,
    val elevationMeters: Double = 0.0,
    val fajrOffsetMinutes: Int = 0,
    val dhuhrOffsetMinutes: Int = 0,
    val asrOffsetMinutes: Int = 0,
    val maghribOffsetMinutes: Int = 0,
    val ishaOffsetMinutes: Int = 0
) {
    fun toCalculationParameters(): CalculationParameters {
        return method.parameters().copy(
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
