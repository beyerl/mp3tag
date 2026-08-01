package de.lb.mp3tag.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExportTemplateTest {

    private fun file(artist: String, album: String, title: String, seconds: Long = 60) =
        TestSource.multi(
            "artist" to listOf(artist),
            "album" to listOf(album),
            "title" to listOf(title),
            "_length_seconds" to listOf(seconds.toString()),
        )

    private val files = listOf(
        file("Beta", "B-Album", "Two", 100),
        file("Alpha", "A-Album", "One", 50),
    )

    @Test
    fun `loop body renders per file, sorted by the loop expression`() {
        val out = ExportTemplate("\$loop(%artist%)%artist% - %title%\n\$loopend()").render(files)
        assertEquals("Alpha - One\nBeta - Two\n", out)
    }

    @Test
    fun `header and footer render once with totals`() {
        val out = ExportTemplate(
            "Files: %_total_files% (%_total_time%)\n\$loop(%artist%)%title%\n\$loopend()End",
        ).render(files)
        assertEquals("Files: 2 (2:30)\nOne\nTwo\nEnd", out)
    }

    @Test
    fun `counter is one-based within the loop`() {
        val out = ExportTemplate("\$loop(%artist%)%_counter%. %title%\n\$loopend()").render(files)
        assertEquals("1. One\n2. Two\n", out)
    }

    @Test
    fun `puts and get persist across the whole render`() {
        val out = ExportTemplate(
            "\$puts(sep,;)\$loop(%artist%)%artist%\$get(sep)\$loopend()",
        ).render(files)
        assertEquals("Alpha;Beta;", out)
    }

    @Test
    fun `loop limit caps the number of files`() {
        val out = ExportTemplate("\$loop(%artist%,1)%artist%\n\$loopend()").render(files)
        assertEquals("Alpha\n", out)
    }

    @Test
    fun `nested loop groups by the outer value`() {
        val grouped = listOf(
            file("Alpha", "A1", "S1"),
            file("Alpha", "A1", "S2"),
            file("Beta", "B1", "S3"),
        )
        val out = ExportTemplate(
            "\$loop(%artist%)# %artist%\n\$loop(%title%)- %title%\n\$loopend()\$loopend()",
        ).render(grouped)
        assertEquals("# Alpha\n- S1\n- S2\n# Beta\n- S3\n", out)
    }

    @Test
    fun `unbalanced loops are rejected`() {
        assertThrows(ScriptException::class.java) { ExportTemplate("\$loop(%a%)x") }
        assertThrows(ScriptException::class.java) { ExportTemplate("x\$loopend()") }
    }

    @Test
    fun `empty file list renders header only`() {
        val out = ExportTemplate("Header\n\$loop(%artist%)%title%\$loopend()").render(emptyList())
        assertEquals("Header\n", out)
    }
}
