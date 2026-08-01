package de.lb.mp3tag.online

import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.domain.TestFileRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatcherTest {

    private fun file(id: Long, track: String? = null, disc: String? = null): LoadedFile {
        val tags = buildMap {
            track?.let { put("TRACKNUMBER", listOf(it)) }
            disc?.let { put("DISCNUMBER", listOf(it)) }
        }
        return LoadedFile(
            id = id,
            file = TestFileRef("$id.mp3", "M/$id.mp3"),
            sizeBytes = 0,
            lastModified = 0,
            props = null,
            tags = TagFields.from(tags),
        )
    }

    private fun track(disc: Int, position: Int, title: String) =
        ReleaseTrack(disc, position, title, null, null)

    @Test
    fun `matches by existing track numbers`() {
        val tracks = listOf(track(1, 1, "One"), track(1, 2, "Two"), track(1, 3, "Three"))
        val files = listOf(file(10, track = "3"), file(11, track = "1"))
        val matched = TrackMatcher.autoMatch(files, tracks)
        assertEquals("Three", matched[10L]?.title)
        assertEquals("One", matched[11L]?.title)
    }

    @Test
    fun `disc numbers disambiguate duplicate positions`() {
        val tracks = listOf(track(1, 1, "D1T1"), track(2, 1, "D2T1"))
        val files = listOf(file(10, track = "1", disc = "2"))
        assertEquals("D2T1", TrackMatcher.autoMatch(files, tracks)[10L]?.title)
    }

    @Test
    fun `files without numbers fall back to order`() {
        val tracks = listOf(track(1, 1, "One"), track(1, 2, "Two"))
        val files = listOf(file(10), file(11))
        val matched = TrackMatcher.autoMatch(files, tracks)
        assertEquals("One", matched[10L]?.title)
        assertEquals("Two", matched[11L]?.title)
    }

    @Test
    fun `mixed - numbered files match first, the rest fill remaining slots in order`() {
        val tracks = listOf(track(1, 1, "One"), track(1, 2, "Two"), track(1, 3, "Three"))
        val files = listOf(file(10), file(11, track = "2"), file(12))
        val matched = TrackMatcher.autoMatch(files, tracks)
        assertEquals("Two", matched[11L]?.title)
        assertEquals("One", matched[10L]?.title)
        assertEquals("Three", matched[12L]?.title)
    }

    @Test
    fun `extra files beyond the track list stay unmatched`() {
        val tracks = listOf(track(1, 1, "One"))
        val files = listOf(file(10, track = "1"), file(11))
        val matched = TrackMatcher.autoMatch(files, tracks)
        assertEquals("One", matched[10L]?.title)
        assertNull(matched[11L])
    }
}
