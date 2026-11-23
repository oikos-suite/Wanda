package com.wander.android.core.playback

import android.os.Bundle
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession

/**
 * The session's extras: the one channel a `MediaController` has for state that is not part of the
 * `Player` interface — see [ActualAudioFormat] and [LiveNowPlaying], the two things sent over it.
 *
 * Media3 replaces the extras whole on every [MediaSession.setSessionExtras], so each value is kept
 * here and they are always sent together; publishing one alone would wipe the other out.
 *
 * The audio format is forgotten on a track change, without publishing: the UI clears its own copy
 * at the same moment, and a song announcement arriving later must not resend the old track's
 * format as if it were the new one's.
 */
internal class SessionExtrasPublisher(private val session: () -> MediaSession?) : Player.Listener {
    private var audioFormat: Bundle? = null
    private var liveNowPlaying: Bundle? = null

    fun publishAudioFormat(format: ActualAudioFormat) {
        audioFormat = format.toBundle()
        publish()
    }

    fun publishLiveNowPlaying(nowPlaying: LiveNowPlaying?) {
        liveNowPlaying = nowPlaying?.toBundle()
        publish()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        audioFormat = null
    }

    private fun publish() {
        val extras = Bundle()
        audioFormat?.let(extras::putAll)
        liveNowPlaying?.let(extras::putAll)
        session()?.setSessionExtras(extras)
    }
}

/**
 * Publishes the *actually* decoded audio format — see [ActualAudioFormat] for why this is a
 * separate source of truth from the track's own container tags.
 *
 * An `AnalyticsListener` runs on the `ExoPlayer` itself, which [PlayerConnection] never touches —
 * it only holds a `MediaController`, whose `Player` interface has no such callback.
 */
@UnstableApi
internal class AudioFormatReporter(private val extras: SessionExtrasPublisher) : AnalyticsListener {
    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        extras.publishAudioFormat(ActualAudioFormat.of(format))
    }
}
