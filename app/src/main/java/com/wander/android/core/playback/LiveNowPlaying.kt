package com.wander.android.core.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/**
 * The song a radio station says it is playing right now, as opposed to the station itself.
 *
 * A station is one endless item, so the queue only ever knows its name. What is on air arrives
 * in-band: an Icecast or Shoutcast stream interleaves `StreamTitle='Artist - Title'` with the audio
 * (ICY metadata, which Media3 asks for on every progressive request), and an HLS station carries
 * the same thing as timed ID3 frames. [PlaybackService] reads them off the player, and they reach
 * [PlayerConnection] through the session extras the same way [ActualAudioFormat] does.
 *
 * [trackId] is the station's track id, so a value that outlived its station is never laid over
 * another one — see [PlaybackState.withLiveNowPlaying]. [artworkUrl] is the song's cover, when
 * [RadioArtworkLookup][com.wander.android.data.repository.RadioArtworkLookup] found one.
 */
@Immutable
data class LiveNowPlaying(
    val trackId: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String? = null
) {

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TRACK_ID, trackId)
        putString(KEY_TITLE, title)
        artist?.let { putString(KEY_ARTIST, it) }
        artworkUrl?.let { putString(KEY_ARTWORK, it) }
    }

    companion object {
        private const val KEY_TRACK_ID = "wanda.liveNowPlaying.trackId"
        private const val KEY_TITLE = "wanda.liveNowPlaying.title"
        private const val KEY_ARTIST = "wanda.liveNowPlaying.artist"
        private const val KEY_ARTWORK = "wanda.liveNowPlaying.artworkUrl"

        /** Reads a [Bundle] written by [toBundle], or null when it carries none of these keys. */
        fun fromBundle(bundle: Bundle): LiveNowPlaying? {
            val trackId = bundle.getString(KEY_TRACK_ID) ?: return null
            val title = bundle.getString(KEY_TITLE) ?: return null
            return LiveNowPlaying(
                trackId = trackId,
                title = title,
                artist = bundle.getString(KEY_ARTIST),
                artworkUrl = bundle.getString(KEY_ARTWORK)
            )
        }
    }
}

/** An artist and title read off a stream, before anything is known about its cover. */
internal data class StreamTitle(val artist: String?, val title: String)

/**
 * Splits an ICY `StreamTitle` into artist and title.
 *
 * `Artist - Title` is a convention, not a format: the first ` - ` divides them, so a title that
 * itself holds one (`Song - Radio Edit`) stays whole. Anything without one — a jingle, the
 * station's slogan — is all title, with no artist to search for.
 */
internal fun parseStreamTitle(raw: String): StreamTitle? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val split = text.indexOf(ARTIST_SEPARATOR)
    if (split > 0) {
        val artist = text.substring(0, split).trim()
        val title = text.substring(split + ARTIST_SEPARATOR.length).trim()
        if (artist.isNotEmpty() && title.isNotEmpty()) return StreamTitle(artist, title)
    }
    return StreamTitle(artist = null, title = text)
}

/**
 * The song this batch of in-band metadata announces: the ICY title of a progressive station, or
 * the `TIT2`/`TPE1` frames of an HLS one. Null when the batch says nothing about one.
 */
@OptIn(UnstableApi::class)
internal fun Metadata.streamTitle(): StreamTitle? {
    var id3Title: String? = null
    var id3Artist: String? = null
    for (i in 0 until length()) {
        when (val entry = get(i)) {
            is IcyInfo -> entry.title?.let(::parseStreamTitle)?.let { return it }
            is TextInformationFrame -> when (entry.id) {
                "TIT2" -> id3Title = entry.values.firstOrNull()
                "TPE1" -> id3Artist = entry.values.firstOrNull()
            }
        }
    }
    val title = id3Title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return StreamTitle(id3Artist?.trim()?.takeIf { it.isNotEmpty() }, title)
}

private const val ARTIST_SEPARATOR = " - "
