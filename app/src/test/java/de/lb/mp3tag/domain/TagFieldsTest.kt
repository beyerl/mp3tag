package de.lb.mp3tag.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFieldsTest {

    @Test
    fun `access is case-insensitive with uppercase canonical keys`() {
        val tags = TagFields.of("artist" to "Kraftwerk")
        assertEquals(listOf("Kraftwerk"), tags["ARTIST"])
        assertEquals(listOf("Kraftwerk"), tags["Artist"])
        assertEquals(setOf("ARTIST"), tags.names)
    }

    @Test
    fun `with replaces values and without removes the field`() {
        val tags = TagFields.of("TITLE" to "One")
            .with("TITLE", listOf("Two"))
        assertEquals(listOf("Two"), tags["TITLE"])
        assertFalse(tags.without("title").has("TITLE"))
    }

    @Test
    fun `setting only empty values removes the field`() {
        val tags = TagFields.of("GENRE" to "Rock").with("GENRE", listOf("", ""))
        assertFalse(tags.has("GENRE"))
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `multi-values join with the display separator`() {
        val tags = TagFields.from(mapOf("ARTIST" to listOf("A", "B")))
        assertEquals("A" + TagFields.DISPLAY_SEPARATOR + "B", tags.joined("ARTIST"))
    }

    @Test
    fun `from filters blank values and drops empty fields`() {
        val tags = TagFields.from(mapOf("A" to listOf("", "x"), "B" to listOf("")))
        assertEquals(listOf("x"), tags["A"])
        assertFalse(tags.has("B"))
    }

    @Test
    fun `canonical trims and uppercases`() {
        assertEquals("ALBUMARTIST", TagFields.canonical(" albumArtist "))
    }

    @Test
    fun `equality is structural`() {
        assertEquals(TagFields.of("A" to "1"), TagFields.of("a" to "1"))
    }
}
