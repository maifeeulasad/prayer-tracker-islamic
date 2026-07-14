package com.mua.prayertracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for prayer unit completions.
 */
@Dao
interface PrayerRecordDao {

    /**
     * Observe the ids of all completed units for a specific day.
     */
    @Query("SELECT unitId FROM prayer_unit_completions WHERE date = :date")
    fun getCompletedUnitIdsByDate(date: String): Flow<List<String>>

    /**
     * Observe all completions for a month.
     * @param yearMonth Format: "YYYY-MM"
     */
    @Query("SELECT * FROM prayer_unit_completions WHERE date LIKE :yearMonth || '-%'")
    fun getCompletionsByMonth(yearMonth: String): Flow<List<PrayerUnitCompletionEntity>>

    /**
     * Mark units as completed. Already-completed units are left untouched.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(completions: List<PrayerUnitCompletionEntity>)

    /**
     * Mark units as not completed for a day.
     */
    @Query("DELETE FROM prayer_unit_completions WHERE date = :date AND unitId IN (:unitIds)")
    suspend fun deleteCompletions(date: String, unitIds: List<String>)
}
