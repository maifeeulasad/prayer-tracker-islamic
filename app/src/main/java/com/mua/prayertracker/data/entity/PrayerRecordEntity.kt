package com.mua.prayertracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing daily prayer records for tracking prayer completion.
 * Each record represents a full day's prayer status.
 */
@Entity(tableName = "prayer_records")
data class PrayerRecordEntity(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD

    // Fajr (2 Sunnat, 2 Fard)
    val fajrSunnat1: Boolean = false,
    val fajrSunnat2: Boolean = false,
    val fajrFard1: Boolean = false,
    val fajrFard2: Boolean = false,

    // Dhuhr (4 Sunnat, 4 Fard, 2 Sunnat)
    val dhuhrSunnatPre1: Boolean = false,
    val dhuhrSunnatPre2: Boolean = false,
    val dhuhrSunnatPre3: Boolean = false,
    val dhuhrSunnatPre4: Boolean = false,
    val dhuhrFard1: Boolean = false,
    val dhuhrFard2: Boolean = false,
    val dhuhrFard3: Boolean = false,
    val dhuhrFard4: Boolean = false,
    val dhuhrSunnatPost1: Boolean = false,
    val dhuhrSunnatPost2: Boolean = false,

    // Asr (4 Sunnat, 4 Fard)
    val asrSunnat1: Boolean = false,
    val asrSunnat2: Boolean = false,
    val asrSunnat3: Boolean = false,
    val asrSunnat4: Boolean = false,
    val asrFard1: Boolean = false,
    val asrFard2: Boolean = false,
    val asrFard3: Boolean = false,
    val asrFard4: Boolean = false,

    // Maghrib (3 Fard, 2 Sunnat)
    val maghribFard1: Boolean = false,
    val maghribFard2: Boolean = false,
    val maghribFard3: Boolean = false,
    val maghribSunnat1: Boolean = false,
    val maghribSunnat2: Boolean = false,

    // Isha (4 Sunnat, 4 Fard, 2 Sunnat, 3 Witr)
    val ishaSunnatPre1: Boolean = false,
    val ishaSunnatPre2: Boolean = false,
    val ishaSunnatPre3: Boolean = false,
    val ishaSunnatPre4: Boolean = false,
    val ishaFard1: Boolean = false,
    val ishaFard2: Boolean = false,
    val ishaFard3: Boolean = false,
    val ishaFard4: Boolean = false,
    val ishaSunnatPost1: Boolean = false,
    val ishaSunnatPost2: Boolean = false,
    val ishaWitr1: Boolean = false,
    val ishaWitr2: Boolean = false,
    val ishaWitr3: Boolean = false
)
