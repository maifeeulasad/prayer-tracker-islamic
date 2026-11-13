package com.mua.prayertracker.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.mua.minidb.MiniDatabase
import com.mua.minidb.entityStore
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity
import java.io.File

/**
 * One-time migration of prayer history from the old Room (SQLite) database
 * into minidb, with every step in one dedicated place:
 *
 *  Step 1 - Detect: is the legacy SQLite database file still on disk?
 *           If not, there is nothing to migrate (fresh install, or the
 *           migration already ran and cleaned up after itself).
 *  Step 2 - Read: open the legacy database read-only with raw SQLite (no
 *           Room dependency needed) and extract the completions. BOTH
 *           historical schemas are understood:
 *             - v2: the normalized `prayer_unit_completions` table
 *               (one row per completed unit), copied as-is;
 *             - v1: the original `prayer_records` table (one row per day
 *               with one Boolean column per rakat), converted through the
 *               column-to-unit-id map below.
 *  Step 3 - Import: put every completion into minidb in ONE transaction
 *           (all or nothing). Re-putting an existing composite key is an
 *           idempotent overwrite, so a retry can never duplicate data.
 *  Step 4 - Clean up: delete the legacy SQLite database (including its
 *           -wal/-shm sidecars) so the migration never runs again.
 *
 * Failure handling: any error aborts BEFORE the cleanup step, nothing is
 * deleted, and the migration retries on the next app launch. The app keeps
 * working with whatever is already in minidb in the meantime.
 */
object RoomToMiniDbMigration {

    private const val TAG = "RoomToMiniDbMigration"

    private const val LEGACY_DATABASE_NAME = "prayer_tracker_db"
    private const val NORMALIZED_TABLE = "prayer_unit_completions"
    private const val WIDE_TABLE = "prayer_records"

    /**
     * Runs the migration when the legacy database exists.
     * @return true when legacy data was imported and the old database removed.
     */
    fun migrateIfNeeded(context: Context, database: MiniDatabase): Boolean {
        // Step 1: detect the legacy database.
        val legacyFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
        if (!legacyFile.exists()) {
            return false
        }

        return try {
            // Step 2: read the legacy completions.
            val completions = readLegacyCompletions(legacyFile)
            Log.i(TAG, "Importing ${completions.size} completions from the Room database")

            // Step 3: import into minidb, all or nothing.
            if (completions.isNotEmpty()) {
                database.entityStore<PrayerUnitCompletionEntity>().putAll(completions)
            }

            // Step 4: remove the legacy database so this never reruns.
            context.deleteDatabase(LEGACY_DATABASE_NAME)
            Log.i(TAG, "Migration complete; legacy database removed")
            true
        } catch (failure: Exception) {
            // Leave the legacy database untouched so the next launch retries.
            Log.e(TAG, "Migration failed; legacy database kept for retry", failure)
            false
        }
    }

    private fun readLegacyCompletions(legacyFile: File): List<PrayerUnitCompletionEntity> {
        SQLiteDatabase.openDatabase(
            legacyFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            return when {
                tableExists(db, NORMALIZED_TABLE) -> readNormalizedTable(db)
                tableExists(db, WIDE_TABLE) -> readWideTable(db)
                else -> {
                    Log.w(TAG, "Legacy database has no known tables; nothing to import")
                    emptyList()
                }
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * v2 schema: one (date, unitId) row per completed unit. Rows with
     * blank values or unit ids no longer in use are skipped.
     */
    private fun readNormalizedTable(db: SQLiteDatabase): List<PrayerUnitCompletionEntity> {
        val completions = mutableListOf<PrayerUnitCompletionEntity>()
        db.rawQuery("SELECT date, unitId FROM $NORMALIZED_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getString(0).orEmpty()
                val unitId = cursor.getString(1).orEmpty()
                if (date.isNotBlank() && unitId in KNOWN_UNIT_IDS) {
                    completions.add(PrayerUnitCompletionEntity(date = date, unitId = unitId))
                }
            }
        }
        return completions
    }

    /**
     * v1 schema: one row per day with one Boolean column per rakat,
     * converted through [WIDE_COLUMN_TO_UNIT_ID]. Columns absent from the
     * row (older partial schemas) are simply skipped.
     */
    private fun readWideTable(db: SQLiteDatabase): List<PrayerUnitCompletionEntity> {
        val completions = mutableListOf<PrayerUnitCompletionEntity>()
        db.rawQuery("SELECT * FROM $WIDE_TABLE", null).use { cursor ->
            val dateIndex = cursor.getColumnIndex("date")
            if (dateIndex < 0) return emptyList()

            while (cursor.moveToNext()) {
                val date = cursor.getString(dateIndex).orEmpty()
                if (date.isBlank()) continue

                for ((column, unitId) in WIDE_COLUMN_TO_UNIT_ID) {
                    val columnIndex = cursor.getColumnIndex(column)
                    if (columnIndex >= 0 && cursor.getInt(columnIndex) == 1) {
                        completions.add(
                            PrayerUnitCompletionEntity(date = date, unitId = unitId)
                        )
                    }
                }
            }
        }
        return completions
    }

    private val WIDE_COLUMN_TO_UNIT_ID = mapOf(
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

    private val KNOWN_UNIT_IDS: Set<String> = WIDE_COLUMN_TO_UNIT_ID.values.toSet()
}
