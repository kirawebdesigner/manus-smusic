package com.smusic.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.smusic.app.data.AnalysisResult
import com.smusic.app.data.DownloadState
import com.smusic.app.data.DownloadTask
import com.smusic.app.data.MediaFormat
import com.smusic.app.data.MediaItem
import com.smusic.app.data.MediaRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val _library = MutableStateFlow(repository.all())
    val library: StateFlow<List<MediaItem>> = _library.asStateFlow()
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
        val existing = _library.value.firstOrNull { it.url == result.media.url && it.state == DownloadState.COMPLETE && it.localPath != null }
        if (existing != null) {
            _download.value = DownloadTask(existing, format, 1f, DownloadState.COMPLETE)
            return
        }
        val workId = repository.enqueue(result.media, format)
        _download.value = DownloadTask(result.media.copy(state = DownloadState.QUEUED), format, 0f, DownloadState.QUEUED)
        if (workId != UUID(0L, 0L)) observeDownload(workId, result.media, format)
    }

    private fun observeDownload(workId: UUID, media: MediaItem, format: MediaFormat) {
        viewModelScope.launch {
            repository.work(workId).collect { info ->
                if (info == null) return@collect
                val downloaded = info.progress.getLong("downloaded", 0L)
                val total = info.progress.getLong("total", 0L)
                val state = when (info.state) {
                    WorkInfo.State.ENQUEUED -> DownloadState.QUEUED
                    WorkInfo.State.RUNNING -> DownloadState.DOWNLOADING
                    WorkInfo.State.SUCCEEDED -> DownloadState.COMPLETE
                    WorkInfo.State.CANCELLED -> DownloadState.CANCELLED
                    WorkInfo.State.FAILED -> DownloadState.FAILED
                    WorkInfo.State.BLOCKED -> DownloadState.PAUSED
                }
                val progress = when {
                    total > 0 -> downloaded.toFloat() / total.toFloat()
                    state == DownloadState.COMPLETE -> 1f
                    else -> 0f
                }
                _download.value = DownloadTask(media, format, progress, state)
                if (info.state.isFinished) _library.value = repository.all()
            }
        }
    }

    fun refreshLibrary() { viewModelScope.launch(Dispatchers.IO) { _library.value = repository.all() } }
    fun delete(item: MediaItem) { viewModelScope.launch(Dispatchers.IO) { repository.delete(item); _library.value = repository.all() } }
    fun clearAnalysis() { _analysis.value = null; _download.value = null }
}
