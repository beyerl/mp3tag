package de.lb.mp3tag.scripting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterParserTest {

    private val file = TestSource(
        "artist" to "Hidden Orchestra",
        "album" to "Night Walks",
        "title" to "Antiphon",
        "_bitrate" to "320",
    )

    private fun matches(filter: String): Boolean = FilterParser.compile(filter)(file)

    @Test
    fun `empty filter matches everything`() {
        assertTrue(matches(""))
        assertTrue(matches("   "))
    }

    @Test
    fun `bare words are substring search across all fields`() {
        assertTrue(matches("orchestra"))
        assertTrue(matches("Hidden Orchestra"))
        assertTrue(matches("hidden walks"))
        assertFalse(matches("hidden nonexistent"))
    }

    @Test
    fun `HAS and IS`() {
        assertTrue(matches("artist HAS Orchestra"))
        assertFalse(matches("artist HAS Symphony"))
        assertTrue(matches("artist IS \"hidden orchestra\""))
        assertFalse(matches("artist IS Hidden"))
    }

    @Test
    fun `MATCHES regex`() {
        assertTrue(matches("title MATCHES ^Anti"))
        assertFalse(matches("title MATCHES ^phon"))
    }

    @Test
    fun `numeric comparisons with placeholder field refs`() {
        assertTrue(matches("\"%_bitrate%\" GREATER 180"))
        assertFalse(matches("\"%_bitrate%\" LESS 180"))
        assertTrue(matches("\"%_bitrate%\" EQUAL 320"))
    }

    @Test
    fun `PRESENT and ABSENT`() {
        assertTrue(matches("album PRESENT"))
        assertTrue(matches("composer ABSENT"))
        assertTrue(matches("composer MISSING"))
        assertFalse(matches("album ABSENT"))
    }

    @Test
    fun `boolean operators and parentheses`() {
        assertTrue(matches("artist HAS Hidden AND album HAS Night"))
        assertFalse(matches("artist HAS Hidden AND album HAS Day"))
        assertTrue(matches("album HAS Day OR album HAS Night"))
        assertTrue(matches("NOT artist HAS Symphony"))
        assertTrue(matches("(album HAS Day OR album HAS Night) AND artist HAS Hidden"))
        assertFalse(matches("NOT (artist HAS Hidden)"))
    }

    @Test
    fun `lowercase keywords are plain search words`() {
        // "has" is not a keyword unless uppercase; bare words search field
        // values, so neither "artist" (a field name) nor "has" is found.
        assertFalse(matches("artist has orchestra"))
        assertTrue(matches("HIDDEN orchestra"))
    }

    @Test
    fun `errors`() {
        assertThrows(ScriptException::class.java) { matches("artist HAS") }
        assertThrows(ScriptException::class.java) { matches("(artist HAS x") }
        assertThrows(ScriptException::class.java) { matches("title MATCHES [unclosed") }
    }
}
