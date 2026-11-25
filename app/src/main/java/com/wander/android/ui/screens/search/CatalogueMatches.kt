package com.wander.android.ui.screens.search

import com.wander.android.data.model.LyricMatch
import com.wander.android.data.model.SourceType
import com.wander.android.data.repository.CatalogTrackResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Each catalogue lookup is a network search, so only the first few are tried. */
private const val MAX_CATALOGUE_LOOKUPS = 5

/**
 * Gives the catalogue's matches a track that plays. A recording nobody here holds is looked for
 * in the selected [sources]; one none of them has is dropped, since a result that cannot be played
 * is not an answer.
 */
internal suspend fun List<LyricMatch>.withPlayableCatalogue(
    resolver: CatalogTrackResolver,
    sources: Set<SourceType>
): List<LyricMatch> {
    val (stubs, held) = partition { it.track.source == SourceType.UNRESOLVED }
    val resolved = coroutineScope {
        stubs.take(MAX_CATALOGUE_LOOKUPS)
            .map { match -> async { resolver.playable(match.track, sources)?.let { match.copy(track = it) } } }
            .awaitAll()
            .filterNotNull()
    }
    return held + resolved
}
