package org.trfl.app

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Submission is synchronous; operations continue in order after a composer leaves composition. */
internal class PostingDraftQueue(private val executor: ExecutorService = Executors.newSingleThreadExecutor()) {
    fun enqueue(operation: () -> Unit) { executor.execute(operation) }
    fun shutdown() { executor.shutdown() }
}
