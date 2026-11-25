package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackSkipDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the user keeps skipping, so radio stops offering it.
 *
 * Only early skips count — see `SkipRecorder` — and only since the track was last played through:
 * a song skipped in the first seconds three times running is one the user does not want right
 * now, and one they then play to the end is wanted again. Nothing here leaves the device.
 */
@Singleton
class SkipHistory @Inject constructor(
    private val dao: TrackSkipDao,
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage
) {
    /** Counts an early skip of [track], unless incognito, which records no listening at all. */
    suspend fun recordSkip(track: UnifiedTrack, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            if (secureStorage.isIncognitoMode || track.isLive || track.isEpisode) return@withContext
            dao.recordSkip(track.id, now)
        }

    /** The tracks radio should leave out; see [withoutWornOut]. */
    suspend fun wornOut(now: Long = System.currentTimeMillis()): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val ids = dao.wornOut(MIN_SKIPS, now - WINDOW_MS)
        if (ids.isEmpty()) emptyList() else trackDao.getTracksByIds(ids).map(TrackEntity::toUnifiedTrack)
    }

    internal companion object {
        /** Once or twice is a mood; three times without a play through in between is a pattern. */
        const val MIN_SKIPS = 3

        /** Skips older than this are forgiven, so a track is never left out for good. */
        const val WINDOW_MS = 60L * 24 * 60 * 60 * 1000
    }
}
