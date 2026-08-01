package de.lb.mp3tag.actions

import de.lb.mp3tag.domain.AudioProps
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.domain.TestFileRef
import de.lb.mp3tag.scripting.ScriptException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRunnerTest {

    private fun loaded(vararg tags: Pair<String, String>) = LoadedFile(
        id = 1,
        file = TestFileRef("01 some song.mp3", "Music/01 some song.mp3"),
        sizeBytes = 0,
        lastModified = 0,
        props = AudioProps(60_000, 320, 44100, 2),
        tags = TagFields.of(*tags),
    )

    private fun run(actions: List<Action>, file: LoadedFile): Map<String, String> =
        ActionRunner(actions).run(file, null)

    @Test
    fun `case conversion`() {
        val buffer = run(
            listOf(Action.CaseConversion("TITLE", CaseMode.CAPS)),
            loaded("TITLE" to "back in black"),
        )
        assertEquals(mapOf("TITLE" to "Back In Black"), buffer)
    }

    @Test
    fun `replace is case-insensitive by default`() {
        val buffer = run(
            listOf(Action.Replace("ARTIST", "AND", "&")),
            loaded("ARTIST" to "Simon and Garfunkel"),
        )
        assertEquals(mapOf("ARTIST" to "Simon & Garfunkel"), buffer)
    }

    @Test
    fun `replace with match case`() {
        val buffer = run(
            listOf(Action.Replace("ARTIST", "AND", "&", matchCase = true)),
            loaded("ARTIST" to "Simon and Garfunkel"),
        )
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `regex replace`() {
        val buffer = run(
            listOf(Action.RegexReplace("TITLE", "\\s+", " ")),
            loaded("TITLE" to "too   many    spaces"),
        )
        assertEquals(mapOf("TITLE" to "too many spaces"), buffer)
    }

    @Test
    fun `invalid regex fails at compile time, before any file is touched`() {
        assertThrows(ScriptException::class.java) {
            ActionRunner(listOf(Action.RegexReplace("TITLE", "[unclosed", "")))
        }
    }

    @Test
    fun `format value sees earlier action results and technical fields`() {
        val buffer = run(
            listOf(
                Action.FormatValue("COMMENT", "ripped"),
                Action.FormatValue("TITLE", "\$caps(%comment%) - %_filename%"),
            ),
            loaded("TITLE" to "x"),
        )
        assertEquals("Ripped - 01 some song", buffer["TITLE"])
        assertEquals("ripped", buffer["COMMENT"])
    }

    @Test
    fun `guess values extracts fields from a composed source`() {
        val buffer = run(
            listOf(Action.GuessValues("%_filename%", "%track% %title%")),
            loaded(),
        )
        assertEquals("01", buffer["TRACK"])
        assertEquals("some song", buffer["TITLE"])
    }

    @Test
    fun `remove fields and remove all except`() {
        val file = loaded("TITLE" to "T", "COMMENT" to "junk", "GENRE" to "Rock")
        assertEquals(
            mapOf("COMMENT" to ""),
            run(listOf(Action.RemoveFields(listOf("COMMENT"))), file),
        )
        assertEquals(
            mapOf("COMMENT" to "", "GENRE" to ""),
            run(listOf(Action.RemoveAllExcept(listOf("TITLE"))), file),
        )
    }

    @Test
    fun `split field produces multi-values`() {
        val buffer = run(
            listOf(Action.SplitField("ARTIST", ";")),
            loaded("ARTIST" to "A; B"),
        )
        assertEquals(mapOf("ARTIST" to "A" + TagFields.DISPLAY_SEPARATOR + "B"), buffer)
    }

    @Test
    fun `no-op actions produce an empty buffer`() {
        val buffer = run(
            listOf(Action.CaseConversion("TITLE", CaseMode.UPPER)),
            loaded("TITLE" to "ALREADY UPPER"),
        )
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `actions run in listed order`() {
        val buffer = run(
            listOf(
                Action.Replace("TITLE", "a", "b"),
                Action.CaseConversion("TITLE", CaseMode.UPPER),
            ),
            loaded("TITLE" to "aaa"),
        )
        assertEquals(mapOf("TITLE" to "BBB"), buffer)
    }
}
