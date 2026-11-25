package com.wander.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Early skips as [com.wander.android.core.database.dao.TrackSkipDao] counts and forgives them. */
@RunWith(KeystoreRobolectricRunner::class)
@Config(sdk = [35])
class TrackSkipDaoRoomTest {
    private lateinit var db: WanderDatabase
    private val skips get() = db.trackSkipDao()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WanderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { db.trackDao().insertNewTracks(listOf(track("1"), track("2"))) }
    }

    @After
    fun close() = db.close()

    private fun skip(id: String, at: Long) = runBlocking { skips.recordSkip("navidrome:$id", at) }

    private fun playThrough(id: String, at: Long) =
        runBlocking { db.trackDao().incrementPlayCount("navidrome:$id", at) }

    private fun wornOut(minSkips: Int = 3, since: Long = 0L) = runBlocking { skips.wornOut(minSkips, since).toSet() }

    @Test
    fun threeSkipsInARowWearATrackOut() {
        skip("1", 10)
        skip("1", 20)
        assertEquals(emptySet<String>(), wornOut())
        skip("1", 30)
        assertEquals(setOf("navidrome:1"), wornOut())
    }

    @Test
    fun aPlayThroughAfterTheLastSkipForgivesIt() {
        repeat(3) { skip("1", 10L + it) }
        playThrough("1", 100)
        assertEquals(emptySet<String>(), wornOut())
    }

    @Test
    fun skipsAfterAPlayThroughCountFromOneAgain() {
        repeat(3) { skip("1", 10L + it) }
        playThrough("1", 100)
        skip("1", 200)
        skip("1", 210)
        assertEquals(2, runBlocking { skips.get("navidrome:1")?.skipCount })
        assertEquals(emptySet<String>(), wornOut())
    }

    @Test
    fun oldSkipsAreForgiven() {
        repeat(3) { skip("1", 10L + it) }
        assertEquals(emptySet<String>(), wornOut(since = 1_000))
    }

    @Test
    fun onlyTheSkippedTrackIsCounted() {
        repeat(3) { skip("1", 10L + it) }
        skip("2", 50)
        assertEquals(setOf("navidrome:1"), wornOut())
    }

    /** The migration must build exactly the table Room expects, or upgraded installs fail to open. */
    @Test
    fun theMigrationBuildsTheTableRoomExpects() {
        val sqlite = db.openHelper.writableDatabase
        val expected = columns(sqlite)
        sqlite.execSQL("DROP TABLE track_skips")
        MIGRATION_49_50.migrate(sqlite)
        assertEquals(expected, columns(sqlite))
    }

    /** Name, type, NOT NULL and primary-key position of each column, as SQLite reports them. */
    private fun columns(sqlite: SupportSQLiteDatabase): List<String> =
        sqlite.query("PRAGMA table_info(`track_skips`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(1)} ${cursor.getString(2)} ${cursor.getInt(3)} ${cursor.getInt(5)}")
                }
            }
        }

    private fun track(id: String) = TrackEntity(
        id = "navidrome:$id",
        sourceTrackId = id,
        source = SourceType.NAVIDROME,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        albumId = "navidrome:Album",
        artistId = null,
        durationMs = 1_000,
        artworkUrl = null,
        streamUri = null,
        trackNumber = null,
        discNumber = null,
        year = null,
        genre = null,
        bitRateKbps = null,
        format = null,
        addedTimestamp = id.toLong()
    )
}
