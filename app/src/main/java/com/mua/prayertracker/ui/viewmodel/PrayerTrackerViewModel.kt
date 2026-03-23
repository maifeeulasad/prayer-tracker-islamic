package com.mua.prayertracker.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mua.prayertracker.data.PrayerDatabase
import com.mua.prayertracker.data.PrayerSettingsStorage
import com.mua.prayertracker.data.entity.PrayerRecordEntity
import com.mua.prayertracker.domain.PrayerTimeProvider
import com.mua.prayertracker.domain.model.CalendarDay
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerCalculationSettings
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.repository.PrayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for managing prayer tracking state and logic.
 */
class PrayerTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PrayerDatabase.getInstance(application)
    private val repository = PrayerRepository(database.prayerRecordDao())
    private val settingsStorage = PrayerSettingsStorage(application)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    private val _currentDate = MutableStateFlow(getTodayDateString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(getCurrentMonthString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _prayers = MutableStateFlow<List<Prayer>>(emptyList())
    val prayers: StateFlow<List<Prayer>> = _prayers.asStateFlow()

    private val _currentPrayerRecord = MutableStateFlow<PrayerRecordEntity?>(null)
    val currentPrayerRecord: StateFlow<PrayerRecordEntity?> = _currentPrayerRecord.asStateFlow()

    private val _calendarDays = MutableStateFlow<List<CalendarDay>>(emptyList())
    val calendarDays: StateFlow<List<CalendarDay>> = _calendarDays.asStateFlow()

    private val _nextPrayerInfo = MutableStateFlow<Pair<PrayerType?, String>>(Pair(null, ""))
    val nextPrayerInfo: StateFlow<Pair<PrayerType?, String>> = _nextPrayerInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _prayerSettings = MutableStateFlow(settingsStorage.load())
    val prayerSettings: StateFlow<PrayerCalculationSettings> = _prayerSettings.asStateFlow()

    init {
        loadPrayersWithPlaceholderTimes()
        loadCurrentDayRecord()
        updateNextPrayerInfo()
        loadCalendarForMonth(_selectedMonth.value)
    }

    /**
     * Load all prayers with their units.
     */
    private fun loadPrayersWithPlaceholderTimes() {
        val placeholderTimes = mapOf(
            PrayerType.FAJR to "--:--",
//            PrayerType.SUNRISE to "--:--",
            PrayerType.DHUHR to "--:--",
            PrayerType.ASR to "--:--",
            PrayerType.MAGHRIB to "--:--",
            PrayerType.ISHA to "--:--"
        )
        _prayers.value = PrayerTimeProvider.getPrayersWithUnits(placeholderTimes)
    }

    /**
     * Load or create today's prayer record.
     */
    private fun loadCurrentDayRecord() {
        viewModelScope.launch {
            _currentPrayerRecord.value =
                repository.getPrayerRecordByDate(_currentDate.value).first()
                    ?: repository.createEmptyRecord(_currentDate.value)
        }
    }

    /**
     * Observe prayer record changes for the current date.
     */
    fun observeCurrentDayRecord() {
        viewModelScope.launch {
            repository.getPrayerRecordByDate(_currentDate.value).collect { record ->
                _currentPrayerRecord.value =
                    record ?: repository.createEmptyRecord(_currentDate.value)
            }
        }
    }

    /**
     * Toggle a prayer unit's completion status.
     */
    fun togglePrayerUnit(unitId: String) {
        viewModelScope.launch {
            val currentRecord =
                _currentPrayerRecord.value ?: repository.createEmptyRecord(_currentDate.value)
            val updatedRecord = repository.togglePrayerUnit(currentRecord, unitId)
            repository.savePrayerRecord(updatedRecord)
            _currentPrayerRecord.value = updatedRecord

            // Refresh calendar to show updated status
            loadCalendarForMonth(_selectedMonth.value)
        }
    }

    /**
     * Toggle multiple prayer units at once (for grouped prayer toggles).
     * This toggles all units in the provided list, effectively marking
     * the entire group as completed or uncompleted.
     */
    fun togglePrayerUnits(unitIds: List<String>) {
        viewModelScope.launch {
            val currentRecord =
                _currentPrayerRecord.value ?: repository.createEmptyRecord(_currentDate.value)
            var updatedRecord = currentRecord

            // Toggle each unit in the list
            for (unitId in unitIds) {
                updatedRecord = repository.togglePrayerUnit(updatedRecord, unitId)
            }

            repository.savePrayerRecord(updatedRecord)
            _currentPrayerRecord.value = updatedRecord

            // Refresh calendar to show updated status
            loadCalendarForMonth(_selectedMonth.value)
        }
    }

    /**
     * Set the current date to track.
     */
    fun setCurrentDate(date: String) {
        _currentDate.value = date
        loadCurrentDayRecord()
        observeCurrentDayRecord()
    }

    /**
     * Navigate to a specific month.
     */
    fun setMonth(yearMonth: String) {
        _selectedMonth.value = yearMonth
        loadCalendarForMonth(yearMonth)
    }

    /**
     * Navigate to previous month.
     */
    fun previousMonth() {
        val cal = Calendar.getInstance()
        cal.time = monthFormat.parse(_selectedMonth.value + "-01") ?: Date()
        cal.add(Calendar.MONTH, -1)
        setMonth(monthFormat.format(cal.time))
    }

    /**
     * Navigate to next month.
     */
    fun nextMonth() {
        val cal = Calendar.getInstance()
        cal.time = monthFormat.parse(_selectedMonth.value + "-01") ?: Date()
        cal.add(Calendar.MONTH, 1)
        setMonth(monthFormat.format(cal.time))
    }

    /**
     * Load calendar days for a specific month.
     */
    private fun loadCalendarForMonth(yearMonth: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val cal = Calendar.getInstance()
            cal.time = monthFormat.parse(yearMonth + "-01") ?: Date()
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            val days = mutableListOf<CalendarDay>()
            val today = getTodayDateString()

            // Add empty days for padding
            repeat(firstDayOfWeek) {
                days.add(
                    CalendarDay(
                        date = "",
                        dayOfMonth = 0,
                        isCurrentMonth = false,
                        isToday = false,
                        completionStatus = DayCompletionStatus.EMPTY
                    )
                )
            }

            // Add days of the month
            for (day in 1..daysInMonth) {
                val dateString = String.format(Locale.getDefault(), "%s-%02d", yearMonth, day)
                val record = repository.getPrayerRecordByDate(dateString).first()
                val completionStatus = repository.getDayCompletionStatus(record)

                days.add(
                    CalendarDay(
                        date = dateString,
                        dayOfMonth = day,
                        isCurrentMonth = true,
                        isToday = dateString == today,
                        completionStatus = completionStatus
                    )
                )
            }

            _calendarDays.value = days
            _isLoading.value = false
        }
    }

    /**
     * Update information about the next prayer.
     */
    private fun updateNextPrayerInfo() {
        updateNextPrayerInfo(currentPrayerTimes())
    }

    private fun updateNextPrayerInfo(prayerTimes: Map<PrayerType, String>) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val nextPrayer = PrayerTimeProvider.getNextPrayer(currentHour, currentMinute, prayerTimes)
        val timeRemaining = PrayerTimeProvider.getTimeUntilNextPrayer(
            currentHour,
            currentMinute,
            prayerTimes
        )

        _nextPrayerInfo.value = Pair(nextPrayer, timeRemaining)
    }

    /**
     * Refresh the next prayer info.
     */
    fun refreshNextPrayerInfo() {
        updateNextPrayerInfo()
    }

    @SuppressLint("MissingPermission")
    fun loadPrayerTimesFromLocation() {
        viewModelScope.launch {
            when (
                val result = PrayerTimeProvider.getPrayerTimes(
                    context = getApplication(),
                    config = _prayerSettings.value.toCalculationConfig()
                )
            ) {
                is PrayerTimeProvider.PrayerTimesResult.Success -> {
                    _prayers.value = PrayerTimeProvider.getPrayersWithUnits(result.times)
                    updateNextPrayerInfo(result.times)
                }

                PrayerTimeProvider.PrayerTimesResult.PermissionDenied -> {
                    _hasLocationPermission.value = false
                }

                PrayerTimeProvider.PrayerTimesResult.LocationUnavailable -> {
                    // todo: render issue for location
                }

                is PrayerTimeProvider.PrayerTimesResult.PolarAnomalyError -> {
                    // todo: render warning
                }

                is PrayerTimeProvider.PrayerTimesResult.Error -> {
                    // todo: render error with irritating popover
                }
            }
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _hasLocationPermission.value = granted
        if (granted) {
            loadPrayerTimesFromLocation()
        }
    }

    fun updateCalculationMethod(method: PrayerTimeProvider.CalculationMethod) {
        updatePrayerSettings { it.copy(method = method) }
    }

    fun updateMadhab(madhab: PrayerTimeProvider.Madhab) {
        updatePrayerSettings { it.copy(madhab = madhab) }
    }

    fun updateHighLatitudeRule(rule: PrayerTimeProvider.HighLatitudeRule?) {
        updatePrayerSettings { it.copy(highLatitudeRule = rule) }
    }

    fun updateElevationMeters(elevationMeters: Double) {
        updatePrayerSettings { it.copy(elevationMeters = elevationMeters.coerceAtLeast(0.0)) }
    }

    fun updateOffsetMinutes(prayerType: PrayerType, minutes: Int) {
        val clampedMinutes = minutes.coerceIn(-90, 90)
        updatePrayerSettings { settings ->
            when (prayerType) {
                PrayerType.FAJR -> settings.copy(fajrOffsetMinutes = clampedMinutes)
                PrayerType.DHUHR -> settings.copy(dhuhrOffsetMinutes = clampedMinutes)
                PrayerType.ASR -> settings.copy(asrOffsetMinutes = clampedMinutes)
                PrayerType.MAGHRIB -> settings.copy(maghribOffsetMinutes = clampedMinutes)
                PrayerType.ISHA -> settings.copy(ishaOffsetMinutes = clampedMinutes)
            }
        }
    }

    private fun updatePrayerSettings(transform: (PrayerCalculationSettings) -> PrayerCalculationSettings) {
        val updated = transform(_prayerSettings.value)
        _prayerSettings.value = updated
        settingsStorage.save(updated)

        if (_hasLocationPermission.value) {
            loadPrayerTimesFromLocation()
        }
    }

    private fun currentPrayerTimes(): Map<PrayerType, String> {
        return _prayers.value.associate { prayer ->
            prayer.type to prayer.time
        }
    }

    private fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    private fun getCurrentMonthString(): String {
        return monthFormat.format(Date())
    }
}
