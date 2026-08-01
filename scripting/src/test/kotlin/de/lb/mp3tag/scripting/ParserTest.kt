package de.lb.mp3tag.scripting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParserTest {

    private val source = TestSource("artist" to "Kraftwerk", "title" to "Autobahn")

    @Test
    fun `plain text and placeholders`() {
        assertEquals("Kraftwerk - Autobahn", eval("%artist% - %title%", source))
    }

    @Test
    fun `placeholder names are case-insensitive`() {
        assertEquals("Kraftwerk", eval("%ARTIST%", source))
    }

    @Test
    fun `quoted text is raw, double quote is a literal quote`() {
        assertEquals("%artist%", eval("'%artist%'", source))
        assertEquals("it's", eval("it''s", source))
    }

    @Test
    fun `optional section emits only when a field resolves`() {
        assertEquals(" (Autobahn)", eval("[ (%title%)]", source))
        assertEquals("", eval("[ (%missing%)]", source))
    }

    @Test
    fun `nested optional propagates to the outer section`() {
        // No field resolves anywhere -> the outer section is suppressed entirely.
        assertEquals("", eval("[x[-%title2%]-y[%missing%]]", TestSource("title2" to "")))
        // An inner resolving field keeps both the inner and outer sections.
        assertEquals("x-T-y", eval("[x[-%title%-]y[%missing%]]", TestSource("title" to "T")))
    }

    @Test
    fun `dollar without call syntax is literal`() {
        assertEquals("5\$ price", eval("5\$ price", source))
    }

    @Test
    fun `commas only separate arguments inside calls`() {
        assertEquals("a,b", eval("a,b", source))
        assertEquals("x", eval("\$if(1,x,y)", source))
    }

    @Test
    fun `unterminated constructs throw with position`() {
        assertThrows(ScriptException::class.java) { eval("%artist", source) }
        assertThrows(ScriptException::class.java) { eval("'oops", source) }
        assertThrows(ScriptException::class.java) { eval("[%artist%", source) }
        assertThrows(ScriptException::class.java) { eval("\$upper(x", source) }
    }

    @Test
    fun `unknown function throws`() {
        assertThrows(ScriptException::class.java) { eval("\$nosuchfn(x)", source) }
    }
}
