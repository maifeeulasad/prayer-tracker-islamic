package com.mua.prayertracker.domain.model

/**
 * Enum representing the five daily prayers
 */
enum class PrayerType(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "فجر"),
    DHUHR("Dhuhr", "ظهر"),
    ASR("Asr", "عصر"),
    MAGHRIB("Maghrib", "مغرب"),
    ISHA("Isha", "عشاء")
}

/**
 * Enum representing the type of prayer unit (Fard, Sunnat, Witr, Nafl).
 */
enum class PrayerCategory(val displayName: String, val arabicName: String) {
    FARD("Fard", "فرض"),
    SUNNAT("Sunnah", "سنة"),
    WITR("Witr", "وتر"),
    NAFL("Nafl", "نفل")
}

/**
 * Represents a single prayer unit that can be tracked.
 */
data class PrayerUnit(
    val id: String,
    val prayerType: PrayerType,
    val category: PrayerCategory,
    val rakatNumber: Int,
    val displayName: String
)

/**
 * Represents a complete prayer (Waqt) with all its units.
 */
data class Prayer(
    val type: PrayerType,
    val time: String, // Fixed time for now, TODO: implement prayer time calculation
    val units: List<PrayerUnit>
) {
    val displayName: String get() = type.displayName
    val arabicName: String get() = type.arabicName

    val fardUnits: List<PrayerUnit> get() = units.filter { it.category == PrayerCategory.FARD }
    val sunnatUnits: List<PrayerUnit> get() = units.filter { it.category == PrayerCategory.SUNNAT }
    val witrUnits: List<PrayerUnit> get() = units.filter { it.category == PrayerCategory.WITR }
    val naflUnits: List<PrayerUnit> get() = units.filter { it.category == PrayerCategory.NAFL }
}
