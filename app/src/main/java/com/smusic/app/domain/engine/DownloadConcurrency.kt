package com.smusic.app.domain.engine

import kotlinx.coroutines.sync.Semaphore

/**
 * Limits how many downloads stream bytes at the same time.
 *
 * Each worker acquires a [ConcurrencyGate] before downloading and releases the
 * exact gate it acquired in a `finally` block, so reconfiguring the limit only
 * affects downloads that start afterwards — no permit accounting bugs.
 */
object DownloadConcurrencyLimiter {

    @Volatile
    private var gate: ConcurrencyGate = ConcurrencyGate(DEFAULT_PERMITS)

    /** Applies a new limit (1..3). No-op when unchanged. */
    fun configure(permits: Int) {
        val clamped = permits.coerceIn(1, MAX_PERMITS)
        synchronized(this) {
            if (gate.permits != clamped) {
                gate = ConcurrencyGate(clamped)
            }
        }
    }

    /** Suspends until a download slot is free. Returns the gate to release later. */
    suspend fun acquire(): ConcurrencyGate {
        val current = gate
        current.acquire()
        return current
    }

    fun release(acquired: ConcurrencyGate) {
        acquired.release()
    }

    class ConcurrencyGate internal constructor(val permits: Int) {
        private val semaphore = Semaphore(permits)

        internal suspend fun acquire() = semaphore.acquire()

        internal fun release() = semaphore.release()
    }

    private const val MAX_PERMITS = 3
    private const val DEFAULT_PERMITS = 2
}
