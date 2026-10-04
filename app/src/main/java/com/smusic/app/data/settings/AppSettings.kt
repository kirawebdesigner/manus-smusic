package com.smusic.app.data.settings

import android.content.Context
import com.smusic.app.domain.model.StorageCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent app settings (SharedPreferences).
 *
 * Every setting here controls real behavior:
 * - [defaultCategory] prefills the download destination on Home,
 * - [isWifiOnly] selects the WorkManager network constraint,
 * - [maxConcurrent] limits simultaneous downloads via a concurrency gate,
 * - [isAutoRetryEnabled] gates the worker's bounded transient retry,
 * - [isAutoplayNext] stops playback after the current track,
 * - [isResumePlayback] restores the last position when replaying an item,
 * - [isShuffleDefault] / [repeatDefault] seed each new playback queue.
 *
 * UI observes the `StateFlow` properties; enforcement code reads the cheap
 * `getXxx()` accessors (SharedPreferences keeps values cached in memory).
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Downloads ---

    private val _defaultCategory = MutableStateFlow(readCategory())
    val defaultCategory: StateFlow<StorageCategory> = _defaultCategory.asStateFlow()

    fun getDefaultCategory(): StorageCategory = readCategory()

    fun setDefaultCategory(category: StorageCategory) {
        prefs.edit().putString(KEY_DEFAULT_CATEGORY, category.name).apply()
        _defaultCategory.value = category
    }

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
    }

    private val _maxConcurrent = MutableStateFlow(readInt(KEY_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT))
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    fun getMaxConcurrent(): Int = readInt(KEY_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT)

    fun setMaxConcurrent(value: Int) {
        val clamped = value.coerceIn(1, MAX_CONCURRENT_LIMIT)
        prefs.edit().putInt(KEY_MAX_CONCURRENT, clamped).apply()
        _maxConcurrent.value = clamped
    }

    private val _autoRetry = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RETRY, true))
    val autoRetry: StateFlow<Boolean> = _autoRetry.asStateFlow()

    fun isAutoRetryEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_RETRY, true)

    fun setAutoRetry(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RETRY, enabled).apply()
        _autoRetry.value = enabled
    }

    // --- Playback ---

    private val _autoplayNext = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY_NEXT, true))
    val autoplayNext: StateFlow<Boolean> = _autoplayNext.asStateFlow()

    fun isAutoplayNext(): Boolean = prefs.getBoolean(KEY_AUTOPLAY_NEXT, true)

    fun setAutoplayNext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT, enabled).apply()
        _autoplayNext.value = enabled
    }

    private val _resumePlayback = MutableStateFlow(prefs.getBoolean(KEY_RESUME_PLAYBACK, true))
    val resumePlayback: StateFlow<Boolean> = _resumePlayback.asStateFlow()

    fun isResumePlayback(): Boolean = prefs.getBoolean(KEY_RESUME_PLAYBACK, true)

    fun setResumePlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, enabled).apply()
        _resumePlayback.value = enabled
    }

    private val _shuffleDefault = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE_DEFAULT, false))
    val shuffleDefault: StateFlow<Boolean> = _shuffleDefault.asStateFlow()

    fun isShuffleDefault(): Boolean = prefs.getBoolean(KEY_SHUFFLE_DEFAULT, false)

    fun setShuffleDefault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_DEFAULT, enabled).apply()
        _shuffleDefault.value = enabled
    }

    private val _repeatDefault = MutableStateFlow(readRepeatDefault())
    val repeatDefault: StateFlow<RepeatDefault> = _repeatDefault.asStateFlow()

    fun getRepeatDefault(): RepeatDefault = readRepeatDefault()

    fun setRepeatDefault(mode: RepeatDefault) {
        prefs.edit().putString(KEY_REPEAT_DEFAULT, mode.name).apply()
        _repeatDefault.value = mode
    }

    // --- Resume position persistence ---

    fun lastPlayedMediaId(): String? = prefs.getString(KEY_LAST_MEDIA_ID, null)

    fun lastPlayedPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION, 0L)

    fun savePlaybackPosition(mediaId: String, positionMs: Long) {
        prefs.edit()
            .putString(KEY_LAST_MEDIA_ID, mediaId)
            .putLong(KEY_LAST_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    private fun readCategory(): StorageCategory =
        runCatching { StorageCategory.valueOf(prefs.getString(KEY_DEFAULT_CATEGORY, StorageCategory.MUSIC.name)!!) }
            .getOrDefault(StorageCategory.MUSIC)

    private fun readRepeatDefault(): RepeatDefault =
        runCatching { RepeatDefault.valueOf(prefs.getString(KEY_REPEAT_DEFAULT, RepeatDefault.OFF.name)!!) }
            .getOrDefault(RepeatDefault.OFF)

    private fun readInt(key: String, fallback: Int): Int =
        prefs.getInt(key, fallback).coerceIn(1, MAX_CONCURRENT_LIMIT)

    enum class RepeatDefault { OFF, ALL, ONE }

    companion object {
        const val MAX_CONCURRENT_LIMIT = 3
        const val DEFAULT_MAX_CONCURRENT = 2

        private const val PREFS_NAME = "smusic_settings"
        private const val KEY_DEFAULT_CATEGORY = "default_category"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_MAX_CONCURRENT = "max_concurrent"
        private const val KEY_AUTO_RETRY = "auto_retry"
        private const val KEY_AUTOPLAY_NEXT = "autoplay_next"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
        private const val KEY_SHUFFLE_DEFAULT = "shuffle_default"
        private const val KEY_REPEAT_DEFAULT = "repeat_default"
        private const val KEY_LAST_MEDIA_ID = "last_media_id"
        private const val KEY_LAST_POSITION = "last_position_ms"
    }
}
