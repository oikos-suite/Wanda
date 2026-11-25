package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSearchTest {

    private fun playlist(id: String, name: String, source: SourceType) =
        UnifiedPlaylist(id = id, source = source, name = name)

    private val subsonic = playlist("navidrome:1", "Road Trip", SourceType.NAVIDROME)
    private val youtube = playlist("ytm:1", "Road trip classics", SourceType.YTMUSIC)
    private val agro = playlist("agro:1", "Road trip with friends", SourceType.AGRO)
    private val library = listOf(subsonic, youtube, agro)
    private val chips = setOf(SourceType.NAVIDROME, SourceType.YTMUSIC)

    @Test
    fun `every word of the query has to be in the name, in any case and without accents`() {
        val summer = playlist("local:1", "Été indien", SourceType.LOCAL)
        val local = setOf(SourceType.LOCAL)
        assertEquals(listOf(summer), matchingPlaylists("ete IND".searchTerms(), listOf(summer), local, local))
        val winter = matchingPlaylists("ete hiver".searchTerms(), listOf(summer), local, local)
        assertEquals(emptyList<UnifiedPlaylist>(), winter)
    }

    @Test
    fun `nothing narrowed shows every source, including one without a chip`() {
        assertEquals(library, matchingPlaylists("road trip".searchTerms(), library, chips, chips))
    }

    @Test
    fun `Subsonic only leaves YouTube Music and chipless sources out`() {
        val onlySubsonic = setOf(SourceType.NAVIDROME)
        assertEquals(listOf(subsonic), matchingPlaylists("road".searchTerms(), library, onlySubsonic, chips))
    }

    @Test
    fun `a blank query has no terms`() {
        assertEquals(emptyList<String>(), "   ".searchTerms())
    }
}
