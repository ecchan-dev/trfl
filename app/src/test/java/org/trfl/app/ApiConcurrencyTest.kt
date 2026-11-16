package org.trfl.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ApiConcurrencyTest {
    private class FakeClock {
        var time = 5_000L
        val sleeps = mutableListOf<Long>()
        fun gate() = RequestRateGate(1000L, { time }, { sleeps += it; time += it })
    }

    @Test fun firstRequestStartsImmediately() = runBlocking {
        val clock = FakeClock(); val gate = clock.gate()
        gate.acquire()
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    @Test fun consecutiveStartsAreSpacedByTheInterval() = runBlocking {
        val clock = FakeClock(); val gate = clock.gate()
        gate.acquire()
        clock.time += 300
        gate.acquire()
        gate.acquire()
        assertEquals(listOf(700L, 1000L), clock.sleeps)
    }

    @Test fun idleGapLongerThanIntervalDoesNotWait() = runBlocking {
        val clock = FakeClock(); val gate = clock.gate()
        gate.acquire()
        clock.time += 2500
        gate.acquire()
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    @Test fun cancelledWaiterDoesNotConsumeASlot() = runBlocking {
        var time = 0L
        val blocked = CompletableDeferred<Unit>()
        val sleeps = mutableListOf<Long>()
        val gate = RequestRateGate(1000L, { time }, { sleeps += it; blocked.await(); time += it })
        gate.acquire()
        val waiter = launch { gate.acquire() }
        yield()
        assertEquals(listOf(1000L), sleeps)
        waiter.cancel(); waiter.join()
        time = 400
        blocked.complete(Unit)
        gate.acquire()
        // The cancelled waiter never started, so only the original slot (ending at 1000) applies.
        assertEquals(listOf(1000L, 600L), sleeps)
    }

    @Test fun realDelaySpacesConcurrentStarts() = runBlocking {
        val gate = RequestRateGate(40L)
        val begin = System.nanoTime()
        (1..3).map { async(Dispatchers.Default) { gate.acquire() } }.awaitAll()
        // The third start cannot happen before two full intervals (allowing for millisecond truncation).
        assertTrue((System.nanoTime() - begin) / 1_000_000 >= 78)
    }

    @Test fun keyedMutexSerialisesSameKeyAndCleansUp() = runBlocking {
        val locks = KeyedMutex<String>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch { locks.withLock("a") { order += "a1 start"; release.await(); order += "a1 end" } }
        yield()
        val second = launch { locks.withLock("a") { order += "a2" } }
        val other = launch { locks.withLock("b") { order += "b" } }
        yield()
        other.join()
        assertEquals(listOf("a1 start", "b"), order)
        assertEquals(1, locks.size())
        release.complete(Unit)
        first.join(); second.join()
        assertEquals(listOf("a1 start", "b", "a1 end", "a2"), order)
        assertEquals(0, locks.size())
    }

    @Test fun keyedMutexReleasesOnFailure() = runBlocking {
        val locks = KeyedMutex<String>()
        assertTrue(runCatching { locks.withLock<Int>("a") { error("boom") } }.isFailure)
        assertEquals(0, locks.size())
        assertEquals(1, locks.withLock("a") { 1 })
    }
}
