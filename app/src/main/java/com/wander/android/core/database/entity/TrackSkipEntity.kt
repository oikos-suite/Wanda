package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How often a track has been skipped early since it was last played through.
 *
 * Its own table rather than columns on `tracks`: a track row is rewritten whenever a backend
 * answers with it again, and a count kept there would have to survive every one of those writes.
 * Here nothing but [com.wander.android.core.database.dao.TrackSkipDao] ever touches it.
 */
@Entity(tableName = "track_skips")
data class TrackSkipEntity(
    @PrimaryKey val trackId: String,
    /** Early skips since the last full play; a full play starts the count again. */
    val skipCount: Int,
    val lastSkippedAt: Long
)
