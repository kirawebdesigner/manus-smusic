package com.smusic.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.domain.manager.DownloadManager
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmusicViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = DownloadManager(application)

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult.asStateFlow()

    private val _selectedFormat = MutableStateFlow<MediaFormat?>(null)
    val selectedFormat: StateFlow<MediaFormat?> = _selectedFormat.asStateFlow()

    private val _selectedCategory = MutableStateFlow(StorageCategory.MUSIC)
    val selectedCategory: StateFlow<StorageCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val queue: StateFlow<List<DownloadJob>> = downloadManager.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library: StateFlow<List<LibraryItem>> = combine(
        downloadManager.libraryFlow,
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            items.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.creator.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUrl(value: String) {
        _url.value = value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFormat(format: MediaFormat) {
        _selectedFormat.value = format
    }

    fun selectCategory(category: StorageCategory) {
        _selectedCategory.value = category
    }

    fun analyze() {
        val currentUrl = _url.value.trim()
        if (currentUrl.isBlank()) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            val result = downloadManager.analyze(currentUrl)
            _analysisResult.value = result
            if (result is AnalysisResult.Success) {
                _selectedFormat.value = result.formats.firstOrNull()
            } else {
                _selectedFormat.value = null
            }
            _isAnalyzing.value = false
        }
    }

    fun clearAnalysis() {
        _analysisResult.value = null
        _selectedFormat.value = null
    }

    fun enqueueDownload() {
        val analysis = _analysisResult.value as? AnalysisResult.Success ?: return
        val format = _selectedFormat.value ?: return

        val destination = DownloadDestination(category = _selectedCategory.value)
        downloadManager.enqueueDownload(
            media = analysis.media,
            format = format,
            destination = destination
        )
    }

    fun cancelJob(jobId: String) {
        downloadManager.cancelJob(jobId)
    }

    fun retryJob(jobId: String) {
        downloadManager.retryJob(jobId)
    }

    fun removeJob(jobId: String) {
        downloadManager.removeJob(jobId)
    }

    fun clearCompletedJobs() {
        downloadManager.clearCompleted()
    }

    fun toggleFavorite(itemId: String) {
        downloadManager.toggleFavorite(itemId)
    }

    fun deleteLibraryItem(itemId: String) {
        downloadManager.deleteLibraryItem(itemId)
    }

    fun refreshAll() {
        downloadManager.refreshState()
    }
}
