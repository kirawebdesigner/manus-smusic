package com.smusic.app.ui

import com.smusic.app.domain.model.DownloadDestination
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaInfo
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every JobState must land in exactly one Downloads screen section. */
class DownloadUiModelTest {

    private fun job(state: JobState): DownloadJob = DownloadJob(
        id = "job-${state.name}",
        mediaInfo = MediaInfo(
            id = "url",
            title = "Title",
            uploader = "Uploader",
            source = "Direct",
            duration = "",
            fileSize = "",
            mediaType = MediaType.AUDIO,
            originalUrl = "https://example.com/file.mp3"
        ),
        selectedFormat = MediaFormat(
            id = "f",
            label = "MP3",
            container = "mp3",
            mimeType = "audio/mpeg",
            fileSize = ""
        ),
        destination = DownloadDestination(StorageCategory.MUSIC),
        state = state
    )

    @Test
    fun `active section holds running jobs`() {
        val sections = DownloadUiModel.sections(
            listOf(job(JobState.DOWNLOADING), job(JobState.PROCESSING), job(JobState.WAITING))
        )
        assertEquals(listOf(JobState.DOWNLOADING, JobState.PROCESSING, JobState.WAITING), sections.active.map { it.state })
        assertTrue(sections.queued.isEmpty())
        assertTrue(sections.completed.isEmpty())
        assertTrue(sections.failed.isEmpty())
        assertFalse(sections.isEmpty)
    }

    @Test
    fun `queued section holds waiting-room jobs`() {
        val sections = DownloadUiModel.sections(
            listOf(job(JobState.QUEUED), job(JobState.ANALYZING), job(JobState.PAUSED))
        )
        assertEquals(listOf(JobState.QUEUED, JobState.ANALYZING, JobState.PAUSED), sections.queued.map { it.state })
        assertTrue(sections.active.isEmpty())
    }

    @Test
    fun `completed section holds finished jobs`() {
        val sections = DownloadUiModel.sections(listOf(job(JobState.COMPLETED)))
        assertEquals(1, sections.completed.size)
        assertTrue(sections.active.isEmpty())
        assertTrue(sections.failed.isEmpty())
    }

    @Test
    fun `failed section holds failed and cancelled jobs for recovery`() {
        val sections = DownloadUiModel.sections(listOf(job(JobState.FAILED), job(JobState.CANCELLED)))
        assertEquals(setOf(JobState.FAILED, JobState.CANCELLED), sections.failed.map { it.state }.toSet())
        assertTrue(sections.active.isEmpty())
        assertTrue(sections.queued.isEmpty())
    }

    @Test
    fun `every state maps to exactly one section`() {
        val all = JobState.entries.map { job(it) }
        val sections = DownloadUiModel.sections(all)
        val total = sections.active.size + sections.queued.size + sections.completed.size + sections.failed.size
        assertEquals(JobState.entries.size, total)
    }

    @Test
    fun `empty job list produces empty sections`() {
        val sections = DownloadUiModel.sections(emptyList())
        assertTrue(sections.isEmpty)
    }
}
