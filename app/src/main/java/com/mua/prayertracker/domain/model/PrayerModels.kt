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

/**
 * Represents the completion status of a single prayer unit.
 */
data class PrayerStatus(
    val prayerType: PrayerType,
    val time: String,
    val fardCompleted: Boolean,
    val sunnatCompleted: Boolean,
    val witrCompleted: Boolean = false,
    val naflCompleted: Boolean = false
)

/**
 * Represents the completion status level for a day.
 */
enum class DayCompletionStatus {
    EMPTY,       // No prayers completed
    PARTIAL,     // Some prayers completed
    MISSED,      // Fard prayers missed
    COMPLETE     // All Fard prayers completed
}

/**
 * Represents a single day in the calendar with its completion status.
 */
data class CalendarDay(
    val date: String,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val completionStatus: DayCompletionStatus
)

/**
 * Represents a prayer time range with start and end times.
 * Each prayer has a valid window during which it can be performed.
 *
 * Time ranges are based on Islamic scholarship:
 * - Fajr: Dawn to Sunrise
 * - Dhuhr: After sun passes zenith to Asr time
 * - Asr: Afternoon until Sunset
 * - Maghrib: Sunset to end of twilight
 * - Isha: Night until midnight (or Fajr according to some opinions)
 *
 * @property startTimeFormatted Formatted start time (e.g., "05:30")
 * @property endTimeFormatted Formatted end time (e.g., "06:45")
 * @property startTimeHours Decimal hours for calculations (e.g., 5.5)
 * @property endTimeHours Decimal hours for calculations (e.g., 6.75)
 * @property durationMinutes Total duration in minutes
 * @property isCurrentlyActive Whether current time falls within this range
 * @property preferredPortion Indicates early/middle/late portion (best to earliest)
 *
 * References:
 * - Islam 365: When to Pray - Understanding the Five Daily Prayer Times
 * - Islam Question & Answer: What Are the Times of the Five Daily Prayers?
 * - Hijri Guide: How to Calculate Prayer Times
 * - Fiqh Islamonline: Times of the Five Daily Prayers
 */
data class PrayerTimeRange(
    val prayerType: PrayerType,
    val startTimeFormatted: String,
    val endTimeFormatted: String,
    val startTimeHours: Double,
    val endTimeHours: Double,
    val durationMinutes: Int,
    val isCurrentlyActive: Boolean,
    val preferredPortion: PreferredPortion = PreferredPortion.EARLY
)

/**
 * Indicates the preferred portion of the prayer time window.
 * Early prayer is always best, but late prayer is still valid.
 */
enum class PreferredPortion {
    EARLY,   // Best time - first third of window
    MIDDLE,  // Acceptable - middle portion
    LATE     // Permissible but discouraged - last portion
}

/**
 * Represents forbidden times when prayer (especially voluntary/nafl) is not allowed.
 * These are brief periods associated with sun worship practices.
 *
 * The three forbidden times are:
 * 1. During Sunrise - roughly 10-20 minutes
 * 2. At Zenith - very brief, just before Dhuhr
 * 3. During Sunset - roughly 10-20 minutes
 *
 * @property type The type of forbidden time
 * @property startTimeFormatted When the forbidden period begins
 * @property endTimeFormatted When the forbidden period ends
 * @property startTimeHours Decimal hours for calculations
 * @property endTimeHours Decimal hours for calculations
 * @property durationMinutes Duration of the forbidden period
 * @property description Explanation of why this time is forbidden
 *
 * References:
 * - Islam-QA: Times when prayer is prohibited
 * - Islam 365: Forbidden times for prayer
 */
data class ForbiddenTime(
    val type: ForbiddenTimeType,
    val startTimeFormatted: String,
    val endTimeFormatted: String,
    val startTimeHours: Double,
    val endTimeHours: Double,
    val durationMinutes: Int,
    val description: String,
    val isCurrentlyActive: Boolean
)

/**
 * Types of forbidden prayer times.
 */
enum class ForbiddenTimeType(
    val displayName: String,
    val arabicName: String,
    val reason: String
) {
    SUNRISE(
        displayName = "Sunrise",
        arabicName = "الشروق",
        reason = "Voluntary prayers prohibited during sunrise - associated with sun worship"
    ),
    ZENITH(
        displayName = "Zenith",
        arabicName = "الزوال",
        reason = "Brief prohibition at solar noon when Dhuhr time enters"
    ),
    SUNSET(
        displayName = "Sunset",
        arabicName = "الغروب",
        reason = "Voluntary prayers prohibited during sunset - associated with sun worship"
    )
}

/**
 * Complete prayer times data with all ranges and forbidden times.
 * This provides a comprehensive view of the prayer schedule for a day.
 *
 * @property prayerRanges Map of each prayer type to its time range
 * @property forbiddenTimes List of all forbidden time periods
 * @property sunriseTime Formatted sunrise time
 * @property sunsetTime Formatted sunset time
 * @property currentTimeRange The currently active prayer time range (if any)
 * @property nextForbiddenTime The next upcoming forbidden time (if any)
 */
data class CompletePrayerSchedule(
    val prayerRanges: Map<PrayerType, PrayerTimeRange>,
    val forbiddenTimes: List<ForbiddenTime>,
    val sunriseTime: String,
    val sunsetTime: String,
    val currentTimeRange: PrayerTimeRange?,
    val nextForbiddenTime: ForbiddenTime?,
    val midnightTime: String  // Islamic midnight = halfway between Maghrib and Fajr
)