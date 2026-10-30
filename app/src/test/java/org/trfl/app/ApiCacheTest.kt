package org.trfl.app

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ApiCacheTest {
    @Test fun persistentResponsesRetainValidators() {
        val dir = Files.createTempDirectory("trfl-cache").toFile()
        try {
            val entry = CachedResponse("{\"boards\":[]}", 1234, "etag", "last-modified")
            ApiCacheStore(dir).write("boards.json", entry)
            assertEquals(entry, ApiCacheStore(dir).read("boards.json"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun lifetimesFavorBoardsWithoutKeepingThreadsIndefinitely() {
        val entry = CachedResponse("[]", 1000)
        assertTrue(entry.fresh("boards.json", 1000 + 60 * 60 * 1000L))
        assertFalse(entry.fresh("g/catalog.json", 1000 + 60000))
        assertTrue(entry.fresh("g/thread/42.json", 1000 + 29999))
        assertFalse(entry.fresh("g/thread/42.json", 1000 + 30000))
        assertFalse(entry.fresh("boards.json", 999))
    }
    @Test fun corruptDataIsDiscarded() {
        val dir = Files.createTempDirectory("trfl-cache").toFile()
        try {
            val store = ApiCacheStore(dir)
            store.write("boards.json", CachedResponse("[]", 0))
            dir.listFiles()!!.single().writeText("broken")
            assertNull(store.read("boards.json"))
            assertEquals(0, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun boundedStorageEvictsOldEntriesAndExplicitRemovalWorks() {
        val dir = Files.createTempDirectory("trfl-cache").toFile()
        try {
            val store = ApiCacheStore(dir, maxBytes = 120)
            store.write("g/catalog.json", CachedResponse("a".repeat(70), 0))
            dir.listFiles()!!.forEach { it.setLastModified(1) }
            store.write("boards.json", CachedResponse("b".repeat(70), 1))
            assertNull(store.read("g/catalog.json"))
            assertNotNull(store.read("boards.json"))
            store.remove("boards.json")
            assertNull(store.read("boards.json"))
        } finally { dir.deleteRecursively() }
    }
}
