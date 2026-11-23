package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * The title and artist, carried along with the cover when the track changes: the outgoing text
 * slides away the way the filmstrip moves and fades, the incoming one arrives from the other side.
 * Direction comes from [playPosition] — the track's place in *play* order, the order the covers
 * are laid out in — so a skip back moves the other way. Not the queue index: under shuffle a skip
 * forward often lands on a lower index, and the text then arrived from the opposite side to the
 * cover it belongs to.
 *
 * A quarter of the width, not the whole of it — the cover makes the big movement, and text that
 * travelled as far would read as a second thing sliding rather than the same change.
 */
@Composable
internal fun TrackTextTransition(
    track: UnifiedTrack,
    playPosition: Int,
    modifier: Modifier = Modifier,
    content: @Composable (UnifiedTrack) -> Unit
) {
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val reducedMotion = LocalReducedMotion.current

    AnimatedContent(
        targetState = TrackSlot(track, playPosition),
        contentKey = { it.track.id },
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.position >= initialState.position) 1 else -1
            val motion = if (reducedMotion) {
                fadeIn(fade) togetherWith fadeOut(fade)
            } else {
                (slideInHorizontally(slide) { it * direction / TRAVEL_DIVISOR } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -it * direction / TRAVEL_DIVISOR } + fadeOut(fade))
            }
            motion using SizeTransform(clip = false)
        },
        label = "trackText"
    ) { slot -> content(slot.track) }
}

private data class TrackSlot(val track: UnifiedTrack, val position: Int)

private const val TRAVEL_DIVISOR = 4
