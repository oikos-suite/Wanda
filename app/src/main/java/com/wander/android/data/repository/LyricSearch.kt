package com.wander.android.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import com.wander.android.core.database.dao.CatalogRecordingDao
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.data.model.LyricMatch
import com.wander.android.data.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds songs by a line of their lyrics, for the search screen's "in the lyrics" section: the
 * lyrics stored on this device, and the Agro catalogue's for recordings the library lacks.
 */
@Singleton
class LyricSearch @Inject constructor(
    private val trackLyricsDao: TrackLyricsDao,
    private val catalogDao: CatalogRecordingDao
) {
    /**
     * Songs whose lyrics hold [query], from [sources] only — the search screen's chips. Catalogue
     * stubs have no source yet and pass; whoever resolves them holds the copy to [sources] too.
     */
    suspend fun search(
        query: String,
        sources: Set<SourceType>,
        limit: Int = 25
    ): List<LyricMatch> = withContext(Dispatchers.IO) {
        val ftsQuery = ftsQueryFor(query)
        if (ftsQuery.isBlank()) return@withContext emptyList()

        try {
            // Filtered before deduplication, not after: a song held on the device and on Subsonic
            // must still be found on Subsonic when only Subsonic is selected, so the copy that
            // would have won has to be out of the running first.
            val matches = trackLyricsDao.searchTracksByLyrics(ftsQuery, limit * 2)
                .filter { it.track.source in sources }
                .map { res ->
                    val (line, ts) = findMatchingLine(res.plainLyrics, res.syncedLyrics, query)
                    LyricMatch(
                        track = res.track.toUnifiedTrack(),
                        matchedLine = line,
                        snippet = res.snippet,
                        timestampMs = ts,
                        source = "LyricsFTS"
                    )
                }
            // Recordings the catalogue knows and the library lacks. Their tracks are stubs, which
            // the caller resolves; sorting by priority puts them behind any copy that is held.
            val fromCatalogue = catalogDao.searchByLyrics(ftsQuery, limit).map { hit ->
                val recording = hit.recording
                val (line, ts) = findMatchingLine(recording.plainLyrics.orEmpty(), recording.syncedLyrics, query)
                LyricMatch(
                    track = hit.recording.toStubTrack(),
                    matchedLine = line,
                    snippet = hit.snippet,
                    timestampMs = ts,
                    source = "Agro"
                )
            }
            // Deduplicate across sources (Local > Navidrome > YTM) using canonical recordingKey
            val seenKeys = mutableSetOf<String>()
            val deduplicated = mutableListOf<LyricMatch>()
            for (match in (matches + fromCatalogue).sortedBy { it.track.source.priority }) {
                val key = TrackDeduplicator.recordingKey(match.track)
                if (seenKeys.add(key)) {
                    deduplicated.add(match)
                }
                if (deduplicated.size >= limit) break
            }
            deduplicated
        } catch (e: SQLiteException) {
            Log.w(TAG, "Lyric search query failed: ${e.message}")
            emptyList()
        }
    }

    private fun ftsQueryFor(query: String): String {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.mapIndexed { idx, term ->
            val clean = term.replace(Regex("[^\\p{L}\\p{Nd}]"), "")
            if (idx == words.size - 1) "$clean*" else clean
        }.filter { it.isNotBlank() && it != "*" }.joinToString(" ")
    }

    private companion object {
        const val TAG = "LyricSearch"
    }
}
