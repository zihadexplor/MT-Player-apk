package com.mtplayer.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.data.db.MtDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Play counting must not depend on SQLite UPSERT (missing on Android 9/10). */
@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: MtDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MtDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun recordPlayInsertsThenIncrements() = runTest {
        val h = db.history()
        h.recordPlay(MediaType.SONG, 7, 1_000)
        h.recordPlay(MediaType.SONG, 7, 2_000)
        h.recordPlay(MediaType.SONG, 7, 3_000)
        h.recordPlay(MediaType.SONG, 8, 2_500)
        h.recordPlay(MediaType.VIDEO, 7, 4_000)

        val songs = h.observeMostPlayed(MediaType.SONG, 10).first()
        assertEquals(listOf(7L, 8L), songs.map { it.mediaId })
        assertEquals(3, songs[0].playCount)
        assertEquals(3_000, songs[0].lastPlayedAt)
        assertEquals(1, songs[1].playCount)
        assertEquals(1, h.observeRecent(MediaType.VIDEO, 10).first().single().playCount)
    }
}
