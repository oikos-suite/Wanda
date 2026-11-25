package com.wander.android.data.repository

import com.wander.android.core.database.dao.CatalogLyricHit
import com.wander.android.core.database.dao.CatalogRecordingDao
import com.wander.android.core.database.dao.CatalogVector
import com.wander.android.core.database.dao.Centroid
import com.wander.android.core.database.dao.Evictable
import com.wander.android.core.database.dao.LyricSearchResult
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.CatalogRecordingEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSearchTest {

    private fun track(id: String, source: SourceType) = TrackEntity(
        id = id, sourceTrackId = id, source = source, title = "Same Song", artist = "Same Artist", album = null,
        albumId = null, artistId = null, durationMs = 180_000L, artworkUrl = null, streamUri = null,
        trackNumber = null, discNumber = null, year = null, genre = null, bitRateKbps = null, format = null,
        isLive = false, isDownloaded = false, isLibrary = true, localFilePath = null, addedTimestamp = 0L
    )

    private fun hit(track: TrackEntity) =
        LyricSearchResult(track, plainLyrics = "under the bridge", syncedLyrics = null, snippet = "under the bridge")

    /** The same recording on the device, on Subsonic and on YouTube Music. */
    private val held = listOf(
        hit(track("local:1", SourceType.LOCAL)),
        hit(track("navidrome:1", SourceType.NAVIDROME)),
        hit(track("ytm:1", SourceType.YTMUSIC))
    )

    private val search = LyricSearch(Lyrics(held), NoCatalogue)

    @Test
    fun `Subsonic only finds the Subsonic copy, though the device copy would win a free choice`() = runBlocking {
        val found = search.search("bridge", setOf(SourceType.NAVIDROME))
        assertEquals(listOf(SourceType.NAVIDROME), found.map { it.track.source })
    }

    @Test
    fun `a source that is not selected is never shown`() = runBlocking {
        val found = search.search("bridge", setOf(SourceType.NAVIDROME, SourceType.LOCAL))
        assertEquals(listOf(SourceType.LOCAL), found.map { it.track.source })
    }

    @Test
    fun `with every source selected the best copy is kept, once`() = runBlocking {
        val found = search.search("bridge", SourceType.entries.toSet())
        assertEquals(1, found.size)
    }

    private class Lyrics(private val results: List<LyricSearchResult>) : TrackLyricsDao {
        override suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity? = null
        override suspend fun findLyricsForTrackOrMetadata(
            trackId: String,
            title: String,
            artist: String,
            durationMs: Long?
        ): TrackLyricsEntity? = null
        override suspend fun insertLyrics(lyrics: TrackLyricsEntity) = Unit
        override suspend fun insertFts(trackId: String, plainLyrics: String) = Unit
        override suspend fun deleteFts(trackId: String) = Unit
        override suspend fun deleteLyrics(trackId: String) = Unit
        override suspend fun searchTracksByLyrics(query: String, limit: Int): List<LyricSearchResult> = results
        override suspend fun countLyrics(): Int = results.size
        override fun countFromCatalogueFlow(): Flow<Int> = flowOf(0)
        override suspend fun trackIdsNamed(title: String, artist: String): List<String> = emptyList()
    }

    private object NoCatalogue : CatalogRecordingDao {
        override suspend fun insert(recording: CatalogRecordingEntity) = Unit
        override suspend fun insertFts(recordingId: String, plainLyrics: String) = Unit
        override suspend fun deleteFts(recordingId: String) = Unit
        override suspend fun byId(recordingId: String): CatalogRecordingEntity? = null
        override suspend fun centroids(model: String, version: Int): List<Centroid> = emptyList()
        override fun countFlow(model: String, version: Int): Flow<Int> = flowOf(0)
        override suspend fun vectors(ids: List<String>, model: String, version: Int): List<CatalogVector> = emptyList()
        override suspend fun searchByLyrics(query: String, limit: Int): List<CatalogLyricHit> = emptyList()
        override suspend fun touch(recordingId: String, at: Long) = Unit
        override suspend fun vectorBytes(): Long = 0L
        override suspend fun evictionOrder(limit: Int): List<Evictable> = emptyList()
        override suspend fun dropFingerprints(ids: List<String>) = Unit
    }
}
