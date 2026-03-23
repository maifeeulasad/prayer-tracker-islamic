package com.mua.prayertracker.data

import android.content.Context
import com.mua.prayertracker.domain.PrayerTimeProvider
import com.mua.prayertracker.domain.model.PrayerCalculationSettings

/**
 * SharedPreferences storage for prayer calculation settings.
 */
class PrayerSettingsStorage(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PrayerCalculationSettings {
        return PrayerCalculationSettings(
            method = parseEnum(
                preferences.getString(KEY_METHOD, null),
                PrayerTimeProvider.CalculationMethod.KARACHI
            ),
            madhab = parseEnum(
                preferences.getString(KEY_MADHAB, null),
                PrayerTimeProvider.Madhab.HANAFI
            ),
            highLatitudeRule = parseNullableEnum(
                preferences.getString(KEY_HIGH_LATITUDE_RULE, null)
            ),
            elevationMeters = preferences.getFloat(KEY_ELEVATION_METERS, 0f).toDouble(),
            fajrOffsetMinutes = preferences.getInt(KEY_OFFSET_FAJR, 0),
            dhuhrOffsetMinutes = preferences.getInt(KEY_OFFSET_DHUHR, 0),
            asrOffsetMinutes = preferences.getInt(KEY_OFFSET_ASR, 0),
            maghribOffsetMinutes = preferences.getInt(KEY_OFFSET_MAGHRIB, 0),
            ishaOffsetMinutes = preferences.getInt(KEY_OFFSET_ISHA, 0)
        )
    }

    fun save(settings: PrayerCalculationSettings) {
        preferences.edit()
            .putString(KEY_METHOD, settings.method.name)
            .putString(KEY_MADHAB, settings.madhab.name)
            .putString(KEY_HIGH_LATITUDE_RULE, settings.highLatitudeRule?.name)
            .putFloat(KEY_ELEVATION_METERS, settings.elevationMeters.toFloat())
            .putInt(KEY_OFFSET_FAJR, settings.fajrOffsetMinutes)
            .putInt(KEY_OFFSET_DHUHR, settings.dhuhrOffsetMinutes)
            .putInt(KEY_OFFSET_ASR, settings.asrOffsetMinutes)
            .putInt(KEY_OFFSET_MAGHRIB, settings.maghribOffsetMinutes)
            .putInt(KEY_OFFSET_ISHA, settings.ishaOffsetMinutes)
            .apply()
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, fallback: T): T {
        if (value == null) return fallback
        return enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }

    private fun parseNullableEnum(value: String?): PrayerTimeProvider.HighLatitudeRule? {
        if (value.isNullOrBlank()) return null
        return enumValues<PrayerTimeProvider.HighLatitudeRule>().firstOrNull { it.name == value }
    }

    private companion object {
        const val PREFS_NAME = "prayer_settings"
        const val KEY_METHOD = "method"
        const val KEY_MADHAB = "madhab"
        const val KEY_HIGH_LATITUDE_RULE = "high_latitude_rule"
        const val KEY_ELEVATION_METERS = "elevation_meters"
        const val KEY_OFFSET_FAJR = "offset_fajr"
        const val KEY_OFFSET_DHUHR = "offset_dhuhr"
        const val KEY_OFFSET_ASR = "offset_asr"
        const val KEY_OFFSET_MAGHRIB = "offset_maghrib"
        const val KEY_OFFSET_ISHA = "offset_isha"
    }
}
