package de.lb.mp3tag.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchEditTest {

    @Test
    fun `effective overlays set and remove edits`() {
        val tags = TagFields.of("ARTIST" to "Old", "GENRE" to "Rock")
        val edits = PendingEdits()
            .withField("ARTIST", FieldEdit.Set(listOf("New")))
            .withField("GENRE", FieldEdit.Remove)
        val effective = BatchEdit.effective(tags, edits)
        assertEquals(listOf("New"), effective["ARTIST"])
        assertFalse(effective.has("GENRE"))
    }

    @Test
    fun `common returns the agreed value`() {
        val a = TagFields.of("ALBUM" to "X")
        val b = TagFields.of("ALBUM" to "X")
        assertEquals("X", BatchEdit.common(listOf(a, b), "ALBUM"))
    }

    @Test
    fun `common returns null for differing values - rendered as keep`() {
        val a = TagFields.of("TITLE" to "One")
        val b = TagFields.of("TITLE" to "Two")
        assertNull(BatchEdit.common(listOf(a, b), "TITLE"))
    }

    @Test
    fun `common treats absent fields as empty string`() {
        val a = TagFields.EMPTY
        val b = TagFields.EMPTY
        assertEquals("", BatchEdit.common(listOf(a, b), "COMMENT"))
        // One file has a value, the other doesn't -> mixed.
        assertNull(BatchEdit.common(listOf(a, TagFields.of("COMMENT" to "hi")), "COMMENT"))
    }

    @Test
    fun `applyBuffer touches only edited fields`() {
        val base = mapOf(1L to TagFields.of("ARTIST" to "A1", "TITLE" to "T1"))
        val result = BatchEdit.applyBuffer(
            buffer = mapOf("ALBUM" to "New Album"),
            selection = listOf(1L),
            currentEdits = emptyMap(),
            baseTags = base,
        )
        val edits = result.getValue(1L)
        assertEquals(setOf("ALBUM"), edits.fields.keys)
        assertEquals(FieldEdit.Set(listOf("New Album")), edits.fields["ALBUM"])
    }

    @Test
    fun `applyBuffer with empty text removes the field`() {
        val base = mapOf(1L to TagFields.of("COMMENT" to "junk"))
        val result = BatchEdit.applyBuffer(
            buffer = mapOf("COMMENT" to ""),
            selection = listOf(1L),
            currentEdits = emptyMap(),
            baseTags = base,
        )
        assertEquals(FieldEdit.Remove, result.getValue(1L).fields["COMMENT"])
    }

    @Test
    fun `applyBuffer skips no-op edits so files stay clean`() {
        val base = mapOf(1L to TagFields.of("ARTIST" to "Same"))
        val result = BatchEdit.applyBuffer(
            buffer = mapOf("ARTIST" to "Same"),
            selection = listOf(1L),
            currentEdits = emptyMap(),
            baseTags = base,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `applyBuffer clearing back to the base value removes the pending edit`() {
        val base = mapOf(1L to TagFields.of("ARTIST" to "Orig"))
        val withEdit = BatchEdit.applyBuffer(
            buffer = mapOf("ARTIST" to "Changed"),
            selection = listOf(1L),
            currentEdits = emptyMap(),
            baseTags = base,
        )
        assertTrue(withEdit.getValue(1L).isDirty)
        val reverted = BatchEdit.applyBuffer(
            buffer = mapOf("ARTIST" to "Orig"),
            selection = listOf(1L),
            currentEdits = withEdit,
            baseTags = base,
        )
        assertTrue(reverted.isEmpty())
    }

    @Test
    fun `applyBuffer applies one buffer to every selected file`() {
        val base = mapOf(
            1L to TagFields.of("ALBUM" to "A"),
            2L to TagFields.of("ALBUM" to "B"),
        )
        val result = BatchEdit.applyBuffer(
            buffer = mapOf("ALBUM" to "Unified"),
            selection = listOf(1L, 2L),
            currentEdits = emptyMap(),
            baseTags = base,
        )
        assertEquals(FieldEdit.Set(listOf("Unified")), result.getValue(1L).fields["ALBUM"])
        assertEquals(FieldEdit.Set(listOf("Unified")), result.getValue(2L).fields["ALBUM"])
    }

    @Test
    fun `parseValues splits on the display separator and drops blanks`() {
        val values = BatchEdit.parseValues("A" + TagFields.DISPLAY_SEPARATOR + " B " + TagFields.DISPLAY_SEPARATOR)
        assertEquals(listOf("A", "B"), values)
    }

    @Test
    fun `unionOfFields preserves first-seen order`() {
        val a = TagFields.of("TITLE" to "x", "ARTIST" to "y")
        val b = TagFields.of("ALBUM" to "z", "TITLE" to "w")
        assertEquals(listOf("TITLE", "ARTIST", "ALBUM"), BatchEdit.unionOfFields(listOf(a, b)))
    }
}
