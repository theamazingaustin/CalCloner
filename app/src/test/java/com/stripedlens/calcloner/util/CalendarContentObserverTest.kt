package com.stripedlens.calcloner.util

import com.stripedlens.calcloner.CalendarSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying [CalendarContentObserver] debouncing behavior
 * and self-write echo suppression.
 */
class CalendarContentObserverTest {

    @Test
    fun testDebouncingCoalescesRapidChanges() = runBlocking {
        val triggerCount = AtomicInteger(0)
        val debounceMs = 100L
        val scope = CoroutineScope(Dispatchers.Default)

        val observer = CalendarContentObserver(
            scope = scope,
            debounceDelayMs = debounceMs,
            handler = null
        ) {
            triggerCount.incrementAndGet()
        }

        // Clear lastSelfWriteTimestamp to ensure echo suppression does not block
        CalendarSyncEngine.lastSelfWriteTimestamp = 0L

        // Fire 5 rapid changes within 20ms of each other (well before 100ms debounce expires)
        for (i in 1..5) {
            observer.onChange(false, null)
            delay(10L)
        }

        // Verify that before the debounce window has elapsed, the trigger count is still 0
        assertEquals("Debounce must not fire while events are still incoming", 0, triggerCount.get())

        // Wait for debounce window to complete
        delay(150L)

        assertEquals("Rapid sequential events must be coalesced into a single debounced callback", 1, triggerCount.get())
    }

    @Test
    fun testEchoSuppressionBlocksSelfWrites() = runBlocking {
        val triggerCount = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)

        val observer = CalendarContentObserver(
            scope = scope,
            debounceDelayMs = 50L,
            handler = null
        ) {
            triggerCount.incrementAndGet()
        }

        // Simulate a self-write by CalCloner just 100ms ago
        CalendarSyncEngine.lastSelfWriteTimestamp = System.currentTimeMillis() - 100L

        // Fire change
        observer.onChange(false, null)

        // Verify no debounce job was even created
        assertNull("Self writes within 10s window must be suppressed immediately", observer.debounceJob)

        delay(100L)
        assertEquals("Echo suppression must completely prevent debounced sync trigger", 0, triggerCount.get())
    }

    @Test
    fun testEchoSuppressionExpiresAfterWindow() = runBlocking {
        val triggerCount = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)

        val observer = CalendarContentObserver(
            scope = scope,
            debounceDelayMs = 50L,
            handler = null
        ) {
            triggerCount.incrementAndGet()
        }

        // Simulate a self-write from 15 seconds ago (outside 10s echo window)
        CalendarSyncEngine.lastSelfWriteTimestamp = System.currentTimeMillis() - 15_000L

        observer.onChange(false, null)

        delay(100L)
        assertEquals("Changes occurring after echo window must trigger normally", 1, triggerCount.get())
    }
}
