package com.mua.prayertracker.domain.repository

import com.mua.prayertracker.data.dao.PrayerRecordDao
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.domain.model.PrayerUnitCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing prayer completions.
 * Acts as a single source of truth for prayer tracking data.
 */
class PrayerRepository(
    private val prayerRecordDao: PrayerRecordDao
) {

    /**
     * Observe the set of completed unit ids for a specific day.
     */
    fun observeCompletedUnits(date: String): Flow<Set<String>> {
        return prayerRecordDao.getCompletedUnitIdsByDate(date).map { it.toSet() }
    }

    /**
     * Observe all completions for a month, grouped by date.
     * Days with no completions are absent from the map.
     */
    fun observeMonthCompletions(yearMonth: String): Flow<Map<String, Set<String>>> {
        return prayerRecordDao.getCompletionsByMonth(yearMonth).map { rows ->
            rows.groupBy(PrayerUnitCompletionEntity::date) { it.unitId }
                .mapValues { (_, unitIds) -> unitIds.toSet() }
        }
    }

    /**
     * Mark units as completed or not completed for a day.
     */
    suspend fun setUnitsCompleted(date: String, unitIds: List<String>, completed: Boolean) {
        if (unitIds.isEmpty()) return
        if (completed) {
            prayerRecordDao.insertCompletions(
                unitIds.map { PrayerUnitCompletionEntity(date = date, unitId = it) }
            )
        } else {
            prayerRecordDao.deleteCompletions(date, unitIds)
        }
    }

    /**
     * Determine the completion status of a day from its completed units.
     */
    fun getDayCompletionStatus(completedUnitIds: Set<String>): DayCompletionStatus {
        return when {
            completedUnitIds.containsAll(PrayerUnitCatalog.allFardUnitIds) ->
                DayCompletionStatus.COMPLETE

            completedUnitIds.isNotEmpty() -> DayCompletionStatus.PARTIAL

            else -> DayCompletionStatus.EMPTY
        }
    }
}
