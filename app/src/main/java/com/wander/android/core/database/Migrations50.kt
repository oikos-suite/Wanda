package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 49 to 50: `track_skips`, the early skips radio learns from. */
internal val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `track_skips` (`trackId` TEXT NOT NULL, `skipCount` INTEGER NOT NULL, " +
                "`lastSkippedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
        )
    }
}
