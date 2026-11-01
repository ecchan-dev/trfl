package org.trfl.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic local fixtures only; no network or platform URI access. */
class PostingDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun syntheticRecoveryAndKeyIsolation() {
        val dir = folder.newFolder()
        val store = PostingDraftStore(dir)
        val reply = PostingDraftKey("g", 123)
        val fresh = PostingDraftKey("g", null)
        val attachment = PostingDraftAttachment("content://synthetic.provider/document/1", "example.png", "image/png", 42)
        val saved = store.save(reply, "hello", "subject", attachment)
        store.save(fresh, "new thread", "", null)
        val recovered = PostingDraftStore(dir)
        assertEquals(saved, recovered.load(reply))
        assertEquals("new thread", recovered.load(fresh)?.text)
        assertNull(recovered.load(PostingDraftKey("x", 123)))
    }

    @Test fun syntheticConfirmedRevisionDoesNotClearNewerEdit() {
        val dir = folder.newFolder()
        val first = PostingDraftStore(dir)
        val second = PostingDraftStore(dir)
        val key = PostingDraftKey("g", 1)
        val submitted = first.save(key, "old", "", null)
        val newer = second.save(key, "new", "", null)
        assertFalse(first.clearIfRevision(key, submitted.revision))
        assertEquals(newer, first.load(key))
        assertTrue(first.clearIfRevision(key, newer.revision))
        assertNull(second.load(key))
    }

    @Test fun syntheticBoundsAndUnsafeKeysRejected() {
        assertThrows(IllegalArgumentException::class.java) { PostingDraftKey("../x", null) }
        assertThrows(IllegalArgumentException::class.java) { PostingDraftKey("g", 0) }
        assertThrows(IllegalArgumentException::class.java) { PostingDraftAttachment("file:///tmp/x", null, null, null) }
        val store = PostingDraftStore(folder.newFolder())
        val key = PostingDraftKey("g", null)
        val saved = store.save(key, "safe", "", null)
        assertThrows(IllegalArgumentException::class.java) { store.save(key, "x".repeat(PostingDraftStore.MAX_TEXT_BYTES + 1), "", null) }
        assertEquals(saved, store.load(key))
    }

    @Test fun syntheticCorruptFileIsNotDeleted() {
        val dir = folder.newFolder()
        val store = PostingDraftStore(dir)
        val key = PostingDraftKey("g", 42)
        store.save(key, "test", "", null)
        val file = dir.listFiles()!!.single()
        file.writeBytes(byteArrayOf(0, 1))
        assertThrows(IllegalStateException::class.java) { PostingDraftStore(dir).load(key) }
        assertThrows(IllegalStateException::class.java) { store.save(key, "replacement", "", null) }
        assertTrue(file.exists())
        assertFalse(store.clearIfRevision(key, "anything"))
    }
}
