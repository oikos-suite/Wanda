package com.wander.android.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import com.wander.android.data.repository.RadioArtworkLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Turns a radio station's in-band song announcements into [LiveNowPlaying] — see there for where
 * they come from. Registered on the service's own player: `onMetadata` is never forwarded to a
 * `MediaController`.
 *
 * The song is published the moment it is announced, and published again with its cover once
 * [artwork] has looked one up, so the title never waits on a search. A lookup still running when
 * the next song is announced is dropped rather than allowed to land a cover on the wrong song.
 *
 * Only live items are read. A recording's tags are its own metadata, already in the queue; timed
 * metadata on one would not be a "now playing" at all.
 */
internal class LiveNowPlayingReporter(
    private val player: Player,
    private val scope: CoroutineScope,
    private val artwork: RadioArtworkLookup,
    private val publish: (LiveNowPlaying?) -> Unit
) : Player.Listener {
    private var current: LiveNowPlaying? = null
    private var lookup: Job? = null

    override fun onMetadata(metadata: Metadata) {
        val item = player.currentMediaItem ?: return
        if (!item.isLive()) return
        val song = metadata.streamTitle() ?: return
        val stationId = item.mediaId
        val previous = current
        if (previous != null && previous.trackId == stationId && previous.title == song.title &&
            previous.artist == song.artist
        ) {
            return
        }

        val announced = LiveNowPlaying(trackId = stationId, title = song.title, artist = song.artist)
        current = announced
        publish(announced)

        lookup?.cancel()
        val artist = song.artist ?: return
        lookup = scope.launch {
            val url = artwork.find(artist, song.title) ?: return@launch
            if (current !== announced) return@launch
            val withCover = announced.copy(artworkUrl = url)
            current = withCover
            publish(withCover)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        lookup?.cancel()
        lookup = null
        if (current != null) {
            current = null
            publish(null)
        }
    }

    private fun MediaItem.isLive(): Boolean = isLiveUri() || toUnifiedTrack()?.isLive == true
}
