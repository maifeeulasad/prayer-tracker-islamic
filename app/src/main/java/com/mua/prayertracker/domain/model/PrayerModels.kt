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