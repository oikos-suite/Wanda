package com.wander.android.ui.screens.search

import com.wander.android.data.model.SearchKind
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.SearchHistoryRepository
import com.wander.android.data.repository.SearchQueryHolder
import com.wander.android.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.wander.android.data.repository.CatalogTrackResolver
import com.wander.android.data.repository.LyricSearch
import com.wander.android.data.repository.PlaylistSearch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One search to run: what was typed, where to look, for what, and which attempt this is. */
private data class SearchRequest(
    val query: String,
    val sources: Set<SourceType>,
    val kind: SearchKind,
    val attempt: Int
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val lyricSearch: LyricSearch,
    private val playlistSearch: PlaylistSearch,
    private val playerConnection: PlayerConnection,
    private val playbackCoordinator: PlaybackCoordinator,
    private val shareRepository: ShareRepository,
    private val queryHolder: SearchQueryHolder,
    private val catalogTracks: CatalogTrackResolver,
    private val searchHistory: SearchHistoryRepository
) : ViewModel() {

    /**
     * The field lives in the dock at the bottom of the app, which outlives this ViewModel, so the
     * text is owned by [SearchQueryHolder] and only observed here. It is also what decides whether
     * this screen is on show at all — see `SearchLayer`.
     */
    val query: StateFlow<String> = queryHolder.query

    /**
     * The backends a search can reach right now.
     *
     * A flow, not a snapshot. This used to read `isConfigured.value` once when the ViewModel was
     * constructed, so a backend that finished signing in a moment later never gained a chip and
     * was never searched until the screen was recreated — and since the search surface lives on each
     * root tab, that could be a long time.
     *
     * Keyed on `isSearchable` rather than `isConfigured`: YouTube Music answers a search without
     * an account, and excluding it while signed out was the reason its results went missing.
     */
    val availableSources: StateFlow<List<SourceType>> =
        combine(musicRepository.sources.map { it.isSearchable }) { flags ->
            musicRepository.sources
                .filterIndexed { index, source -> flags[index] && source.capabilities.search }
                .map { it.sourceType }
                .sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Which of those the user has narrowed to, or null for "whatever is available".
     *
     * Null is not the same as the full set. A user who has never touched the chips wants every
     * source, *including* one that connects later; a user who explicitly selected every chip has
     * made a choice about the sources that existed then. Only the second is a set.
     */
    private val _selectedSources = MutableStateFlow<Set<SourceType>?>(null)

    val selectedSources: StateFlow<Set<SourceType>> =
        combine(_selectedSources, availableSources) { explicit, available ->
            explicit?.intersect(available.toSet())?.ifEmpty { available.toSet() }
                ?: available.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * What kind of thing this search is for. Only YouTube Music serves anything but [
     * SearchKind.TRACKS]; the other backends answer the non-music kinds with nothing rather than
     * with songs, so the results stay honest to the chip that is selected.
     */
    private val _kind = MutableStateFlow(SearchKind.TRACKS)
    val kind: StateFlow<SearchKind> = _kind.asStateFlow()

    fun selectKind(kind: SearchKind) { _kind.value = kind }

    /** Bumped by [retry], so the same query, sources and kind are asked again. */
    private val attempt = MutableStateFlow(0)

    fun retry() { attempt.update { it + 1 } }

    /** Empties the dock's field, which is what ends a search — see `SearchLayer`. */
    fun clearQuery() = queryHolder.set("")

    // Re-runs when the sources change as well as the query: toggling a backend on has to go and
    // ask it, not just unhide results that were never fetched.
    private val searchResults: StateFlow<SearchUiState> = combine(
        query,
        // The *resolved* selection, not the raw nullable one. While the user has made no explicit
        // choice the raw flow holds a constant null, so a backend finishing its sign-in mid-search
        // changed what "everything" means without this flow noticing — the results stayed as they
        // were until the next keystroke.
        selectedSources,
        _kind,
        attempt
    ) { text, sources, kind, tries -> SearchRequest(text, sources, kind, tries) }
        .debounce { request -> if (request.query.isBlank()) 0L else DEBOUNCE_MS }
        .flatMapLatest { (query, sources, kind) ->
            flow {
                if (query.isBlank()) {
                    emit(SearchUiState())
                    return@flow
                }
                emit(SearchUiState(isSearching = true, hasQuery = true))
                val outcome = musicRepository.searchWithStatus(query, sources, kind)
                // Lyrics and playlists are music: neither answers a search for videos or episodes.
                val music = kind == SearchKind.TRACKS
                val lyricMatches = if (music) {
                    lyricSearch.search(query, sources).withPlayableCatalogue(catalogTracks, sources)
                } else {
                    emptyList()
                }
                val playlists = if (music) {
                    playlistSearch.search(query, sources, availableSources.value.toSet(), outcome.playlists)
                } else {
                    emptyList()
                }
                emit(
                    SearchUiState(
                        isSearching = false,
                        results = outcome.tracks,
                        albums = outcome.albums,
                        artists = outcome.artists,
                        playlists = playlists,
                        lyricMatches = lyricMatches,
                        hasQuery = true,
                        unansweredSources = outcome.failedSources,
                        allSourcesFailed = outcome.allFailed
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    // Search results are a snapshot taken before any like happened, so the liked set is overlaid
    // from Room — otherwise tapping the heart wrote through but left the icon unchanged.
    val uiState: StateFlow<SearchUiState> = combine(
        searchResults,
        musicRepository.getLikedTrackIdsFlow()
    ) { state, likedIds ->
        state.copy(
            results = state.results.map { it.copy(isLiked = it.id in likedIds) },
            lyricMatches = state.lyricMatches.map { m ->
                m.copy(track = m.track.copy(isLiked = m.track.id in likedIds))
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun toggleSource(source: SourceType) {
        val current = selectedSources.value
        _selectedSources.value = if (source in current) {
            // Never empty: a search with no sources would just silently return nothing.
            current.minus(source).ifEmpty { current }
        } else {
            current + source
        }
    }

    /** Back to "everything", including sources that connect later — see [_selectedSources]. */
    fun selectAllSources() { _selectedSources.value = null }

    /**
     * Files the current query under recent searches. Called when a result is used — played, or
     * opened — so what is kept is the searches that found something; see [SearchHistoryRepository].
     */
    fun rememberQuery() = searchHistory.record(query.value)

    /** Plays the track and seeks directly to the matched lyric line's timestamp offset. */
    fun playTrackAtTimestamp(track: UnifiedTrack, timestampMs: Long?) {
        rememberQuery()
        playerConnection.play(
            tracks = listOf(track),
            startIndex = 0,
            startPositionMs = timestampMs ?: 0L
        )
    }

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /** Plays the track, then fills the queue behind it with its source's radio. */
    fun startRadio(track: UnifiedTrack) {
        rememberQuery()
        viewModelScope.launch { playbackCoordinator.startRadio(track) }
    }

    /** Whether this track's backend can mint a public link at all. */
    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    /** The link is published on a shared flow and raised as a share sheet by `WanderApp`. */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}
