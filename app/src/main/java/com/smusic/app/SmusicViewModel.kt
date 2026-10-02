package com.smusic.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smusic.app.data.AnalysisResult
import com.smusic.app.data.DownloadState
import com.smusic.app.data.MediaFormat
import com.smusic.app.data.MediaItem
import com.smusic.app.data.MediaRepository
import com.smusic.app.data.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmusicViewModel : ViewModel() {
    private val repository = MediaRepository()
    val library: StateFlow<List<MediaItem>> = repository.library
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()
    private val _selectedFormat = MutableStateFlow<MediaFormat?>(null)
    val selectedFormat: StateFlow<MediaFormat?> = _selectedFormat.asStateFlow()
    private val _download = MutableStateFlow<DownloadTask?>(null)
    val download: StateFlow<DownloadTask?> = _download.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setUrl(value: String) { _url.value = value }

    fun analyze() {
        viewModelScope.launch {
            _busy.value = true
            _analysis.value = repository.analyze(_url.value)
            _selectedFormat.value = (_analysis.value as? AnalysisResult.Success)?.formats?.firstOrNull()
            _busy.value = false
        }
    }

    fun selectFormat(format: MediaFormat) { _selectedFormat.value = format }

    fun download() {
        val result = _analysis.value as? AnalysisResult.Success ?: return
        val format = _selectedFormat.value ?: return
        viewModelScope.launch {
            _download.value = DownloadTask(result.media, format, 0f, DownloadState.DOWNLOADING)
            runCatching { repository.download(result.media, format) { progress -> _download.value = DownloadTask(result.media, format, progress, DownloadState.DOWNLOADING) } }
                .onSuccess { _download.value = it }
                .onFailure { _download.value = DownloadTask(result.media, format, 0f, DownloadState.FAILED) }
        }
    }

    fun clearAnalysis() { _analysis.value = null; _download.value = null }
}
