package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * These tracks without the ones the user keeps skipping, by id and by recording: a song skipped
 * on YouTube Music is the same song on Subsonic, and offering that copy instead is no answer.
 */
internal fun List<UnifiedTrack>.withoutWornOut(wornOut: List<UnifiedTrack>): List<UnifiedTrack> {
    if (wornOut.isEmpty()) return this
    val ids = wornOut.mapTo(HashSet()) { it.id }
    val recordings = wornOut.mapTo(HashSet(), TrackDeduplicator::recordingKey)
    return filterNot { it.id in ids || TrackDeduplicator.recordingKey(it) in recordings }
}
