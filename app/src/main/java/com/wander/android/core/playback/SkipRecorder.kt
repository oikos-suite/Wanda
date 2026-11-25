package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import com.wander.android.data.repository.SkipHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Notices a track skipped early — the user moved on to the next one in its first
 * [EARLY_SKIP_MS] — and files it with [SkipHistory].
 *
 * Read off the player rather than the skip buttons, so every way of skipping counts: the
 * notification, a headset, the mini player's swipe, a car. Going back, picking another track
 * further down the queue, and a track ending on its own are not skips.
 */
internal class SkipRecorder(
    private val player: Player,
    private val scope: CoroutineScope,
    private val skips: SkipHistory
) : Player.Listener {

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val timeline = player.currentTimeline
        val next = if (timeline.isEmpty || oldPosition.mediaItemIndex >= timeline.windowCount) {
            C.INDEX_UNSET
        } else {
            timeline.getNextWindowIndex(oldPosition.mediaItemIndex, player.repeatMode, player.shuffleModeEnabled)
        }
        val skipped = isEarlySkip(
            reason = reason,
            fromIndex = oldPosition.mediaItemIndex,
            toIndex = newPosition.mediaItemIndex,
            nextIndex = next,
            positionMs = oldPosition.positionMs
        )
        // A file opened from another app is played, not added to the library and its history.
        val track = oldPosition.mediaItem?.takeIf { skipped }?.toUnifiedTrack()
        if (track != null && !isOpenedFileId(track.id)) scope.launch { skips.recordSkip(track) }
    }
}

/** How far into a track moving on still counts as skipping it. */
internal const val EARLY_SKIP_MS = 30_000L

/**
 * Whether a jump in playback is an early skip: a seek, not the track ending, to the item that
 * would have played next, from less than [EARLY_SKIP_MS] into the current one.
 */
internal fun isEarlySkip(reason: Int, fromIndex: Int, toIndex: Int, nextIndex: Int, positionMs: Long): Boolean =
    reason == Player.DISCONTINUITY_REASON_SEEK &&
        fromIndex != toIndex &&
        nextIndex != C.INDEX_UNSET &&
        toIndex == nextIndex &&
        positionMs in 0 until EARLY_SKIP_MS
