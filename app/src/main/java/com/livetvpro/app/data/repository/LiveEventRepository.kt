package com.livetvpro.app.data.repository

import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.LiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveEventRepository @Inject constructor(
    private val dataRepository: NativeDataRepository
) {
    suspend fun getLiveEvents(): List<LiveEvent> = withContext(Dispatchers.IO) {
        val nativeEvents = if (dataRepository.isDataLoaded()) dataRepository.getLiveEvents() else emptyList()
        val externalEvents = dataRepository.getExternalLiveEvents()
        val externalIds = externalEvents.map { it.id }.toSet()
        externalEvents + nativeEvents.filter { it.id !in externalIds }
    }

    suspend fun getEventById(eventId: String): LiveEvent? = withContext(Dispatchers.IO) {
        getLiveEvents().find { it.id == eventId }
    }

    suspend fun getEventCategories(): List<EventCategory> = withContext(Dispatchers.IO) {
        val nativeCategories = if (dataRepository.isDataLoaded()) dataRepository.getEventCategories() else emptyList()
        val externalCategories = dataRepository.getExternalLiveEvents()
            .map { it.eventCategoryName }
            .filter { it.isNotBlank() }
            .distinct()
            .map { catName ->
                EventCategory(
                    id      = catName,
                    name    = catName,
                    slug    = catName.lowercase().replace(" ", "_"),
                    logoUrl = ""
                )
            }
        val nativeIds = nativeCategories.map { it.id }.toSet()
        nativeCategories + externalCategories.filter { it.id !in nativeIds }
    }
}
