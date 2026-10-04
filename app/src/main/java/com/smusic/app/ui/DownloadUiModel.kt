package com.smusic.app.ui

import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState

/** Jobs grouped into the four sections the Downloads screen renders. */
data class DownloadSections(
    val active: List<DownloadJob>,
    val queued: List<DownloadJob>,
    val completed: List<DownloadJob>,
    val failed: List<DownloadJob>
) {
    val isEmpty: Boolean
        get() = active.isEmpty() && queued.isEmpty() && completed.isEmpty() && failed.isEmpty()
}

/**
 * Pure state → section mapping (unit tested): downloading/processing/waiting
 * are active; queued/analyzing/paused wait; completed succeeded; failed and
 * cancelled share the recovery section.
 */
object DownloadUiModel {

    fun sections(jobs: List<DownloadJob>): DownloadSections = DownloadSections(
        active = jobs.filter {
            it.state == JobState.DOWNLOADING ||
                it.state == JobState.PROCESSING ||
                it.state == JobState.WAITING
        },
        queued = jobs.filter {
            it.state == JobState.QUEUED ||
                it.state == JobState.ANALYZING ||
                it.state == JobState.PAUSED
        },
        completed = jobs.filter { it.state == JobState.COMPLETED },
        failed = jobs.filter {
            it.state == JobState.FAILED || it.state == JobState.CANCELLED
        }
    )
}
