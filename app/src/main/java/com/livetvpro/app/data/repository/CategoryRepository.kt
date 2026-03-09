package com.livetvpro.app.data.repository

import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dataRepository: NativeDataRepository
) {
    suspend fun getCategories(): List<Category> {
        if (!dataRepository.isDataLoaded()) {
            return emptyList()
        }
        return dataRepository.getCategories()
    }

    suspend fun getCategoryBySlug(slug: String): Category? {
        return getCategories().find { it.slug == slug }
    }

    suspend fun getSports(): List<Channel> {
        if (!dataRepository.isDataLoaded()) {
            return emptyList()
        }
        return dataRepository.getSports()
    }
}
