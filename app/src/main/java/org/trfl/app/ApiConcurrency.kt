package org.trfl.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces the start of consecutive operations by at least [intervalMillis].
 * Callers queue on [acquire]; a caller cancelled while waiting does not consume a slot.
 */
class RequestRateGate(
    private val intervalMillis: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var next = 0L
    private var started = false

    suspend fun acquire() {
        mutex.withLock {
            if (started) {
                val wait = next - now()
                if (wait > 0) sleep(wait)
            }
            next = now() + intervalMillis
            started = true
        }
    }
}

/** One [Mutex] per key, created on demand and dropped once no caller holds or awaits it. */
class KeyedMutex<K : Any> {
    private class Entry { val mutex = Mutex(); var users = 0 }
    private val entries = HashMap<K, Entry>()

    suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) { if (--entry.users == 0) entries.remove(key) }
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }
}
