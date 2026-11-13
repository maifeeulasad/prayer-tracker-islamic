package com.mua.prayertracker.domain.repository

import android.content.Context
import com.mua.minidb.EntityStore
import com.mua.minidb.entityStore
import com.mua.prayertracker.data.MiniPrayerDatabase
import com.mua.prayertracker.data.entity.PrayerUnitCompletionEntity
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.domain.model.PrayerUnitCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing prayer completions, backed by minidb.
 * Acts as a single source of truth for prayer tracking data.
 *
 * minidb is synchronous and has no change notification of its own, so this
 * repository provides the reactivity: every write emits on [changes], and
 * the observe* flows re-read on each emission. All disk access runs on
 * Dispatchers.IO (opening the database included, via lazy [store]).
 */
class PrayerRepository(context: Context) {

    private val appContext = context.applicationContext

    private val store: EntityStore<PrayerUnitCompletionEntity> by lazy {
        MiniPrayerDatabase.getInstance(appContext).entityStore<PrayerUnitCompletionEntity>()
    }

    // replay = 1 with an initial emission so every new collector computes
    // its first value immediately; DROP_OLDEST keeps writers non-blocking.
    private val changes = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(Unit) }

    /**
     * Observe the set of completed unit ids for a specific day.
     */
    fun observeCompletedUnits(date: String): Flow<Set<String>> = changes
        .map { readCompletedUnits(date) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * Observe all completions for a month, grouped by date.
     * Days with no completions are absent from the map.
     */
    fun observeMonthCompletions(yearMonth: String): Flow<Map<String, Set<String>>> = changes
        .map { readMonthCompletions(yearMonth) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * Mark units as completed or not completed for a day.
     */
    suspend fun setUnitsCompleted(date: String, unitIds: List<String>, completed: Boolean) {
        if (unitIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            if (completed) {
                store.putAll(
                    unitIds.map { PrayerUnitCompletionEntity(date = date, unitId = it) }
                )
            } else {
                store.deleteMany(unitIds.map { listOf(date, it) })
            }
        }
        changes.emit(Unit)
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

    private fun readCompletedUnits(date: String): Set<String> =
        store.query()
            .where("date").eq(date)
            .toList()
            .map { it.unitId }
            .toSet()

    private fun readMonthCompletions(yearMonth: String): Map<String, Set<String>> =
        store.query()
            .where("date").startsWith("$yearMonth-")
            .toList()
            .groupBy(PrayerUnitCompletionEntity::date) { it.unitId }
            .mapValues { (_, unitIds) -> unitIds.toSet() }
}
