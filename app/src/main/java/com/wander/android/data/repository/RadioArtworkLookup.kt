package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.deezer.DeezerApiClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a cover for the song a radio station is playing — see
 * [LiveNowPlaying][com.wander.android.core.playback.LiveNowPlaying]. Stations come from the
 * Subsonic server with no artwork of their own, and a stream's song announcement carries only
 * text, so the cover has to come from a search.
 *
 * Your own music — on this device and on your server — is searched first, always: the song goes
 * nowhere that does not already hold your library. Only when that finds nothing, and only with
 * [SecureStorage.isRadioArtworkLookupEnabled] on, is Deezer's public catalogue asked — no account
 * needed, so it covers every station whatever backends are signed in. It is behind the switch
 * because it sends every song the station plays to Deezer as it plays.
 */
@Singleton
class RadioArtworkLookup @Inject constructor(
    private val musicRepository: MusicRepository,
    private val deezerCatalog: DeezerApiClient,
    private val secureStorage: SecureStorage
) {
    /** The cover of [artist]'s [title], or null when no source has a confident match. */
    suspend fun find(artist: String, title: String): String? {
        val own = musicRepository.searchAllSources("$artist $title", onlySources = OwnSources)
        val ownMatch = RadioArtworkMatch.pick(own, artist, title)
        return when {
            ownMatch != null -> ownMatch.artworkUrl
            secureStorage.isRadioArtworkLookupEnabled.value -> fromCatalog(artist, title)
            else -> null
        }
    }

    private suspend fun fromCatalog(artist: String, title: String): String? {
        // Deezer's advanced search: each field matched on its own, so a common title by somebody
        // else does not crowd the right song out of the first page.
        val query = "artist:\"${artist.quoteSafe()}\" track:\"${title.quoteSafe()}\""
        return deezerCatalog.searchTracks(query, limit = CATALOG_RESULTS).fold(
            onSuccess = { RadioArtworkMatch.pick(it, artist, title)?.artworkUrl },
            onFailure = { error ->
                Log.w(TAG, "Radio cover lookup failed: ${error.loggableMessage()}")
                null
            }
        )
    }

    private fun String.quoteSafe(): String = replace('"', ' ')

    private companion object {
        const val TAG = "RadioArtworkLookup"
        const val CATALOG_RESULTS = 10
        val OwnSources = SourceType.entries.filter { it.isPersonalLibrary }.toSet()
    }
}

/**
 * Which search result is the song a station announced.
 *
 * Strict on purpose. A search for a song nobody has returns *something*, and a confident stranger's
 * cover is worse than the station's placeholder: both the title and the artist have to agree.
 * Agreeing allows for the decoration stations and catalogues add — `(Radio Edit)`, `feat.`,
 * punctuation and case — by comparing words only, and by letting one title extend the other.
 */
internal object RadioArtworkMatch {

    fun pick(candidates: List<UnifiedTrack>, artist: String, title: String): UnifiedTrack? {
        val wantTitle = normalize(title)
        val wantArtist = normalize(artist)
        if (wantTitle.isEmpty() || wantArtist.isEmpty()) return null
        return candidates.firstOrNull { candidate ->
            !candidate.artworkUrl.isNullOrBlank() && !candidate.isLive &&
                titlesAgree(normalize(candidate.title), wantTitle) &&
                artistsAgree(normalize(candidate.artist), wantArtist)
        }
    }

    private fun titlesAgree(found: String, wanted: String): Boolean =
        found == wanted || found.startsWith("$wanted ") || wanted.startsWith("$found ")

    /** One artist line contains the other: `A` is credited on `A & B`, and `A feat. B` is still `A`. */
    private fun artistsAgree(found: String, wanted: String): Boolean =
        found.isNotEmpty() && (" $found ".contains(" $wanted ") || " $wanted ".contains(" $found "))

    /** Lower case, bracketed asides dropped, and anything but letters and digits made a single space. */
    internal fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Bracketed, " ")
            .replace(NonWord, " ")
            .trim()

    private val Bracketed = Regex("""\([^)]*\)|\[[^\]]*\]""")
    private val NonWord = Regex("""[^\p{L}\p{N}]+""")
}
