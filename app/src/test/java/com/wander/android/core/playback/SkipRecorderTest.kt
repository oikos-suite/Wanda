package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipRecorderTest {

    private fun skip(
        reason: Int = Player.DISCONTINUITY_REASON_SEEK,
        from: Int = 0,
        to: Int = 1,
        next: Int = 1,
        at: Long = 5_000L
    ) = isEarlySkip(reason, from, to, next, at)

    @Test
    fun `moving on to the next track in its first seconds is a skip`() = assertTrue(skip())

    @Test
    fun `half a minute in it is not`() = assertFalse(skip(at = EARLY_SKIP_MS))

    @Test
    fun `a track ending on its own is not a skip`() =
        assertFalse(skip(reason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION))

    @Test
    fun `going back is not a skip`() = assertFalse(skip(from = 3, to = 2, next = 4))

    @Test
    fun `picking a track further down the queue is not a skip`() = assertFalse(skip(to = 5, next = 1))

    @Test
    fun `seeking within the same track is not a skip`() = assertFalse(skip(to = 0, next = 1))

    @Test
    fun `the last track with nothing after it has no next to skip to`() =
        assertFalse(skip(to = 0, next = C.INDEX_UNSET))
}
