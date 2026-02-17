package com.mua.prayertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mua.prayertracker.data.dao.PrayerRecordDao
import com.mua.prayertracker.data.entity.PrayerRecordEntity

/**
 * Room database for the Prayer Tracker app.
 * Contains the prayer_records table for storing daily prayer tracking data.
 */
@Database(
    entities = [PrayerRecordEntity::class],
    version = 1,
    exportSchema = false
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
