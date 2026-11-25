package com.wander.android.ui.screens.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.selection.SelectedTrack
import com.wander.android.ui.components.selection.TrackSelectionState
import com.wander.android.ui.components.selection.TrackSwipeBox

/**
 * The songs a search matched, under a "Songs" heading when other sections stand above them.
 * Swipeable and selectable like every other track list.
 */
internal fun LazyListScope.trackResultsSection(
    tracks: List<UnifiedTrack>,
    withHeader: Boolean,
    selection: TrackSelectionState,
    onPlay: (UnifiedTrack) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit
) {
    if (withHeader && tracks.isNotEmpty()) {
        searchSectionHeader(key = "header_tracks", title = R.string.search_songs)
    }
    itemsIndexed(
        items = tracks,
        key = { _, track -> track.id },
        contentType = { _, _ -> "track" }
    ) { index, track ->
        val entranceScale = rememberShelfEntranceScale(index)
        val item = SelectedTrack(track.id, track)
        TrackSwipeBox(
            track = track,
            enabled = !selection.isActive,
            modifier = Modifier.animateItem().scale(entranceScale)
        ) {
            TrackRow(
                track = track,
                // A radio from the tapped song, not the results list.
                //
                // The list is everything matching a string: searching "Signals" and pressing one of
                // them queued every other song with "Signals" in its name, by unrelated artists, in
                // relevance order. That is a spelling coincidence, not a running order. A station
                // seeded from the track plays it first and then what actually sounds like it, across
                // every configured source — see `PlaybackCoordinator.startRadio`.
                onPlay = selection.onTap(item) { onPlay(track) },
                onLongPress = selection.onLongPress(item) { onLongPress(track) },
                selected = selection.stateOf(item.key)
            )
        }
    }
}
