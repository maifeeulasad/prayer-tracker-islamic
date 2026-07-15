package com.mua.prayertracker.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mua.prayertracker.data.PrayerDatabase
import com.mua.prayertracker.data.PrayerSettingsStorage
import com.mua.prayertracker.domain.PrayerTimeProvider
import com.mua.prayertracker.domain.location.GeoPoint
import com.mua.prayertracker.domain.location.ManualLocationStorage
import com.mua.prayertracker.domain.location.ResolvedLocation
import com.mua.prayertracker.domain.location.distanceBetweenMeters
import com.mua.prayertracker.domain.location.resolveLocation
import com.mua.prayertracker.domain.model.CalendarDay
import com.mua.prayertracker.domain.model.DayCompletionStatus
import com.mua.prayertracker.domain.model.ForbiddenTime
import com.mua.prayertracker.domain.model.Prayer
import com.mua.prayertracker.domain.model.PrayerCalculationSettings
import com.mua.prayertracker.domain.model.PrayerTimeRange
import com.mua.prayertracker.domain.model.PrayerType
import com.mua.prayertracker.domain.repository.PrayerRepository
import com.mua.prayertracker.util.PrayerNotificationScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for managing prayer tracking state and logic.
 */
class PrayerTrackerViewModel(application: Application) : AndroidViewModel(application) {
    // Import notification scheduler
    private val notificationScheduler = PrayerNotificationScheduler

    private val database = PrayerDatabase.getInstance(application)
    private val repository = PrayerRepository(database.prayerRecordDao())
    private val settingsStorage = PrayerSettingsStorage(application)

    // Locale.US keeps stored date keys in ASCII digits regardless of the device locale.
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    private val _currentDate = MutableStateFlow(getTodayDateString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _selectedMonth = MutableStateFlow(getCurrentMonthString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _prayers = MutableStateFlow<List<Prayer>>(emptyList())
    val prayers: StateFlow<List<Prayer>> = _prayers.asStateFlow()

    private val _todayCompletedUnits = MutableStateFlow<Set<String>>(emptySet())
    val todayCompletedUnits: StateFlow<Set<String>> = _todayCompletedUnits.asStateFlow()

    private val _calendarDays = MutableStateFlow<List<CalendarDay>>(emptyList())
    val calendarDays: StateFlow<List<CalendarDay>> = _calendarDays.asStateFlow()

    private val _nextPrayerInfo = MutableStateFlow<Pair<PrayerType?, String>>(Pair(null, ""))
    val nextPrayerInfo: StateFlow<Pair<PrayerType?, String>> = _nextPrayerInfo.asStateFlow()

    private val _prayerRanges = MutableStateFlow<Map<PrayerType, PrayerTimeRange>>(emptyMap())
    val prayerRanges: StateFlow<Map<PrayerType, PrayerTimeRange>> = _prayerRanges.asStateFlow()

    private val _forbiddenTimes = MutableStateFlow<List<ForbiddenTime>>(emptyList())
    val forbiddenTimes: StateFlow<List<ForbiddenTime>> = _forbiddenTimes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _prayerSettings = MutableStateFlow(settingsStorage.load())
    val prayerSettings: StateFlow<PrayerCalculationSettings> = _prayerSettings.asStateFlow()

    private val manualLocationStorage = ManualLocationStorage(application)

    private val _manualLocation = MutableStateFlow(manualLocationStorage.load())
    val manualLocation: StateFlow<GeoPoint?> = _manualLocation.asStateFlow()

    private val _deviceLocation = MutableStateFlow<GeoPoint?>(null)
    val deviceLocation: StateFlow<GeoPoint?> = _deviceLocation.asStateFlow()

    /** The location in effect (manual wins over device), tagged with its source. */
    val resolvedLocation: StateFlow<ResolvedLocation?> =
        combine(_manualLocation, _deviceLocation) { manual, device ->
            resolveLocation(manual, device)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            resolveLocation(_manualLocation.value, null)
        )

    /** Distance between the manually set location and the device location, if both known. */
    val manualDistanceMeters: StateFlow<Double?> =
        combine(_manualLocation, _deviceLocation) { manual, device ->
            distanceBetweenMeters(manual, device)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadPrayersWithPlaceholderTimes()
        loadPrayerTimesFromLocation()
        updateNextPrayerInfo()
        observeTodayCompletedUnits()
        observeCalendar()
    }

    /**
     * Keep today's completed units in sync with the database.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayCompletedUnits() {
        viewModelScope.launch {
            _currentDate
                .flatMapLatest { date -> repository.observeCompletedUnits(date) }
                .collect { completedUnits -> _todayCompletedUnits.value = completedUnits }
        }
    }

    /**
     * Keep the calendar in sync with the selected month and the database.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCalendar() {
        viewModelScope.launch {
            _selectedMonth
                .flatMapLatest { month ->
                    repository.observeMonthCompletions(month).map { month to it }
                }
                .collect { (month, completionsByDate) ->
                    _calendarDays.value = buildCalendarDays(month, completionsByDate)
                    _isLoading.value = false
                }
        }
    }

    /**
     * Toggle a group of prayer units for today.
     * If every unit in the group is completed, the group is cleared;
     * otherwise all units in the group are marked completed.
     */
    fun togglePrayerUnits(unitIds: List<String>) {
        viewModelScope.launch {
            val completed = _todayCompletedUnits.value
            val shouldComplete = !unitIds.all { it in completed }
            repository.setUnitsCompleted(_currentDate.value, unitIds, shouldComplete)
        }
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
     * Navigate to a specific month.
     */
    fun setMonth(yearMonth: String) {
        _isLoading.value = true
        _selectedMonth.value = yearMonth
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
     * Build calendar days for a month from its completions.
     */
    private fun buildCalendarDays(
        yearMonth: String,
        completionsByDate: Map<String, Set<String>>
    ): List<CalendarDay> {
        val cal = Calendar.getInstance()
        cal.time = monthFormat.parse(yearMonth) ?: Date()
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
            val dateString = String.format(Locale.US, "%s-%02d", yearMonth, day)
            val completedUnits = completionsByDate[dateString].orEmpty()

            days.add(
                CalendarDay(
                    date = dateString,
                    dayOfMonth = day,
                    isCurrentMonth = true,
                    isToday = dateString == today,
                    completionStatus = repository.getDayCompletionStatus(completedUnits)
                )
            )
        }

        return days
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

        val nextPrayer =
            PrayerTimeProvider.getNextPrayer(currentHour, currentMinute, prayerTimes)
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
            val manualLocation = _manualLocation.value

            // Always refresh the device position so the UI can show it and the
            // distance from a manually set location. Null when permission is
            // missing or the device location is unavailable.
            _deviceLocation.value = PrayerTimeProvider.getDeviceLocation(getApplication())

            when (
                val result = PrayerTimeProvider.getPrayerTimes(
                    context = getApplication(),
                    config = _prayerSettings.value.toCalculationConfig(),
                    overrideLocation = manualLocation
                )
            ) {
                is PrayerTimeProvider.PrayerTimesResult.Success -> {
                    _prayers.value = PrayerTimeProvider.getPrayersWithUnits(result.times)
                    updateNextPrayerInfo(result.times)

                    // Schedule notifications for today's prayers
                    notificationScheduler.schedulePrayerNotifications(
                        getApplication(),
                        result.times
                    )

                    when (
                        val scheduleResult = PrayerTimeProvider.getCompletePrayerSchedule(
                            context = getApplication(),
                            config = _prayerSettings.value.toCalculationConfig(),
                            overrideLocation = manualLocation
                        )
                    ) {
                        is PrayerTimeProvider.CompleteScheduleResult.Success -> {
                            _prayerRanges.value = scheduleResult.schedule.prayerRanges
                            _forbiddenTimes.value = scheduleResult.schedule.forbiddenTimes
                        }

                        PrayerTimeProvider.CompleteScheduleResult.PermissionDenied -> {
                            _hasLocationPermission.value = false
                            _prayerRanges.value = emptyMap()
                            _forbiddenTimes.value = emptyList()
                        }

                        PrayerTimeProvider.CompleteScheduleResult.LocationUnavailable -> {
                            _prayerRanges.value = emptyMap()
                            _forbiddenTimes.value = emptyList()
                        }

                        is PrayerTimeProvider.CompleteScheduleResult.PolarAnomalyError -> {
                            _prayerRanges.value = emptyMap()
                            _forbiddenTimes.value = emptyList()
                        }

                        is PrayerTimeProvider.CompleteScheduleResult.Error -> {
                            _prayerRanges.value = emptyMap()
                            _forbiddenTimes.value = emptyList()
                        }
                    }
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

    /**
     * Set and persist a manual location from raw user input.
     * @return false when the input is not a valid latitude/longitude pair;
     *         nothing is changed in that case.
     */
    fun setManualLocation(latitudeText: String, longitudeText: String): Boolean {
        val point = GeoPoint.parse(latitudeText, longitudeText) ?: return false
        if (!manualLocationStorage.save(point)) return false
        _manualLocation.value = point
        loadPrayerTimesFromLocation()
        return true
    }

    /**
     * Remove the manual location and fall back to the device location.
     */
    fun clearManualLocation() {
        manualLocationStorage.clear()
        _manualLocation.value = null
        loadPrayerTimesFromLocation()
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

        // A manual location works without the location permission.
        if (_hasLocationPermission.value || _manualLocation.value != null) {
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
