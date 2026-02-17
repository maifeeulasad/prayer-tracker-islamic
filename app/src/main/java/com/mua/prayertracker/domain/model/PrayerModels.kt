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