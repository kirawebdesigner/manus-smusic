package com.smusic.app.domain.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.data.settings.AppSettings
import com.smusic.app.domain.model.MediaType
import com.smusic.app.service.SmusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the UI needs to render playback, as one immutable snapshot. */
data class PlayerState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentIndex: Int = -1,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null
) {
    /** True while a track is loaded and available for the mini/full player. */
    val isActive: Boolean get() = currentIndex >= 0 && title != null
}

/**
 * UI-facing playback abstraction over Media3.
 *
 * The UI never touches ExoPlayer directly: it calls intent methods
 * (play/pause/seek/next) and observes [state]. The actual player lives in
 * [SmusicPlaybackService] (a MediaSessionService), so playback — and the
 * system media notification/lock-screen controls — survive navigation,
 * backgrounding, and activity recreation. Connection is a [MediaController]
 * bound to that service.
 */
class PlayerController(
    context: Context,
    private val settings: AppSettings
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var pendingPlay: PendingPlay? = null

    /** mediaId → mediaType for the current queue (kept locally, not in Media3). */
    private val mediaTypes = mutableMapOf<String, MediaType>()

    private data class PendingPlay(
        val items: List<LibraryItem>,
        val startIndex: Int
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishCurrentState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(errorMessage = "Playback failed. The file may have been moved or deleted.")
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, SmusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected == null) {
                controllerFuture = null
                _state.update { it.copy(errorMessage = "Player is unavailable right now.") }
                return@addListener
            }
            controller = connected
            connected.addListener(playerListener)
            _state.update { it.copy(isConnected = true) }
            publishCurrentState()
            startPositionPolling()
            pendingPlay?.let { play ->
                pendingPlay = null
                applyPlay(connected, play.items, play.startIndex)
            }
            // Callbacks arrive on the main thread for Compose state safety.
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Replaces the queue with [items] and starts playback at [startIndex]. */
    fun play(items: List<LibraryItem>, startIndex: Int) {
        if (items.isEmpty() || startIndex !in items.indices) return
        val active = controller
        if (active == null) {
            pendingPlay = PendingPlay(items, startIndex)
            connect()
            return
        }
        applyPlay(active, items, startIndex)
    }

    private fun applyPlay(active: MediaController, items: List<LibraryItem>, startIndex: Int) {
        items.forEach { mediaTypes[it.id] = it.mediaType }

        val resumeFrom = if (settings.isResumePlayback() &&
            items[startIndex].id == settings.lastPlayedMediaId()
        ) {
            settings.lastPlayedPositionMs()
        } else {
            0L
        }

        active.setMediaItems(items.map { toMediaItem(it) }, startIndex, resumeFrom)
        active.shuffleModeEnabled = settings.isShuffleDefault()
        active.repeatMode = toMedia3Repeat(settings.getRepeatDefault())
        active.prepare()
        active.play()

        _state.update {
            it.copy(errorMessage = null, positionMs = resumeFrom)
        }
        publishCurrentState()
    }

    fun togglePlayPause() {
        val active = controller ?: return
        if (active.isPlaying) active.pause() else active.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        publishCurrentState()
    }

    fun seekNext() {
        controller?.seekToNext()
        publishCurrentState()
    }

    fun seekPrevious() {
        val active = controller ?: return
        // Standard player behavior: restart the track unless we're near its start.
        if (active.currentPosition > 3_000L) {
            active.seekTo(0L)
        } else {
            active.seekToPrevious()
        }
        publishCurrentState()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        publishCurrentState()
    }

    fun cycleRepeatMode() {
        val active = controller ?: return
        active.repeatMode = when (active.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishCurrentState()
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun release() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingPlay = null
        scope.cancel()
    }

    // --- Internals ---------------------------------------------------------------

    private fun startPositionPolling() {
        if (positionJob != null) return
        positionJob = scope.launch {
            while (isActive) {
                delay(POSITION_POLL_MS)
                val active = controller ?: break
                publishCurrentState()

                // Persist resume position for the "Resume playback" setting.
                if (settings.isResumePlayback() && active.isPlaying) {
                    active.currentMediaItem?.mediaId?.let { mediaId ->
                        settings.savePlaybackPosition(mediaId, active.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
        }
    }

    private fun publishCurrentState() {
        val active = controller ?: return
        val index = active.currentMediaItemIndex
        val item = active.currentMediaItem
        val metadata = item?.mediaMetadata
        _state.update {
            it.copy(
                isConnected = true,
                isPlaying = active.isPlaying,
                isBuffering = active.playbackState == Player.STATE_BUFFERING,
                currentIndex = index,
                title = metadata?.title?.toString()
                    ?: item?.localConfiguration?.uri?.lastPathSegment,
                artist = metadata?.artist?.toString(),
                album = metadata?.albumTitle?.toString(),
                mediaType = item?.mediaId?.let { id -> mediaTypes[id] } ?: MediaType.AUDIO,
                positionMs = active.currentPosition.coerceAtLeast(0L),
                durationMs = active.duration.takeIf { d -> d > 0L && d != C.TIME_UNSET } ?: 0L,
                hasPrevious = active.hasPreviousMediaItem() || active.currentPosition > 3_000L,
                hasNext = active.hasNextMediaItem(),
                shuffleEnabled = active.shuffleModeEnabled,
                repeatMode = fromMedia3Repeat(active.repeatMode)
            )
        }
    }

    private fun toMediaItem(item: LibraryItem): MediaItem =
        MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(Uri.fromFile(File(item.localPath)))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.creator)
                    .setAlbumTitle(item.album)
                    .build()
            )
            .build()

    private fun toMedia3Repeat(mode: AppSettings.RepeatDefault): Int = when (mode) {
        AppSettings.RepeatDefault.OFF -> Player.REPEAT_MODE_OFF
        AppSettings.RepeatDefault.ALL -> Player.REPEAT_MODE_ALL
        AppSettings.RepeatDefault.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun fromMedia3Repeat(mode: Int): RepeatMode = when (mode) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    companion object {
        private const val POSITION_POLL_MS = 500L
    }
}
