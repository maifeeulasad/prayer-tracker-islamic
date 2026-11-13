package com.mua.prayertracker.data

import android.content.Context
import com.mua.minidb.MiniDB
import com.mua.minidb.MiniDatabase
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity
import com.mua.prayertracker.data.migration.RoomToMiniDbMigration

/**
 * minidb database for the Prayer Tracker app.
 * Contains the prayer_unit_completions store for daily prayer tracking data.
 *
 * Opening is synchronous disk I/O — always call [getInstance] off the main
 * thread (the repository only touches it from Dispatchers.IO).
 */
object MiniPrayerDatabase {

    private const val DATABASE_NAME = "prayer_tracker"

    @Volatile
    private var INSTANCE: MiniDatabase? = null

    fun getInstance(context: Context): MiniDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val appContext = context.applicationContext
                val database = MiniDB.open(
                    appContext,
                    DATABASE_NAME,
                    listOf(PrayerUnitCompletionEntity::class)
                )

                // One-time import of data left behind by the old Room
                // database; runs before any read goes through this instance.
                RoomToMiniDbMigration.migrateIfNeeded(appContext, database)

                INSTANCE = database
                database
            }
        }
    }
}
