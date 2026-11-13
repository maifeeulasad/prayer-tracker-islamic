package com.mua.prayertracker.data.entity

import com.mua.minidb.Entity
import com.mua.minidb.PrimaryKey

/**
 * One record per completed prayer unit (rakat) on a given day, stored in
 * minidb. A unit is completed if and only if a record exists for it.
 *
 * The two [PrimaryKey] properties form a composite key `listOf(date, unitId)`,
 * so re-putting the same completion is an idempotent overwrite.
 *
 * The unit ids are the stable identifiers defined in
 * [com.mua.prayertracker.domain.model.PrayerUnitCatalog] (e.g. "fajr_fard_1").
 */
@Entity(store = "prayer_unit_completions")
data class PrayerUnitCompletionEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    @PrimaryKey val unitId: String,
) {
    /** The composite key this record is stored under. */
    val key: List<String> get() = listOf(date, unitId)
}
