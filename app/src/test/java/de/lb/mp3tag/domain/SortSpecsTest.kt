package de.lb.mp3tag.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SortSpecsTest {

    @Test
    fun `leadingNumber parses plain, padded and slashed values`() {
        assertEquals(3, SortSpecs.leadingNumber("3"))
        assertEquals(3, SortSpecs.leadingNumber("03"))
        assertEquals(3, SortSpecs.leadingNumber("3/12"))
        assertEquals(Int.MAX_VALUE, SortSpecs.leadingNumber(""))
        assertEquals(Int.MAX_VALUE, SortSpecs.leadingNumber(null))
        assertEquals(Int.MAX_VALUE, SortSpecs.leadingNumber("x3"))
    }

    @Test
    fun `album sort orders by album, disc, then track`() {
        fun file(id: Long, album: String, disc: String, track: String) = LoadedFile(
            id = id,
            file = TestFileRef("$id.mp3", "$id.mp3"),
            sizeBytes = 0,
            lastModified = 0,
            props = null,
            tags = TagFields.of("ALBUM" to album, "DISCNUMBER" to disc, "TRACKNUMBER" to track),
        )

        val files = listOf(
            file(1, "B", "1", "1"),
            file(2, "A", "2", "1"),
            file(3, "A", "1", "10"),
            file(4, "A", "1", "2"),
        )
        val sorted = files.sortedWith(SortSpecs.comparator(SortSpec(SortKey.ALBUM)))
        assertEquals(listOf(4L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `descending reverses the order`() {
        fun file(id: Long, name: String) = LoadedFile(
            id = id,
            file = TestFileRef(name, name),
            sizeBytes = 0,
            lastModified = 0,
            props = null,
            tags = TagFields.EMPTY,
        )

        val files = listOf(file(1, "a.mp3"), file(2, "b.mp3"))
        val sorted = files.sortedWith(
            SortSpecs.comparator(SortSpec(SortKey.FILENAME, ascending = false)),
        )
        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }
}
