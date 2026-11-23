package com.wander.android.data.repository

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a cover for the song a radio station is playing — see
 * [LiveNowPlaying][com.wander.android.core.playback.LiveNowPlaying]. Stations come from the
 * Subsonic server with no artwork of their own, and a stream's song announcement carries only
 * text, so the cover has to come from a search.
 *
 * Your own music — on this device and on your server — is always searched: the song goes nowhere
 * that does not already hold your library. YouTube Music and Deezer are searched only with
 * [SecureStorage.isRadioArtworkLookupEnabled] on, because that sends every song the station plays
 * to them as it plays, which nothing you did asked for.
 */
@Singleton
class RadioArtworkLookup @Inject constructor(
    private val musicRepository: MusicRepository,
    private val secureStorage: SecureStorage
) {
    /** The cover of [artist]'s [title], or null when no source has a confident match. */
    suspend fun find(artist: String, title: String): String? {
        val sources = if (secureStorage.isRadioArtworkLookupEnabled.value) null else OwnSources
        val results = musicRepository.searchAllSources("$artist $title", onlySources = sources)
        return RadioArtworkMatch.pick(results, artist, title)?.artworkUrl
    }

    private companion object {
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
