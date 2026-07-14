package com.mua.prayertracker.domain.model

/**
 * A group of rakats that is tracked with a single toggle (e.g. "4 Rakat Fard").
 */
data class PrayerUnitGroup(
    val label: String,
    val category: PrayerCategory,
    val unitIds: List<String>
) {
    fun isCompleted(completedUnitIds: Set<String>): Boolean =
        unitIds.all { it in completedUnitIds }
}

/**
 * Single source of truth for the trackable prayer units and their stable ids.
 * The unit ids are persisted in the database, so they must never change.
 */
object PrayerUnitCatalog {

    private fun group(
        label: String,
        category: PrayerCategory,
        idPrefix: String,
        rakatCount: Int
    ) = PrayerUnitGroup(label, category, (1..rakatCount).map { "${idPrefix}_$it" })

    val groupsByPrayer: Map<PrayerType, List<PrayerUnitGroup>> = mapOf(
        PrayerType.FAJR to listOf(
            group("2 Rakat Sunnat", PrayerCategory.SUNNAT, "fajr_sunnat", 2),
            group("2 Rakat Fard", PrayerCategory.FARD, "fajr_fard", 2)
        ),
        PrayerType.DHUHR to listOf(
            group("4 Rakat Sunnat", PrayerCategory.SUNNAT, "dhuhr_sunnat_pre", 4),
            group("4 Rakat Fard", PrayerCategory.FARD, "dhuhr_fard", 4),
            group("2 Rakat Sunnat", PrayerCategory.SUNNAT, "dhuhr_sunnat_post", 2)
        ),
        PrayerType.ASR to listOf(
            group("4 Rakat Sunnat", PrayerCategory.SUNNAT, "asr_sunnat", 4),
            group("4 Rakat Fard", PrayerCategory.FARD, "asr_fard", 4)
        ),
        PrayerType.MAGHRIB to listOf(
            group("3 Rakat Fard", PrayerCategory.FARD, "maghrib_fard", 3),
            group("2 Rakat Sunnat", PrayerCategory.SUNNAT, "maghrib_sunnat", 2)
        ),
        PrayerType.ISHA to listOf(
            group("4 Rakat Sunnat", PrayerCategory.SUNNAT, "isha_sunnat_pre", 4),
            group("4 Rakat Fard", PrayerCategory.FARD, "isha_fard", 4),
            group("2 Rakat Sunnat", PrayerCategory.SUNNAT, "isha_sunnat_post", 2),
            group("3 Rakat Witr", PrayerCategory.WITR, "isha_witr", 3)
        )
    )

    fun groupsFor(prayerType: PrayerType): List<PrayerUnitGroup> =
        groupsByPrayer.getValue(prayerType)

    /** Every fard unit id across all five prayers; a day is COMPLETE when all are done. */
    val allFardUnitIds: Set<String> = groupsByPrayer.values
        .flatten()
        .filter { it.category == PrayerCategory.FARD }
        .flatMap { it.unitIds }
        .toSet()
}
