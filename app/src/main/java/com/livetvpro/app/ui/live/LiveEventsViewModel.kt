package com.livetvpro.app.ui.live

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.repository.LiveEventRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class StatusCounts(
    val all: Int = 0,
    val live: Int = 0,
    val upcoming: Int = 0,
    val recent: Int = 0,
)

@HiltViewModel
class LiveEventsViewModel @Inject constructor(
    private val liveEventRepository: LiveEventRepository,
    private val themeManager: ThemeManager
) : RetryViewModel() {

    private val _events = MutableLiveData<List<LiveEvent>>()
    val events: LiveData<List<LiveEvent>> = _events

    private val _eventCategories = MutableLiveData<List<EventCategory>>()
    val eventCategories: LiveData<List<EventCategory>> = _eventCategories

    private val _filteredEvents = MutableLiveData<List<LiveEvent>>()
    val filteredEvents: LiveData<List<LiveEvent>> = _filteredEvents

    private val _statusCounts = MutableLiveData(StatusCounts())
    val statusCounts: LiveData<StatusCounts> = _statusCounts

    private val _categoryCounts = MutableLiveData<Map<String, Int>>(emptyMap())
    val categoryCounts: LiveData<Map<String, Int>> = _categoryCounts

    val primaryColorFlow: StateFlow<Int> = themeManager.primaryColorFlow

    var pendingStatusFilter: EventStatus? = null
        private set
    var pendingCategoryId: String = "evt_cat_all"
        private set
    var pendingSearchQuery: String = ""
        private set

    private val sdf = java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault()
    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

    init {
        loadData()
    }

    override fun loadData() {
        loadEvents()
    }

    override fun onResume() {
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                startLoading()
                val events = liveEventRepository.getLiveEvents()
                _events.value = events
                Timber.d("Loaded ${events.size} live events")
                finishLoading(dataIsEmpty = events.isEmpty())
                filterEvents(pendingStatusFilter, pendingCategoryId)
            } catch (e: OutOfMemoryError) {
                System.gc()
                Timber.e("OOM loading live events")
                _events.value = emptyList()
                finishLoading(dataIsEmpty = true, error = Exception("Low memory. Please close other apps and try again."))
            } catch (e: Exception) {
                Timber.e(e, "Error loading live events")
                _events.value = emptyList()
                finishLoading(dataIsEmpty = true, error = e)
            }
        }
    }

    fun loadEventCategories() {
        if (_eventCategories.value != null) return
        viewModelScope.launch {
            try {
                val categories = liveEventRepository.getEventCategories()
                _eventCategories.value = categories
                Timber.d("Loaded ${categories.size} event categories")
            } catch (e: OutOfMemoryError) {
                System.gc()
                Timber.e("OOM loading event categories")
                _eventCategories.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading event categories")
            }
        }
    }

    fun filterEvents(status: EventStatus?, categoryId: String = "evt_cat_all") {
        pendingStatusFilter = status
        pendingCategoryId = categoryId
        applyFilter()
    }

    fun filterEventsSilent(status: EventStatus?, categoryId: String = "evt_cat_all") {
        viewModelScope.launch {
            try {
                val events = liveEventRepository.getLiveEvents()
                _events.value = events
                pendingStatusFilter = status
                pendingCategoryId = categoryId
                applyFilter()
            } catch (e: Exception) {
                Timber.e(e, "Error in silent refresh")
            }
        }
    }

    private fun matchesLive(event: LiveEvent, currentTime: Long): Boolean =
        event.isLive || isEventLiveByTime(event, currentTime)

    private fun matchesUpcoming(event: LiveEvent, currentTime: Long): Boolean =
        !event.isLive && !isEventLiveByTime(event, currentTime) && isEventUpcoming(event, currentTime)

    private fun matchesRecent(event: LiveEvent, currentTime: Long): Boolean =
        !event.isLive && isEventEnded(event, currentTime)

    private fun applySearch(events: List<LiveEvent>): List<LiveEvent> {
        if (pendingSearchQuery.isEmpty()) return events
        val q = pendingSearchQuery.lowercase()
        return events.filter { event ->
            event.title.lowercase().contains(q) ||
            event.team1Name.lowercase().contains(q) ||
            event.team2Name.lowercase().contains(q)
        }
    }

    private fun applyFilter() {
        val allEvents = _events.value ?: return
        val currentTime = System.currentTimeMillis()
        val searched = applySearch(allEvents)

        // Status counts (All/Live/Upcoming/Recent) scoped to the currently selected category.
        val categoryScoped = if (pendingCategoryId != "evt_cat_all") {
            searched.filter { it.eventCategoryId == pendingCategoryId }
        } else searched
        _statusCounts.value = StatusCounts(
            all = categoryScoped.size,
            live = categoryScoped.count { matchesLive(it, currentTime) },
            upcoming = categoryScoped.count { matchesUpcoming(it, currentTime) },
            recent = categoryScoped.count { matchesRecent(it, currentTime) },
        )

        val statusScoped = when (pendingStatusFilter) {
            EventStatus.LIVE -> searched.filter { matchesLive(it, currentTime) }
            EventStatus.UPCOMING -> searched.filter { matchesUpcoming(it, currentTime) }
            EventStatus.RECENT -> searched.filter { matchesRecent(it, currentTime) }
            null -> searched
        }
        val counts = mutableMapOf<String, Int>()
        counts["evt_cat_all"] = statusScoped.size
        for (event in statusScoped) {
            val id = event.eventCategoryId
            if (!id.isNullOrEmpty()) counts[id] = (counts[id] ?: 0) + 1
        }
        if (counts != _categoryCounts.value) {
            _categoryCounts.value = counts
        }

        var filtered = searched

        if (pendingCategoryId != "evt_cat_all") {
            filtered = filtered.filter { event ->
                event.eventCategoryId == pendingCategoryId
            }
        }

        filtered = when (pendingStatusFilter) {
            EventStatus.LIVE -> {
                filtered.filter { event -> matchesLive(event, currentTime) }.sortedWith(
                    compareByDescending<LiveEvent> { it.wrapper.isNotEmpty() }
                        .thenBy { it.startTime }
                )
            }
            EventStatus.UPCOMING -> {
                filtered.filter { event -> matchesUpcoming(event, currentTime) }.sortedWith(
                    compareByDescending<LiveEvent> { it.wrapper.isNotEmpty() }
                        .thenBy { it.startTime }
                )
            }
            EventStatus.RECENT -> {
                filtered.filter { event -> matchesRecent(event, currentTime) }
                    .sortedByDescending { it.startTime }
            }
            null -> {
                filtered.sortedWith(
                    compareBy<LiveEvent> { event ->
                        when {
                            matchesLive(event, currentTime) -> 0
                            isEventUpcoming(event, currentTime) -> 1
                            else -> 2
                        }
                    }.thenByDescending { it.wrapper.isNotEmpty() }
                     .thenBy { it.startTime }
                )
            }
        }

        if (filtered != _filteredEvents.value) {
            _filteredEvents.value = filtered
        }
    }

    fun searchEvents(query: String) {
        pendingSearchQuery = query
        filterEvents(pendingStatusFilter, pendingCategoryId)
    }

    private fun isEventLiveByTime(event: LiveEvent, currentTime: Long): Boolean {
        return try {
            val startTime = parseTimestamp(event.startTime)
            val endTime = event.endTime?.let { parseTimestamp(it) } ?: Long.MAX_VALUE
            currentTime in startTime..endTime
        } catch (e: Exception) {
            false
        }
    }

    private fun isEventUpcoming(event: LiveEvent, currentTime: Long): Boolean {
        return try {
            val startTime = parseTimestamp(event.startTime)
            currentTime < startTime
        } catch (e: Exception) {
            false
        }
    }

    private fun isEventEnded(event: LiveEvent, currentTime: Long): Boolean {
        return try {
            val endTime = event.endTime?.let { parseTimestamp(it) }
                ?: parseTimestamp(event.startTime)
            currentTime > endTime
        } catch (e: Exception) {
            false
        }
    }

    private fun parseTimestamp(timeString: String): Long {
        return try {
            sdf.parse(timeString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
