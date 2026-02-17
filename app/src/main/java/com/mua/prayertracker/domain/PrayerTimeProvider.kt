package com.mua.prayertracker.domain

import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.model.PrayerUnit
import com.mua.prayertracker.domain.model.PrayerCategory

/**
 * Provides prayer times
 * todo: implement full prayer time calculation based on location
 */
object PrayerTimeProvider {

    /**
     * Get fixed prayer times.
     * In a full implementation, this would calculate times based on location.
     */
    fun getPrayerTimes(): Map<PrayerType, String> {
        return mapOf(
            PrayerType.FAJR to "05:15",
            PrayerType.DHUHR to "12:30",
            PrayerType.ASR to "15:45",
            PrayerType.MAGHRIB to "18:15",
            PrayerType.ISHA to "20:00"
        )
    }

    /**
     * Get the next prayer time.
     */
    fun getNextPrayer(currentHour: Int, currentMinute: Int): PrayerType? {
        val prayerTimes = getPrayerTimes()
        val currentTimeMinutes = currentHour * 60 + currentMinute

        val upcomingPrayers = prayerTimes.entries
            .map { entry ->
                val (hour, minute) = parseTime(entry.value)
                entry.key to (hour * 60 + minute)
            }
            .filter { it.second > currentTimeMinutes }
            .sortedBy { it.second }

        return upcomingPrayers.firstOrNull()?.first
    }

    /**
     * Get time remaining until next prayer.
     */
    fun getTimeUntilNextPrayer(currentHour: Int, currentMinute: Int): String {
        val nextPrayer = getNextPrayer(currentHour, currentMinute) ?: return "All prayers completed"
        val prayerTimes = getPrayerTimes()
        val prayerTime = prayerTimes[nextPrayer] ?: return ""

        val (targetHour, targetMinute) = parseTime(prayerTime)
        val currentMinutes = currentHour * 60 + currentMinute
        val targetMinutes = targetHour * 60 + targetMinute

        val diffMinutes = targetMinutes - currentMinutes
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }

    /**
     * Get all prayers with their units.
     */
    fun getPrayersWithUnits(): List<Prayer> {
        return listOf(
            Prayer(
                type = PrayerType.FAJR,
                time = getPrayerTimes()[PrayerType.FAJR] ?: "05:15",
                units = listOf(
                    PrayerUnit("fajr_sunnat_1", PrayerType.FAJR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("fajr_sunnat_2", PrayerType.FAJR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("fajr_fard_1", PrayerType.FAJR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("fajr_fard_2", PrayerType.FAJR, PrayerCategory.FARD, 2, "Fard 2")
                )
            ),
            Prayer(
                type = PrayerType.DHUHR,
                time = getPrayerTimes()[PrayerType.DHUHR] ?: "12:30",
                units = listOf(
                    PrayerUnit("dhuhr_sunnat_pre_1", PrayerType.DHUHR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("dhuhr_sunnat_pre_2", PrayerType.DHUHR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("dhuhr_sunnat_pre_3", PrayerType.DHUHR, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("dhuhr_sunnat_pre_4", PrayerType.DHUHR, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("dhuhr_fard_1", PrayerType.DHUHR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("dhuhr_fard_2", PrayerType.DHUHR, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("dhuhr_fard_3", PrayerType.DHUHR, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("dhuhr_fard_4", PrayerType.DHUHR, PrayerCategory.FARD, 4, "Fard 4"),
                    PrayerUnit("dhuhr_sunnat_post_1", PrayerType.DHUHR, PrayerCategory.SUNNAT, 1, "Sunnat Post 1"),
                    PrayerUnit("dhuhr_sunnat_post_2", PrayerType.DHUHR, PrayerCategory.SUNNAT, 2, "Sunnat Post 2")
                )
            ),
            Prayer(
                type = PrayerType.ASR,
                time = getPrayerTimes()[PrayerType.ASR] ?: "15:45",
                units = listOf(
                    PrayerUnit("asr_sunnat_1", PrayerType.ASR, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("asr_sunnat_2", PrayerType.ASR, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("asr_sunnat_3", PrayerType.ASR, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("asr_sunnat_4", PrayerType.ASR, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("asr_fard_1", PrayerType.ASR, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("asr_fard_2", PrayerType.ASR, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("asr_fard_3", PrayerType.ASR, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("asr_fard_4", PrayerType.ASR, PrayerCategory.FARD, 4, "Fard 4")
                )
            ),
            Prayer(
                type = PrayerType.MAGHRIB,
                time = getPrayerTimes()[PrayerType.MAGHRIB] ?: "18:15",
                units = listOf(
                    PrayerUnit("maghrib_fard_1", PrayerType.MAGHRIB, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("maghrib_fard_2", PrayerType.MAGHRIB, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("maghrib_fard_3", PrayerType.MAGHRIB, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("maghrib_sunnat_1", PrayerType.MAGHRIB, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("maghrib_sunnat_2", PrayerType.MAGHRIB, PrayerCategory.SUNNAT, 2, "Sunnat 2")
                )
            ),
            Prayer(
                type = PrayerType.ISHA,
                time = getPrayerTimes()[PrayerType.ISHA] ?: "20:00",
                units = listOf(
                    PrayerUnit("isha_sunnat_pre_1", PrayerType.ISHA, PrayerCategory.SUNNAT, 1, "Sunnat 1"),
                    PrayerUnit("isha_sunnat_pre_2", PrayerType.ISHA, PrayerCategory.SUNNAT, 2, "Sunnat 2"),
                    PrayerUnit("isha_sunnat_pre_3", PrayerType.ISHA, PrayerCategory.SUNNAT, 3, "Sunnat 3"),
                    PrayerUnit("isha_sunnat_pre_4", PrayerType.ISHA, PrayerCategory.SUNNAT, 4, "Sunnat 4"),
                    PrayerUnit("isha_fard_1", PrayerType.ISHA, PrayerCategory.FARD, 1, "Fard 1"),
                    PrayerUnit("isha_fard_2", PrayerType.ISHA, PrayerCategory.FARD, 2, "Fard 2"),
                    PrayerUnit("isha_fard_3", PrayerType.ISHA, PrayerCategory.FARD, 3, "Fard 3"),
                    PrayerUnit("isha_fard_4", PrayerType.ISHA, PrayerCategory.FARD, 4, "Fard 4"),
                    PrayerUnit("isha_sunnat_post_1", PrayerType.ISHA, PrayerCategory.SUNNAT, 1, "Sunnat Post 1"),
                    PrayerUnit("isha_sunnat_post_2", PrayerType.ISHA, PrayerCategory.SUNNAT, 2, "Sunnat Post 2"),
                    PrayerUnit("isha_witr_1", PrayerType.ISHA, PrayerCategory.WITR, 1, "Witr 1"),
                    PrayerUnit("isha_witr_2", PrayerType.ISHA, PrayerCategory.WITR, 2, "Witr 2"),
                    PrayerUnit("isha_witr_3", PrayerType.ISHA, PrayerCategory.WITR, 3, "Witr 3")
                )
            )
        )
    }
}
