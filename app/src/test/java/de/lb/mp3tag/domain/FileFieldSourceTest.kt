package de.lb.mp3tag.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFieldSourceTest {

    private val loaded = LoadedFile(
        id = 1,
        file = TestFileRef("01 Autobahn.mp3", "Music/Kraftwerk/01 Autobahn.mp3"),
        sizeBytes = 1234,
        lastModified = 0,
        props = AudioProps(lengthMs = 125_000, bitrateKbps = 320, sampleRateHz = 44100, channels = 2),
        tags = TagFields.of("ARTIST" to "Kraftwerk", "TITLE" to "Autobahn"),
    )

    @Test
    fun `tag fields resolve case-insensitively`() {
        val source = FileFieldSource(loaded, null)
        assertEquals(listOf("Kraftwerk"), source.values("artist"))
    }

    @Test
    fun `pending edits overlay the base tags`() {
        val edits = PendingEdits().withField("TITLE", FieldEdit.Set(listOf("Other")))
        val source = FileFieldSource(loaded, edits)
        assertEquals(listOf("Other"), source.values("TITLE"))
    }

    @Test
    fun `technical placeholders`() {
        val source = FileFieldSource(loaded, null)
        assertEquals(listOf("01 Autobahn"), source.values("_filename"))
        assertEquals(listOf("01 Autobahn.mp3"), source.values("_filename_ext"))
        assertEquals(listOf("mp3"), source.values("_extension"))
        assertEquals(listOf("Kraftwerk"), source.values("_directory"))
        assertEquals(listOf("320"), source.values("_bitrate"))
        assertEquals(listOf("2:05"), source.values("_length"))
        assertEquals(listOf("125"), source.values("_length_seconds"))
        assertTrue(source.values("_unknown_tech").isEmpty())
    }

    @Test
    fun `field names include filename for free-text filter search`() {
        val source = FileFieldSource(loaded, null)
        assertTrue("_FILENAME" in source.fieldNames())
        assertTrue("ARTIST" in source.fieldNames())
    }
}
