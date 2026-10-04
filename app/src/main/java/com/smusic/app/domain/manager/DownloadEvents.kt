package com.smusic.app.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process revision counter.
 *
 * WorkManager workers (same process) bump this after writing queue/library
 * rows; [DownloadManager] observes it and refreshes its StateFlows, so the UI
 * reflects worker progress and completions without polling the database.
 * StateFlow conflation naturally coalesces bursts of bumps.
 */
object DownloadEvents {

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun bump() {
        _revision.value = System.nanoTime()
    }
}
