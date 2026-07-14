package com.mua.prayertracker.data.entity

import androidx.room.Entity

/**
 * One row per completed prayer unit (rakat) on a given day.
 * A unit is completed if and only if a row exists for it.
 *
 * The unit ids are the stable identifiers defined in
 * [com.mua.prayertracker.domain.model.PrayerUnitCatalog] (e.g. "fajr_fard_1").
 */
@Entity(
    tableName = "prayer_unit_completions",
    primaryKeys = ["date", "unitId"]
)
data class PrayerUnitCompletionEntity(
    val date: String, // Format: YYYY-MM-DD
    val unitId: String
)
