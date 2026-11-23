package com.wander.android.core.playback

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LiveNowPlayingTest {

    private val station = UnifiedTrack(
        id = "navidrome:radio:7",
        source = SourceType.NAVIDROME,
        title = "Big N",
        artist = "Internet radio",
        isLive = true
    )

    @Test
    fun streamTitleSplitsOnTheFirstSeparator() {
        assertEquals(StreamTitle("Daft Punk", "One More Time - Radio Edit"),
            parseStreamTitle("Daft Punk - One More Time - Radio Edit"))
    }

    @Test
    fun streamTitleWithoutAnArtistIsAllTitle() {
        assertEquals(StreamTitle(null, "Big N - "), parseStreamTitle(" Big N - "))
        assertEquals(StreamTitle(null, "The best hits"), parseStreamTitle("The best hits"))
    }

    @Test
    fun blankStreamTitleIsNothing() {
        assertNull(parseStreamTitle("   "))
    }

    @Test
    fun theSongReplacesTheStationAndTheStationMovesToTheAlbumSlot() {
        val state = PlaybackState(currentTrack = station)
            .withLiveNowPlaying(LiveNowPlaying(station.id, "One More Time", "Daft Punk", "https://cover"))
        val shown = state.shownTrack!!
        assertEquals("One More Time", shown.title)
        assertEquals("Daft Punk", shown.artist)
        assertEquals("Big N", shown.album)
        assertEquals("https://cover", shown.artworkUrl)
        assertEquals(station.id, shown.id)
        // What the heart and the history act on stays the station.
        assertSame(station, state.currentTrack)
    }

    @Test
    fun withNoArtistTheStationKeepsTheArtistLine() {
        val shown = PlaybackState(currentTrack = station)
            .withLiveNowPlaying(LiveNowPlaying(station.id, "Jingle", artist = null)).shownTrack!!
        assertEquals("Jingle", shown.title)
        assertEquals("Big N", shown.artist)
        assertNull(shown.album)
    }

    @Test
    fun theSongIsDroppedOnceAnotherTrackPlays() {
        val withSong = PlaybackState(currentTrack = station)
            .withLiveNowPlaying(LiveNowPlaying(station.id, "Song", "Artist"))
        val next = withSong.copy(currentTrack = station.copy(id = "navidrome:radio:8"))
            .withLiveNowPlaying(withSong.liveNowPlaying)
        assertNull(next.liveNowPlaying)
        assertEquals("Big N", next.shownTrack?.title)
    }

    @Test
    fun anotherTracksSongIsIgnored() {
        val state = PlaybackState(currentTrack = station)
        assertSame(state, state.withLiveNowPlaying(LiveNowPlaying("navidrome:radio:8", "Song", "Artist")))
    }

    @Test
    fun aRecordingIsNeverOverlaid() {
        val song = station.copy(id = "navidrome:42", isLive = false)
        val state = PlaybackState(currentTrack = song)
        assertSame(state, state.withLiveNowPlaying(LiveNowPlaying(song.id, "Song", "Artist")))
    }
}
