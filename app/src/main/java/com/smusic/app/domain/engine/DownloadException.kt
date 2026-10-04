package com.smusic.app.domain.engine

import java.io.IOException

/**
 * Base type for download failures. [userMessage] is a UI-safe description;
 * [getMessage] keeps the technical detail for logs and tests.
 *
 * Subclasses are [IOException]s so existing callers and tests that treat any
 * download failure as an IO problem keep working.
 */
open class DownloadException(
    message: String,
    val userMessage: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Permanent failure: retrying the same request can't succeed
 * (HTTP 404, malformed URL, redirect loop, empty body, rejected range).
 */
class PermanentDownloadException(
    message: String,
    userMessage: String
) : DownloadException(message, userMessage)

/**
 * Transient failure: worth a bounded automatic retry
 * (timeouts, connection resets, HTTP 408/429/5xx).
 */
class TransientDownloadException(
    message: String,
    userMessage: String,
    cause: Throwable? = null
) : DownloadException(message, userMessage, cause)
