package com.wander.android.ui.screens.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.ui.components.playlistArtKey
import com.wander.android.ui.screens.library.PlaylistRow

/** How many playlists show before the section is expanded. */
private const val COLLAPSED_PLAYLISTS = 3

/**
 * The playlists a search matched — your library's first, then the backends' — as rows, the first
 * few and a toggle for the rest, the way the lyric matches are shown.
 */
internal fun LazyListScope.playlistResultsSection(
    playlists: List<UnifiedPlaylist>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    if (playlists.isEmpty()) return
    val collapsible = playlists.size > COLLAPSED_PLAYLISTS
    val displayed = if (expanded) playlists else playlists.take(COLLAPSED_PLAYLISTS)

    searchSectionHeader(key = "header_playlists", title = R.string.search_section_playlists)
    itemsIndexed(
        items = displayed,
        key = { _, playlist -> "playlist_${playlist.id}" },
        contentType = { _, _ -> "playlist" }
    ) { index, playlist ->
        PlaylistRow(
            playlist = playlist,
            index = index,
            onClick = { onOpenPlaylist(playlist.id) },
            sharedKey = playlistArtKey(playlist.id),
            modifier = Modifier.animateItem()
        )
    }
    if (collapsible) {
        item(key = "footer_playlists_toggle") {
            TextButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem()
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.search_lyrics_show_fewer)
                    } else {
                        val hidden = playlists.size - COLLAPSED_PLAYLISTS
                        pluralStringResource(R.plurals.search_playlists_show_more, hidden, hidden)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
