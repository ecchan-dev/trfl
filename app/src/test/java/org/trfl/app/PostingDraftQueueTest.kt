package org.trfl.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic drafts only; exercises navigation-like save/load ordering without Android. */
class PostingDraftQueueTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun syntheticQueuedEditsSurviveComposerDisposalAndPrecedeReload() {
        val store = PostingDraftStore(folder.newFolder())
        val key = PostingDraftKey("g", 123)
        val queue = PostingDraftQueue()
        val done = CountDownLatch(1)
        var loaded: PostingDraft? = null
        try {
            queue.enqueue { store.save(key, "first", "", null) }
            queue.enqueue { store.save(key, "last", "", null) }
            // Simulate leaving and reopening the composer immediately.
            queue.enqueue { loaded = store.load(key); done.countDown() }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("last", loaded?.text)
        } finally { queue.shutdown() }
    }
}
