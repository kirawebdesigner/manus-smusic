package com.smusic.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.auth.SpotifyAuthManager
import com.smusic.app.data.settings.AppSettings
import com.smusic.app.domain.discovery.DiscoveryManager
import com.smusic.app.domain.discovery.DiscoveryResult
import com.smusic.app.domain.library.LibrarySearch
import com.smusic.app.domain.manager.DownloadManager
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.domain.player.PlayerController
import com.smusic.app.domain.player.PlayerState
import com.smusic.app.domain.storage.StorageUsage
import java.io.File
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SmusicViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = DownloadManager(application)
    private val settings: AppSettings get() = downloadManager.settings
    private val spotifyAuth = SpotifyAuthManager(application, BuildConfig.SMUSIC_SPOTIFY_CLIENT_ID)
    private val discoveryManager = DiscoveryManager(spotifyToken = { spotifyAuth.accessToken() })

    // --- Playback ---

    val playerController = PlayerController(application, settings)

    val playerState: StateFlow<PlayerState> = playerController.state

    // --- Analysis ---

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult.asStateFlow()

    private val _discoveryResult = MutableStateFlow<DiscoveryResult?>(null)
    val discoveryResult: StateFlow<DiscoveryResult?> = _discoveryResult.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _spotifyConnected = MutableStateFlow(spotifyAuth.isConnected())
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _selectedFormat = MutableStateFlow<MediaFormat?>(null)
    val selectedFormat: StateFlow<MediaFormat?> = _selectedFormat.asStateFlow()

    private val _selectedCategory = MutableStateFlow(StorageCategory.MUSIC)
    val selectedCategory: StateFlow<StorageCategory> = _selectedCategory.asStateFlow()

    // --- Library ---

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Debounced so filtering runs once the user pauses typing, not per keystroke. */
    private val debouncedQuery = _searchQuery.debounce(SEARCH_DEBOUNCE_MS)

    val queue: StateFlow<List<DownloadJob>> = downloadManager.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryLoaded: StateFlow<Boolean> = downloadManager.libraryLoaded

    val library: StateFlow<List<LibraryItem>> = combine(
        downloadManager.libraryFlow,
        debouncedQuery
    ) { items, query ->
        LibrarySearch.filter(items, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Settings ---

    val defaultCategory: StateFlow<StorageCategory> = settings.defaultCategory
    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly
    val maxConcurrent: StateFlow<Int> = settings.maxConcurrent
    val autoRetry: StateFlow<Boolean> = settings.autoRetry
    val autoplayNext: StateFlow<Boolean> = settings.autoplayNext
    val resumePlayback: StateFlow<Boolean> = settings.resumePlayback
    val shuffleDefault: StateFlow<Boolean> = settings.shuffleDefault
    val repeatDefault: StateFlow<AppSettings.RepeatDefault> = settings.repeatDefault

    private val _storageUsage = MutableStateFlow<StorageUsage?>(null)
    val storageUsage: StateFlow<StorageUsage?> = _storageUsage.asStateFlow()

    init {
        playerController.connect()
        _selectedCategory.value = settings.getDefaultCategory()
    }

    // --- Actions: analysis ---

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

    /** Existing Home action: direct media uses the download analyzer; names/platform links use discovery. */
    fun analyzeOrDiscover() {
        val value = _url.value.trim()
        if (value.isBlank()) return
        val platformLink = value.contains("youtube.com", true) || value.contains("youtu.be", true) ||
            value.contains("spotify.com", true) || value.contains("spotify.link", true)
        if (!platformLink && (value.startsWith("http://") || value.startsWith("https://"))) {
            analyze()
            return
        }
        viewModelScope.launch {
            _isDiscovering.value = true
            _discoveryResult.value = discoveryManager.execute(value)
            _isDiscovering.value = false
        }
    }

    fun beginSpotifyConnect(): Uri? = spotifyAuth.authorizationUri()

    fun handleSpotifyCallback(uri: Uri) {
        viewModelScope.launch {
            _spotifyConnected.value = spotifyAuth.handleCallback(uri).isSuccess
        }
    }

    fun clearAnalysis() {
        _analysisResult.value = null
        _selectedFormat.value = null
        _discoveryResult.value = null
    }

    fun enqueueDownload() {
        val analysis = _analysisResult.value as? AnalysisResult.Success ?: return
        val format = _selectedFormat.value ?: return

        val destination = DownloadDestination(category = _selectedCategory.value)
        viewModelScope.launch {
            downloadManager.enqueueDownload(
                media = analysis.media,
                format = format,
                destination = destination
            )
        }
    }

    // --- Actions: queue ---

    fun cancelJob(jobId: String) = downloadManager.cancelJob(jobId)

    fun retryJob(jobId: String) = downloadManager.retryJob(jobId)

    fun removeJob(jobId: String) = downloadManager.removeJob(jobId)

    fun clearCompletedJobs() = downloadManager.clearCompleted()

    // --- Actions: library ---

    fun toggleFavorite(itemId: String) = downloadManager.toggleFavorite(itemId)

    fun deleteLibraryItem(itemId: String) = downloadManager.deleteLibraryItem(itemId)

    /** Starts playback of [item] with the full library as the queue. */
    fun playItem(item: LibraryItem) {
        val all = downloadManager.libraryFlow.value
        val playlist = if (all.any { it.id == item.id }) all else listOf(item)
        val start = playlist.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        playerController.play(playlist, start)
        downloadManager.recordPlay(item.id)
    }

    /** Plays the library item (or a raw file) at [path] — used by Downloads. */
    fun playItemByPath(path: String) {
        val item = downloadManager.libraryFlow.value.firstOrNull { it.localPath == path }
            ?: LibraryItem(
                id = path,
                title = File(path).nameWithoutExtension,
                creator = "Saved media",
                localPath = path
            )
        playItem(item)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun seekNext() = playerController.seekNext()
    fun seekPrevious() = playerController.seekPrevious()
    fun toggleShuffle() = playerController.setShuffle(!playerController.state.value.shuffleEnabled)
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun dismissPlayerError() = playerController.dismissError()

    // --- Actions: settings ---

    fun setDefaultCategory(category: StorageCategory) = settings.setDefaultCategory(category)
    fun setWifiOnly(enabled: Boolean) = settings.setWifiOnly(enabled)
    fun setMaxConcurrent(value: Int) = settings.setMaxConcurrent(value)
    fun setAutoRetry(enabled: Boolean) = settings.setAutoRetry(enabled)
    fun setAutoplayNext(enabled: Boolean) = settings.setAutoplayNext(enabled)
    fun setResumePlayback(enabled: Boolean) = settings.setResumePlayback(enabled)
    fun setShuffleDefault(enabled: Boolean) = settings.setShuffleDefault(enabled)
    fun setRepeatDefault(mode: AppSettings.RepeatDefault) = settings.setRepeatDefault(mode)

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsage.value = downloadManager.storageUsage()
        }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch {
            downloadManager.clearTemporaryFiles()
            refreshStorageUsage()
        }
    }

    fun clearDownloadHistory() {
        downloadManager.clearCompleted()
        refreshStorageUsage()
    }

    fun refreshAll() {
        downloadManager.refreshState()
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
