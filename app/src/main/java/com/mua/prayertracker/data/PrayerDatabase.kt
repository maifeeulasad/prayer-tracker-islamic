package com.mua.prayertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mua.prayertracker.data.dao.PrayerRecordDao
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity

/**
 * Room database for the Prayer Tracker app.
 * Contains the prayer_unit_completions table for storing daily prayer tracking data.
 */
@Database(
    entities = [PrayerUnitCompletionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class PrayerDatabase : RoomDatabase() {

    abstract fun prayerRecordDao(): PrayerRecordDao

    companion object {
        private const val DATABASE_NAME = "prayer_tracker_db"

        @Volatile
        private var INSTANCE: PrayerDatabase? = null

        fun getInstance(context: Context): PrayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrayerDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v1 stored one prayer_records row per day with one Boolean column per rakat.
         * v2 normalizes this to one row per completed unit, keyed by the stable unit
         * ids from PrayerUnitCatalog, so no user history is lost on upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prayer_unit_completions` (
                        `date` TEXT NOT NULL,
                        `unitId` TEXT NOT NULL,
                        PRIMARY KEY(`date`, `unitId`)
                    )
                    """.trimIndent()
                )
                LEGACY_COLUMN_TO_UNIT_ID.forEach { (column, unitId) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO prayer_unit_completions (date, unitId) " +
                            "SELECT date, '$unitId' FROM prayer_records WHERE $column = 1"
                    )
                }
                db.execSQL("DROP TABLE IF EXISTS prayer_records")
            }
        }

        private val LEGACY_COLUMN_TO_UNIT_ID = mapOf(
            "fajrSunnat1" to "fajr_sunnat_1",
            "fajrSunnat2" to "fajr_sunnat_2",
            "fajrFard1" to "fajr_fard_1",
            "fajrFard2" to "fajr_fard_2",
            "dhuhrSunnatPre1" to "dhuhr_sunnat_pre_1",
            "dhuhrSunnatPre2" to "dhuhr_sunnat_pre_2",
            "dhuhrSunnatPre3" to "dhuhr_sunnat_pre_3",
            "dhuhrSunnatPre4" to "dhuhr_sunnat_pre_4",
            "dhuhrFard1" to "dhuhr_fard_1",
            "dhuhrFard2" to "dhuhr_fard_2",
            "dhuhrFard3" to "dhuhr_fard_3",
            "dhuhrFard4" to "dhuhr_fard_4",
            "dhuhrSunnatPost1" to "dhuhr_sunnat_post_1",
            "dhuhrSunnatPost2" to "dhuhr_sunnat_post_2",
            "asrSunnat1" to "asr_sunnat_1",
            "asrSunnat2" to "asr_sunnat_2",
            "asrSunnat3" to "asr_sunnat_3",
            "asrSunnat4" to "asr_sunnat_4",
            "asrFard1" to "asr_fard_1",
            "asrFard2" to "asr_fard_2",
            "asrFard3" to "asr_fard_3",
            "asrFard4" to "asr_fard_4",
            "maghribFard1" to "maghrib_fard_1",
            "maghribFard2" to "maghrib_fard_2",
            "maghribFard3" to "maghrib_fard_3",
            "maghribSunnat1" to "maghrib_sunnat_1",
            "maghribSunnat2" to "maghrib_sunnat_2",
            "ishaSunnatPre1" to "isha_sunnat_pre_1",
            "ishaSunnatPre2" to "isha_sunnat_pre_2",
            "ishaSunnatPre3" to "isha_sunnat_pre_3",
            "ishaSunnatPre4" to "isha_sunnat_pre_4",
            "ishaFard1" to "isha_fard_1",
            "ishaFard2" to "isha_fard_2",
            "ishaFard3" to "isha_fard_3",
            "ishaFard4" to "isha_fard_4",
            "ishaSunnatPost1" to "isha_sunnat_post_1",
            "ishaSunnatPost2" to "isha_sunnat_post_2",
            "ishaWitr1" to "isha_witr_1",
            "ishaWitr2" to "isha_witr_2",
            "ishaWitr3" to "isha_witr_3"
        )
    }
}
