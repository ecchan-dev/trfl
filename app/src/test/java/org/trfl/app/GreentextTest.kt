package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GreentextTest {
    private fun green(body: String) = greentextRanges(body).map { body.substring(it.first, it.last + 1) }

    @Test fun marksImplyingLines() {
        assertEquals(listOf(">be me", ">be anon"), green(">be me\nnormal line\n>be anon"))
    }

    @Test fun skipsQuoteLinksAndCrossBoardLinks() {
        assertEquals(emptyList<String>(), green(">>123 reply\n>>>/g/ board\n>>>/g/456"))
        assertEquals(listOf(">>not a quote", ">>>thing"), green(">>not a quote\n>>>thing"))
    }

    @Test fun allowsLeadingWhitespaceAndIgnoresMidLineArrows() {
        val body = "  >indented\na > b\n>"
        assertEquals(listOf(">indented", ">"), green(body))
        assertEquals(2, greentextRanges(body).first().first)
    }

    @Test fun handlesEmptyAndTrailingNewlines() {
        assertEquals(emptyList<String>(), green(""))
        assertEquals(listOf(">a"), green(">a\n"))
        assertEquals(listOf(">a", ">b"), green(">a\n\n>b\n"))
    }
}
