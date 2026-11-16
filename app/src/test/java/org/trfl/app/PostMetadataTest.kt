package org.trfl.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class PostMetadataTest {
    private fun age(seconds: Long, now: Long) = relativeTime(seconds, now, ZoneOffset.UTC, Locale.US)
    private fun utc(year: Int, month: Int, day: Int) = LocalDateTime.of(year, month, day, 12, 0).toEpochSecond(ZoneOffset.UTC)

    @Test fun relativeTimeBuckets() {
        val now = 1_000_000L
        assertEquals("now", age(now, now))
        assertEquals("now", age(now - 59, now))
        assertEquals("now", age(now + 30, now)) // clock skew
        assertEquals("1m", age(now - 60, now))
        assertEquals("5m", age(now - 5 * 60 - 20, now))
        assertEquals("59m", age(now - 3599, now))
        assertEquals("1h", age(now - 3600, now))
        assertEquals("3h", age(now - 3 * 3600, now))
        assertEquals("23h", age(now - 86399, now))
        assertEquals("1d", age(now - 86400, now))
        assertEquals("6d", age(now - 7 * 86400 + 1, now))
    }

    @Test fun relativeTimeFallsBackToShortDate() {
        val now = utc(2026, 11, 17)
        assertEquals("Nov 3", age(utc(2026, 11, 3), now))
        assertEquals("Jan 5", age(utc(2026, 1, 5), now))
        assertEquals("Dec 24, 2025", age(utc(2025, 12, 24), now))
    }

    @Test fun metaPartsDefaultToAnonymousAndEndWithPostNumber() {
        val post = Post(123, "", "", null, null, 0)
        assertEquals(listOf("Anonymous", "No. 123"), postMetaParts(post, 0))
    }

    @Test fun metaPartsIncludeTripCapcodeIdAndAge() {
        val post = Post(456, "Subject", "", null, null, 0, created = 1000, name = "moot", trip = "!Ep8pui8Vw2",
            capcode = "admin", posterId = "Ab12Cd34")
        assertEquals(listOf("moot !Ep8pui8Vw2 ## Admin", "ID: Ab12Cd34", "2h", "No. 456"), postMetaParts(post, 1000 + 2 * 3600))
        assertTrue(postMetaParts(post, 0).joinToString(" · ").endsWith("No. 456"))
    }

    @Test fun parsePostReadsMetadata() {
        val json = JSONObject("""{"no":42,"sub":"Hi","com":"body","name":"Anon &amp; co","trip":"!!abc","id":"XyZ12345",
            "capcode":"mod","filename":"cat","ext":".jpg","tim":1700000000123,"time":1700000000,"sticky":1,"closed":1,"replies":3}""")
        val post = parsePost(json, "g") { it.replace("&amp;", "&") }
        assertEquals(42L, post.id)
        assertEquals("Anon & co", post.name)
        assertEquals("!!abc", post.trip)
        assertEquals("XyZ12345", post.posterId)
        assertEquals("mod", post.capcode)
        assertEquals("cat.jpg", post.filename)
        assertEquals(1700000000L, post.created)
        assertTrue(post.sticky)
        assertTrue(post.closed)
        assertEquals("https://i.4cdn.org/g/1700000000123.jpg", post.image)
    }

    @Test fun parsePostDefaultsWhenMetadataMissing() {
        val post = parsePost(JSONObject("""{"no":7,"time":5}"""), "g") { it }
        assertEquals("", post.name)
        assertEquals("", post.trip)
        assertEquals("", post.posterId)
        assertEquals("", post.capcode)
        assertEquals("", post.filename)
        assertFalse(post.sticky)
        assertFalse(post.closed)
        assertNull(post.image)
    }

    @Test fun snapshotRoundTripsNewFields() {
        val post = Post(9, "t", "b", "https://i.4cdn.org/g/1.jpg", "https://i.4cdn.org/g/1s.jpg", 2, images = 1, created = 10,
            lastReply = 20, video = true, name = "n", trip = "!t", posterId = "id1", capcode = "mod", filename = "f.webm",
            sticky = true, closed = true)
        val json = JSONObject(postToJson(post).toString())
        assertEquals(post, postFromJson(json, post.image, post.thumb))
    }

    @Test fun oldSnapshotsStillLoad() {
        val old = JSONObject("""{"id":9,"title":"t","body":"b","replies":2,"images":1,"created":10,"lastReply":20}""")
        assertEquals(Post(9, "t", "b", null, null, 2, images = 1, created = 10, lastReply = 20), postFromJson(old, null, null))
    }
}
