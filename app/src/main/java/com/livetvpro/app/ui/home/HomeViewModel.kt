package com.livetvpro.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.repository.CategoryRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val themeManager: ThemeManager
) : RetryViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _filteredCategories = MutableLiveData<List<Category>>()
    val filteredCategories: LiveData<List<Category>> = _filteredCategories

    val primaryColorFlow: StateFlow<Int> = themeManager.primaryColorFlow

    var currentSearchQuery = ""
        private set

    init {
        loadData()
    }

    override fun loadData() {
        viewModelScope.launch {
            try {
                startLoading()
                val categories = categoryRepository.getCategories()
                _categories.value = categories
                applyFilter()
                finishLoading(dataIsEmpty = categories.isEmpty())
            } catch (e: Exception) {
                _categories.value = emptyList()
                _filteredCategories.value = emptyList()
                finishLoading(dataIsEmpty = true, error = e)
            }
        }
    }

    override fun onResume() {
    }

    fun refreshSilent() {
        viewModelScope.launch {
            try {
                val categories = categoryRepository.getCategories()
                _categories.value = categories
                applyFilter()
            } catch (e: Exception) {
            }
        }
    }

    fun searchCategories(query: String) {
        currentSearchQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        try {
            val allCategories = _categories.value ?: emptyList()
            _filteredCategories.value = if (currentSearchQuery.isBlank()) {
                allCategories
            } else {
                allCategories.filter {
                    it.name.contains(currentSearchQuery, ignoreCase = true) ||
                    it.slug.contains(currentSearchQuery, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
        }
    }
}
