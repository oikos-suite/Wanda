package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.TrackSkipEntity

/** The early skips that tell radio what not to play again; see [TrackSkipEntity]. */
@Dao
interface TrackSkipDao {

    @Query("SELECT * FROM track_skips WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackSkipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(skip: TrackSkipEntity)

    /** When [trackId] was last played through, or null if it never was. */
    @Query("SELECT lastPlayedTimestamp FROM tracks WHERE id = :trackId")
    suspend fun lastFullPlay(trackId: String): Long?

    /**
     * Counts one more early skip of [trackId] at [now].
     *
     * A play through since the last skip starts the count again at one: whoever skipped a song
     * three times last spring and played it to the end yesterday has changed their mind.
     */
    @Transaction
    suspend fun recordSkip(trackId: String, now: Long) {
        val previous = get(trackId)
        val playedSince = previous != null && (lastFullPlay(trackId) ?: 0L) > previous.lastSkippedAt
        val count = if (previous == null || playedSince) 1 else previous.skipCount + 1
        put(TrackSkipEntity(trackId = trackId, skipCount = count, lastSkippedAt = now))
    }

    /**
     * Tracks skipped early at least [minSkips] times, the last of them at or after [since], with
     * no play through after that last skip.
     */
    @Query(
        "SELECT s.trackId FROM track_skips s LEFT JOIN tracks t ON t.id = s.trackId " +
            "WHERE s.skipCount >= :minSkips AND s.lastSkippedAt >= :since " +
            "AND s.lastSkippedAt > COALESCE(t.lastPlayedTimestamp, 0)"
    )
    suspend fun wornOut(minSkips: Int, since: Long): List<String>
}
