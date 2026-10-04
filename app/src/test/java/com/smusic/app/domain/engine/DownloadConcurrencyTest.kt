package com.smusic.app.domain.engine

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The limiter gates how many downloads stream bytes at once. These tests pin
 * the two invariants: the configured permit count is honored (and clamped),
 * and releasing always returns the exact gate that was acquired.
 */
class DownloadConcurrencyTest {

    @Test
    fun `a second download waits until a permit is released`() {
        runBlocking {
        DownloadConcurrencyLimiter.configure(1)

        val first = DownloadConcurrencyLimiter.acquire()
        var secondAcquired = false
        var secondGate: DownloadConcurrencyLimiter.ConcurrencyGate? = null

        val waiter = launch {
            secondGate = DownloadConcurrencyLimiter.acquire()
            secondAcquired = true
        }

        // Give the waiter every chance to run while the only permit is held.
        repeat(10) { yield() }
        assertFalse(secondAcquired)

        DownloadConcurrencyLimiter.release(first)
        withTimeout(2_000) { waiter.join() }
        assertTrue(secondAcquired)

        secondGate?.let { DownloadConcurrencyLimiter.release(it) }
        }
    }

    @Test
    fun `configure clamps the permit count to the allowed range`() {
        runBlocking {
        DownloadConcurrencyLimiter.configure(99)
        val upper = DownloadConcurrencyLimiter.acquire()
        assertEquals(3, upper.permits)
        DownloadConcurrencyLimiter.release(upper)

        DownloadConcurrencyLimiter.configure(0)
        val lower = DownloadConcurrencyLimiter.acquire()
        assertEquals(1, lower.permits)
        DownloadConcurrencyLimiter.release(lower)

        DownloadConcurrencyLimiter.configure(2)
        val middle = DownloadConcurrencyLimiter.acquire()
        assertEquals(2, middle.permits)
        DownloadConcurrencyLimiter.release(middle)
        }
    }

    @Test
    fun `reconfiguring does not strand permits of the previous gate`() {
        runBlocking {
        DownloadConcurrencyLimiter.configure(1)
        val oldGate = DownloadConcurrencyLimiter.acquire()

        // A new limit applies to downloads starting afterwards...
        DownloadConcurrencyLimiter.configure(2)

        // ...while the in-flight download still releases the gate it acquired.
        DownloadConcurrencyLimiter.release(oldGate)

        val gateA = DownloadConcurrencyLimiter.acquire()
        val gateB = DownloadConcurrencyLimiter.acquire()
        assertEquals(2, gateA.permits)
        assertEquals(2, gateB.permits)
        DownloadConcurrencyLimiter.release(gateA)
        DownloadConcurrencyLimiter.release(gateB)
        }
    }
}
