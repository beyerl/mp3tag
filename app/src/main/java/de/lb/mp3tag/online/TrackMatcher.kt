package de.lb.mp3tag.online

import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.SortSpecs

/**
 * Pairs local files with release tracks: first by the files' existing track
 * (and disc) numbers where they match uniquely, then remaining files in
 * list order against remaining tracks in release order.
 */
object TrackMatcher {

    fun autoMatch(files: List<LoadedFile>, tracks: List<ReleaseTrack>): Map<Long, ReleaseTrack> {
        val result = LinkedHashMap<Long, ReleaseTrack>()
        val unmatchedTracks = tracks.toMutableList()
        val unmatchedFiles = files.toMutableList()

        // Pass 1: unique track-number (+ disc, when the file has one) matches.
        val byNumber = files.mapNotNull { file ->
            val trackNo = SortSpecs.leadingNumber(file.tags.first("TRACKNUMBER"))
                .takeIf { it != Int.MAX_VALUE } ?: return@mapNotNull null
            val discNo = SortSpecs.leadingNumber(file.tags.first("DISCNUMBER"))
                .takeIf { it != Int.MAX_VALUE }
            val candidates = unmatchedTracks.filter { track ->
                track.position == trackNo && (discNo == null || track.disc == discNo)
            }
            candidates.singleOrNull()?.let { file to it }
        }
        for ((file, track) in byNumber) {
            if (track in unmatchedTracks) {
                result[file.id] = track
                unmatchedTracks.remove(track)
                unmatchedFiles.remove(file)
            }
        }

        // Pass 2: remaining files in order against remaining tracks in order.
        for ((file, track) in unmatchedFiles.zip(unmatchedTracks)) {
            result[file.id] = track
        }
        return result
    }
}
