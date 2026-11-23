package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioArtworkMatchTest {

    private fun hit(title: String, artist: String, art: String? = "https://art/$title") = UnifiedTrack(
        id = "ytm:$title",
        source = SourceType.YTMUSIC,
        title = title,
        artist = artist,
        artworkUrl = art
    )

    @Test
    fun decoratedTitleAndCreditedArtistStillMatch() {
        val found = RadioArtworkMatch.pick(
            listOf(hit("One More Time (Radio Edit)", "Daft Punk & Romanthony")),
            artist = "DAFT PUNK",
            title = "One more time"
        )
        assertEquals("ytm:One More Time (Radio Edit)", found?.id)
    }

    @Test
    fun aTitleExtendedByTheStationStillMatches() {
        val found = RadioArtworkMatch.pick(listOf(hit("Around the World", "Daft Punk")), "Daft Punk", "Around the World - Live")
        assertEquals("ytm:Around the World", found?.id)
    }

    @Test
    fun anotherArtistsSongOfTheSameNameIsRefused() {
        assertNull(RadioArtworkMatch.pick(listOf(hit("Hello", "Lionel Richie")), "Adele", "Hello"))
    }

    @Test
    fun aDifferentSongByTheArtistIsRefused() {
        assertNull(RadioArtworkMatch.pick(listOf(hit("Hello Again", "Adele")), "Adele", "Hell"))
    }

    @Test
    fun aMatchWithoutArtworkIsSkippedForOneWithIt() {
        val found = RadioArtworkMatch.pick(
            listOf(hit("Hello", "Adele", art = null), hit("Hello", "Adele", art = "https://cover")),
            "Adele",
            "Hello"
        )
        assertEquals("https://cover", found?.artworkUrl)
    }

    @Test
    fun artistWordsMustMatchWhole() {
        assertNull(RadioArtworkMatch.pick(listOf(hit("Song", "Adelel")), "Adele", "Song"))
    }
}
