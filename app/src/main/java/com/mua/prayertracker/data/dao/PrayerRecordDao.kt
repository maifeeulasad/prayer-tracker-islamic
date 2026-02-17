package com.mua.prayertracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mua.prayertracker.data.entity.PrayerRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for prayer records.
 * Provides methods to interact with the prayer_records table.
 */
@Dao
interface PrayerRecordDao {

    /**
     * Get a specific prayer record by date.
     * Returns Flow to observe changes in real-time.
     */
    @Query("SELECT * FROM prayer_records WHERE date = :date")
    fun getPrayerRecordByDate(date: String): Flow<PrayerRecordEntity?>

    /**
     * Get all prayer records for a specific month.
     * @param yearMonth Format: "YYYY-MM" to filter by month
     */
    @Query("SELECT * FROM prayer_records WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    fun getPrayerRecordsByMonth(yearMonth: String): Flow<List<PrayerRecordEntity>>

    /**
     * Insert a new prayer record or replace existing one.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrayerRecord(record: PrayerRecordEntity)

    /**
     * Update an existing prayer record.
     */
    @Update
    suspend fun updatePrayerRecord(record: PrayerRecordEntity)

    /**
     * Delete a prayer record by date.
     */
    @Query("DELETE FROM prayer_records WHERE date = :date")
    suspend fun deletePrayerRecord(date: String)

    /**
     * Get count of completed fard prayers for a month.
     * Used for statistics.
     */
    @Query("""
        SELECT COUNT(*) FROM prayer_records
        WHERE date LIKE :yearMonth || '%'
        AND fajrFard1 = 1 AND fajrFard2 = 1
        AND dhuhrFard1 = 1 AND dhuhrFard2 = 1 AND dhuhrFard3 = 1 AND dhuhrFard4 = 1
        AND asrFard1 = 1 AND asrFard2 = 1 AND asrFard3 = 1 AND asrFard4 = 1
        AND maghribFard1 = 1 AND maghribFard2 = 1 AND maghribFard3 = 1
        AND ishaFard1 = 1 AND ishaFard2 = 1 AND ishaFard3 = 1 AND ishaFard4 = 1
    """)
    fun getCompleteFardDaysCount(yearMonth: String): Flow<Int>

    /**
     * Get all prayer records for statistics.
     */
    @Query("SELECT * FROM prayer_records ORDER BY date DESC")
    fun getAllPrayerRecords(): Flow<List<PrayerRecordEntity>>
}
