package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class PostRepliesTest {
    private fun post(id: Long, body: String) = Post(id, "", body, null, null, 0)
    @Test fun indexesIncomingRepliesAndDeduplicatesRepeatedQuotes() {
        val posts = listOf(post(1, "OP"), post(2, ">>1 hello >>1"), post(3, ">>1 >>2"))
        val index = indexPostReplies(posts)
        assertEquals(listOf(2L, 3L), index[1L]!!.map { it.id })
        assertEquals(listOf(3L), index[2L]!!.map { it.id })
        assertNull(index[3L])
    }
    @Test fun ignoresExternalReferencesSelfReferencesAndOverflow() {
        assertTrue(indexPostReplies(listOf(post(1, ">>1 >>999 >>999999999999999999999999"))).isEmpty())
        assertTrue(indexPostReplies(emptyList()).isEmpty())
    }
}
