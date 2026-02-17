package com.mua.prayertracker.domain.repository

import com.mua.prayertracker.data.dao.PrayerRecordDao
import com.mua.prayertracker.data.entity.PrayerRecordEntity
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.domain.model.PrayerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing prayer records.
 * Acts as a single source of truth for prayer tracking data.
 */
class PrayerRepository(
    private val prayerRecordDao: PrayerRecordDao
) {

    /**
     * Get a specific day's prayer record.
     */
    fun getPrayerRecordByDate(date: String): Flow<PrayerRecordEntity?> {
        return prayerRecordDao.getPrayerRecordByDate(date)
    }

    /**
     * Get all prayer records for a specific month.
     */
    fun getPrayerRecordsByMonth(yearMonth: String): Flow<List<PrayerRecordEntity>> {
        return prayerRecordDao.getPrayerRecordsByMonth(yearMonth)
    }

    /**
     * Get all prayer records.
     */
    fun getAllPrayerRecords(): Flow<List<PrayerRecordEntity>> {
        return prayerRecordDao.getAllPrayerRecords()
    }

    /**
     * Save or update a prayer record.
     */
    suspend fun savePrayerRecord(record: PrayerRecordEntity) {
        prayerRecordDao.insertPrayerRecord(record)
    }

    /**
     * Delete a prayer record by date.
     */
    suspend fun deletePrayerRecord(date: String) {
        prayerRecordDao.deletePrayerRecord(date)
    }

    /**
     * Get count of days with all Fard prayers completed for a month.
     */
    fun getCompleteFardDaysCount(yearMonth: String): Flow<Int> {
        return prayerRecordDao.getCompleteFardDaysCount(yearMonth)
    }

    /**
     * Create an empty prayer record for a given date.
     */
    fun createEmptyRecord(date: String): PrayerRecordEntity {
        return PrayerRecordEntity(date = date)
    }

    /**
     * Determine the completion status of a day based on the record.
     */
    fun getDayCompletionStatus(record: PrayerRecordEntity?): DayCompletionStatus {
        if (record == null) return DayCompletionStatus.EMPTY

        val fajrFard = record.fajrFard1 && record.fajrFard2
        val dhuhrFard = record.dhuhrFard1 && record.dhuhrFard2 && record.dhuhrFard3 && record.dhuhrFard4
        val asrFard = record.asrFard1 && record.asrFard2 && record.asrFard3 && record.asrFard4
        val maghribFard = record.maghribFard1 && record.maghribFard2 && record.maghribFard3
        val ishaFard = record.ishaFard1 && record.ishaFard2 && record.ishaFard3 && record.ishaFard4

        val allFard = fajrFard && dhuhrFard && asrFard && maghribFard && ishaFard
        val anyCompleted = fajrFard || dhuhrFard || asrFard || maghribFard || ishaFard

        return when {
            allFard -> DayCompletionStatus.COMPLETE
            anyCompleted -> DayCompletionStatus.PARTIAL
            else -> DayCompletionStatus.EMPTY
        }
    }

    /**
     * Check if all Fard prayers are completed for a record.
     */
    fun areAllFardCompleted(record: PrayerRecordEntity?): Boolean {
        if (record == null) return false

        return record.fajrFard1 && record.fajrFard2 &&
                record.dhuhrFard1 && record.dhuhrFard2 && record.dhuhrFard3 && record.dhuhrFard4 &&
                record.asrFard1 && record.asrFard2 && record.asrFard3 && record.asrFard4 &&
                record.maghribFard1 && record.maghribFard2 && record.maghribFard3 &&
                record.ishaFard1 && record.ishaFard2 && record.ishaFard3 && record.ishaFard4
    }

    /**
     * Toggle a specific prayer unit in the record.
     */
    fun togglePrayerUnit(record: PrayerRecordEntity, unitId: String): PrayerRecordEntity {
        return when (unitId) {
            // Fajr
            "fajr_sunnat_1" -> record.copy(fajrSunnat1 = !record.fajrSunnat1)
            "fajr_sunnat_2" -> record.copy(fajrSunnat2 = !record.fajrSunnat2)
            "fajr_fard_1" -> record.copy(fajrFard1 = !record.fajrFard1)
            "fajr_fard_2" -> record.copy(fajrFard2 = !record.fajrFard2)

            // Dhuhr
            "dhuhr_sunnat_pre_1" -> record.copy(dhuhrSunnatPre1 = !record.dhuhrSunnatPre1)
            "dhuhr_sunnat_pre_2" -> record.copy(dhuhrSunnatPre2 = !record.dhuhrSunnatPre2)
            "dhuhr_sunnat_pre_3" -> record.copy(dhuhrSunnatPre3 = !record.dhuhrSunnatPre3)
            "dhuhr_sunnat_pre_4" -> record.copy(dhuhrSunnatPre4 = !record.dhuhrSunnatPre4)
            "dhuhr_fard_1" -> record.copy(dhuhrFard1 = !record.dhuhrFard1)
            "dhuhr_fard_2" -> record.copy(dhuhrFard2 = !record.dhuhrFard2)
            "dhuhr_fard_3" -> record.copy(dhuhrFard3 = !record.dhuhrFard3)
            "dhuhr_fard_4" -> record.copy(dhuhrFard4 = !record.dhuhrFard4)
            "dhuhr_sunnat_post_1" -> record.copy(dhuhrSunnatPost1 = !record.dhuhrSunnatPost1)
            "dhuhr_sunnat_post_2" -> record.copy(dhuhrSunnatPost2 = !record.dhuhrSunnatPost2)

            // Asr
            "asr_sunnat_1" -> record.copy(asrSunnat1 = !record.asrSunnat1)
            "asr_sunnat_2" -> record.copy(asrSunnat2 = !record.asrSunnat2)
            "asr_sunnat_3" -> record.copy(asrSunnat3 = !record.asrSunnat3)
            "asr_sunnat_4" -> record.copy(asrSunnat4 = !record.asrSunnat4)
            "asr_fard_1" -> record.copy(asrFard1 = !record.asrFard1)
            "asr_fard_2" -> record.copy(asrFard2 = !record.asrFard2)
            "asr_fard_3" -> record.copy(asrFard3 = !record.asrFard3)
            "asr_fard_4" -> record.copy(asrFard4 = !record.asrFard4)

            // Maghrib
            "maghrib_fard_1" -> record.copy(maghribFard1 = !record.maghribFard1)
            "maghrib_fard_2" -> record.copy(maghribFard2 = !record.maghribFard2)
            "maghrib_fard_3" -> record.copy(maghribFard3 = !record.maghribFard3)
            "maghrib_sunnat_1" -> record.copy(maghribSunnat1 = !record.maghribSunnat1)
            "maghrib_sunnat_2" -> record.copy(maghribSunnat2 = !record.maghribSunnat2)

            // Isha
            "isha_sunnat_pre_1" -> record.copy(ishaSunnatPre1 = !record.ishaSunnatPre1)
            "isha_sunnat_pre_2" -> record.copy(ishaSunnatPre2 = !record.ishaSunnatPre2)
            "isha_sunnat_pre_3" -> record.copy(ishaSunnatPre3 = !record.ishaSunnatPre3)
            "isha_sunnat_pre_4" -> record.copy(ishaSunnatPre4 = !record.ishaSunnatPre4)
            "isha_fard_1" -> record.copy(ishaFard1 = !record.ishaFard1)
            "isha_fard_2" -> record.copy(ishaFard2 = !record.ishaFard2)
            "isha_fard_3" -> record.copy(ishaFard3 = !record.ishaFard3)
            "isha_fard_4" -> record.copy(ishaFard4 = !record.ishaFard4)
            "isha_sunnat_post_1" -> record.copy(ishaSunnatPost1 = !record.ishaSunnatPost1)
            "isha_sunnat_post_2" -> record.copy(ishaSunnatPost2 = !record.ishaSunnatPost2)
            "isha_witr_1" -> record.copy(ishaWitr1 = !record.ishaWitr1)
            "isha_witr_2" -> record.copy(ishaWitr2 = !record.ishaWitr2)
            "isha_witr_3" -> record.copy(ishaWitr3 = !record.ishaWitr3)

            else -> record
        }
    }
}
