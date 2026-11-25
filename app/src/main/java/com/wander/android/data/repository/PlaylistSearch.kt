package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The playlists a search names: the library's own first, then what the backends returned.
 *
 * The library's are matched here, out of Room, rather than asked of each backend: Subsonic has no
 * playlist search at all, and Wanda's own and Agro's playlists only exist on this device. Every
 * one of them is already stored, so this costs no request and still answers offline.
 */
@Singleton
class PlaylistSearch @Inject constructor(private val musicRepository: MusicRepository) {

    /**
     * [remote] behind the library's playlists matching [query], each held to [selected].
     *
     * [available] is every source with a chip on the search screen. A playlist from a source with
     * no chip — Agro's, say — has nothing to switch it off with, so it shows while the search is
     * not narrowed and hides once it is, instead of slipping past a choice the user made.
     */
    suspend fun search(
        query: String,
        selected: Set<SourceType>,
        available: Set<SourceType>,
        remote: List<UnifiedPlaylist>
    ): List<UnifiedPlaylist> {
        val terms = query.searchTerms()
        if (terms.isEmpty()) return emptyList()
        // A failed read of Wanda's or Agro's own lists leaves them out of this one row. It is not
        // a failed search: the screen's error state speaks for the backends, which were asked.
        val own = musicRepository.getPlaylists(only = RemotePlaylistStore.ROOM_NATIVE).getOrNull().orEmpty()
        val connected = musicRepository.sources.filter { it.isConfigured.value }.mapTo(HashSet()) { it.sourceType }
        val stored = musicRepository.observeRemotePlaylists().first().filter { it.source in connected }
        return matchingPlaylists(terms, own + stored, selected, available) +
            remote.filter { it.source in selected }
    }
}

/** The library playlists whose name holds every one of [terms], in the selected sources only. */
internal fun matchingPlaylists(
    terms: List<String>,
    library: List<UnifiedPlaylist>,
    selected: Set<SourceType>,
    available: Set<SourceType>
): List<UnifiedPlaylist> {
    val narrowed = selected != available
    return library
        .filter { it.source in selected || (it.source !in available && !narrowed) }
        .filter { playlist -> playlist.name.foldedForSearch().let { name -> terms.all(name::contains) } }
        .distinctBy { it.id }
}

/** Lower case with accents removed, so "Été" is found by typing "ete". */
internal fun String.foldedForSearch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

internal fun String.searchTerms(): List<String> =
    foldedForSearch().split(WHITESPACE).filter { it.isNotBlank() }

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
