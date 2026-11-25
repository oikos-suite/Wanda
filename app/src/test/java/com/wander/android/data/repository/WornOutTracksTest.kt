package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class WornOutTracksTest {

    private fun track(id: String, source: SourceType, title: String) =
        UnifiedTrack(id = id, source = source, title = title, artist = "Artist", durationMs = 200_000L)

    private val skippedOnYouTube = track("ytm:1", SourceType.YTMUSIC, "Overplayed")
    private val sameOnSubsonic = track("navidrome:1", SourceType.NAVIDROME, "Overplayed")
    private val fresh = track("navidrome:2", SourceType.NAVIDROME, "Fresh")

    @Test
    fun `a worn out song is left out on every source that has it`() {
        val station = listOf(skippedOnYouTube, sameOnSubsonic, fresh)
        assertEquals(listOf(fresh), station.withoutWornOut(listOf(skippedOnYouTube)))
    }

    @Test
    fun `with nothing worn out the station is unchanged`() {
        val station = listOf(skippedOnYouTube, fresh)
        assertEquals(station, station.withoutWornOut(emptyList()))
    }
}
