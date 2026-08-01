package de.lb.mp3tag.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternMatcherTest {

    @Test
    fun `basic artist - title split`() {
        val result = PatternMatcher("%artist% - %title%").match("Kraftwerk - Autobahn")
        assertEquals(mapOf("ARTIST" to "Kraftwerk", "TITLE" to "Autobahn"), result)
    }

    @Test
    fun `lazy fields - extra separator goes to the trailing field`() {
        val result = PatternMatcher("%track% - %title%").match("01 - Foo - Bar")
        assertEquals(mapOf("TRACK" to "01", "TITLE" to "Foo - Bar"), result)
    }

    @Test
    fun `directory components map to fields`() {
        val result = PatternMatcher("%artist%/%album%/%track% %title%")
            .match("Kraftwerk/Autobahn/01 Autobahn")
        assertEquals(
            mapOf("ARTIST" to "Kraftwerk", "ALBUM" to "Autobahn", "TRACK" to "01", "TITLE" to "Autobahn"),
            result,
        )
    }

    @Test
    fun `pattern separators match both slash styles`() {
        val result = PatternMatcher("%artist%\\%title%").match("A/B")
        assertEquals(mapOf("ARTIST" to "A", "TITLE" to "B"), result)
    }

    @Test
    fun `dummy captures and discards`() {
        val result = PatternMatcher("%dummy% - %title%").match("01 - Song")
        assertEquals(mapOf("TITLE" to "Song"), result)
    }

    @Test
    fun `fields never span directory separators`() {
        assertNull(PatternMatcher("%artist% - %title%").match("A - B/C"))
    }

    @Test
    fun `no match returns null`() {
        assertNull(PatternMatcher("%track%. %title%").match("no dot separator here"))
    }

    @Test
    fun `values are trimmed`() {
        val result = PatternMatcher("%track%-%title%").match("01 - Song")
        assertEquals(mapOf("TRACK" to "01", "TITLE" to "Song"), result)
    }

    @Test
    fun `function calls are rejected in matching patterns`() {
        assertThrows(ScriptException::class.java) {
            PatternMatcher("\$upper(%artist%) - %title%")
        }
    }

    @Test
    fun `pattern without placeholders is rejected`() {
        assertThrows(ScriptException::class.java) { PatternMatcher("plain text") }
    }
}
