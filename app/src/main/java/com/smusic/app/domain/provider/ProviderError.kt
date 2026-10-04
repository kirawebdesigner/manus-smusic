package com.smusic.app.domain.provider

/**
 * User-facing provider failures.
 *
 * [title] and [message] are safe to render directly in the UI. Technical detail
 * (stack traces, raw HTTP codes beyond the friendly suffix) belongs in logs,
 * never on screen.
 */
sealed interface ProviderError {
    val title: String
    val message: String

    data object InvalidUrl : ProviderError {
        override val title: String = "Invalid link"
        override val message: String =
            "That doesn't look like a valid web address. Check the link and try again."
    }

    data object NetworkUnavailable : ProviderError {
        override val title: String = "Can't reach the server"
        override val message: String =
            "Check your internet connection and try again."
    }

    data object MediaUnavailable : ProviderError {
        override val title: String = "Media unavailable"
        override val message: String =
            "The server couldn't provide this file. It may have been removed or the link may have expired."
    }

    data object ServerRejected : ProviderError {
        override val title: String = "Server rejected the request"
        override val message: String =
            "The server refused to share this file. It may be private or rate-limited."
    }

    data object ProtectedContent : ProviderError {
        override val title: String = "Protected source"
        override val message: String =
            "Smusic doesn't download protected or restricted content."
    }

    data object UnknownMediaType : ProviderError {
        override val title: String = "Not a media file"
        override val message: String =
            "This link doesn't point to a supported audio or video file. Paste a direct link to the media file itself."
    }

    data object AnalysisFailed : ProviderError {
        override val title: String = "Couldn't analyze this link"
        override val message: String =
            "Something went wrong while reading the link. Please try again."
    }
}
