package de.lb.mp3tag.scripting

import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionsTest {

    private val source = TestSource(
        "artist" to "AC/DC",
        "title" to "back in black",
        "track" to "3",
        "year" to "1980",
    )

    @Test
    fun `conditionals`() {
        assertEquals("yes", eval("\$if(\$eql(%track%,3),yes,no)", source))
        assertEquals("no", eval("\$if(\$eql(%track%,4),yes,no)", source))
        assertEquals("fallback", eval("\$if2(%missing%,fallback)", source))
        assertEquals("3", eval("\$if2(%track%,fallback)", source))
        assertEquals("big", eval("\$if(\$grtr(%year%,1979),big,small)", source))
        assertEquals("1", eval("\$and(1,x)", source))
        assertEquals("", eval("\$and(1,)", source))
        assertEquals("1", eval("\$or(,x)", source))
        assertEquals("1", eval("\$not(0)", source))
        assertEquals("1", eval("\$odd(3)", source))
        assertEquals("", eval("\$odd(4)", source))
        assertEquals("1", eval("\$isdigit(123)", source))
        assertEquals("", eval("\$isdigit(12a)", source))
        assertEquals("long", eval("\$iflonger(hello,3,long,short)", source))
        assertEquals("more", eval("\$ifgreater(10,9,more,less)", source))
        assertEquals("1", eval("\$stricmp(ABC,abc)", source))
        assertEquals("", eval("\$strcmp(ABC,abc)", source))
    }

    @Test
    fun `case conversion`() {
        assertEquals("BACK IN BLACK", eval("\$upper(%title%)", source))
        assertEquals("ac/dc", eval("\$lower(%artist%)", source))
        assertEquals("Back In Black", eval("\$caps(%title%)", source))
        // $caps starts a new word after any non-letter; $caps3 only after whitespace.
        assertEquals("Ac/Dc", eval("\$caps(%artist%)", source))
        assertEquals("Ac/dc", eval("\$caps3(%artist%)", source))
        // $caps2 keeps the rest of each word untouched.
        assertEquals("McFLY", eval("\$caps2(mcFLY)", source))
    }

    @Test
    fun `substrings and length`() {
        assertEquals("back", eval("\$left(%title%,4)", source))
        assertEquals("black", eval("\$right(%title%,5)", source))
        assertEquals("ack", eval("\$mid(%title%,2,3)", source))
        assertEquals("in black", eval("\$mid(%title%,6)", source))
        assertEquals("13", eval("\$len(%title%)", source))
        assertEquals("ck in black", eval("\$cutleft(%title%,2)", source))
        assertEquals("back in", eval("\$cutright(%title%,6)", source))
    }

    @Test
    fun `trim num repeat reverse`() {
        assertEquals("x", eval("\$trim( x )", source))
        assertEquals("x ", eval("\$trimleft( x )", source))
        assertEquals("ab", eval("\$trim(--ab--,-)", source))
        assertEquals("003", eval("\$num(%track%,3)", source))
        assertEquals("012", eval("\$num(12abc,3)", source))
        assertEquals("0", eval("\$num(abc,1)", source))
        assertEquals("ababab", eval("\$repeat(ab,3)", source))
        assertEquals("cba", eval("\$reverse(abc)", source))
    }

    @Test
    fun `search and replace`() {
        assertEquals("back_in_black", eval("\$replace(%title%, ,_)", source))
        assertEquals("xyx", eval("\$replace(aya,a,x)", source))
        assertEquals("b1ck in bl1ck", eval("\$regexp(%title%,a,1)", source))
        // Parentheses inside arguments must be quoted, as in desktop Mp3tag.
        assertEquals("in", eval("\$regexp(%title%,'^back (in) black\$','\$1')", source))
        assertEquals("5", eval("\$strstr(%title%, in)", source))
        assertEquals("0", eval("\$strstr(%title%,zzz)", source))
        assertEquals("1", eval("\$strchr(%title%,b)", source))
        assertEquals("9", eval("\$strrchr(%title%,b)", source))
        assertEquals("3", eval("\$distance(kitten,sitting)", source))
        assertEquals("97", eval("\$ord(abc)", source))
        assertEquals("A", eval("\$char(65)", source))
        assertEquals("AC_DC", eval("\$validate(\$upper(%artist%),_)", source))
        assertEquals("1,234,567", eval("\$fmtnum(1234567)", source))
    }

    @Test
    fun `arithmetic`() {
        assertEquals("6", eval("\$add(1,2,3)", source))
        assertEquals("4", eval("\$sub(10,5,1)", source))
        assertEquals("24", eval("\$mul(2,3,4)", source))
        assertEquals("3", eval("\$div(10,3)", source))
        assertEquals("0", eval("\$div(10,0)", source))
        assertEquals("1", eval("\$mod(10,3)", source))
        assertEquals("36", eval("\$len(\$uuid())", source))
    }

    @Test
    fun `meta multi-values`() {
        val source = TestSource.multi("artist" to listOf("A", "B", "C"))
        assertEquals("A", eval("%artist%", source))
        assertEquals("A, B, C", eval("\$meta(artist)", source))
        assertEquals("B", eval("\$meta(artist,1)", source))
        assertEquals("", eval("\$meta(artist,9)", source))
        assertEquals("A; B; C", eval("\$meta_sep(artist,; )", source))
    }

    @Test
    fun `variables`() {
        assertEquals("xx", eval("\$puts(v,x)\$get(v)\$get(v)", source))
        assertEquals("yy", eval("\$put(v,y)\$get(v)", source))
        assertEquals("", eval("\$get(undefined)", source))
    }

    @Test
    fun `lazy evaluation - untaken branch is not evaluated`() {
        // The untaken branch contains an unknown function; lazy args must not touch it.
        assertEquals("ok", eval("\$if(1,ok,\$nosuchfn(x))", source))
    }
}
