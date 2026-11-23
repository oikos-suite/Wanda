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
        val item = player.currentMediaItem?.takeIf { it.isLive() }
        val song = item?.let { metadata.streamTitle() }
        if (item == null || song == null || current?.isSong(item.mediaId, song) == true) return
        announce(LiveNowPlaying(trackId = item.mediaId, title = song.title, artist = song.artist))
    }

    private fun announce(announced: LiveNowPlaying) {
        current = announced
        publish(announced)
        lookup?.cancel()
        val artist = announced.artist
        lookup = artist?.let { scope.launch { addCover(announced, it) } }
    }

    private suspend fun addCover(announced: LiveNowPlaying, artist: String) {
        val url = artwork.find(artist, announced.title)
        if (url != null && current === announced) {
            val withCover = announced.copy(artworkUrl = url)
            current = withCover
            publish(withCover)
        }
    }

    private fun LiveNowPlaying.isSong(stationId: String, song: StreamTitle): Boolean =
        trackId == stationId && title == song.title && artist == song.artist

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
