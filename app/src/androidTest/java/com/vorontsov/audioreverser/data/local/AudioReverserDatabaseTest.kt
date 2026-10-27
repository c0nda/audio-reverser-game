package com.vorontsov.audioreverser.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioReverserDatabaseTest {

    private lateinit var database: AudioReverserDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AudioReverserDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveRound() = runTest {
        val dao = database.gameRoundDao()
        dao.insert(round("r1", createdAt = 100))

        val loaded = dao.getRound("r1")
        assertEquals("r1", loaded?.id)
        assertEquals("IN_PROGRESS", loaded?.status)
    }

    @Test
    fun observeRoundsEmitsReactiveUpdates() = runTest {
        val dao = database.gameRoundDao()
        dao.insert(round("r1", createdAt = 100))
        assertEquals(1, dao.observeRounds().first().size)

        dao.insert(round("r2", createdAt = 200))
        assertEquals(2, dao.observeRounds().first().size)
        // Newest first.
        assertEquals("r2", dao.observeRounds().first().first().id)
    }

    @Test
    fun attemptsAreOrderedByCreationThenId() = runTest {
        database.gameRoundDao().insert(round("r1", createdAt = 100))
        val dao = database.guessAttemptDao()
        dao.insert(attempt("a2", "r1", createdAt = 200))
        dao.insert(attempt("a1", "r1", createdAt = 100))
        dao.insert(attempt("a3", "r1", createdAt = 200))

        val ordered = dao.getAttempts("r1").map { it.id }
        assertEquals(listOf("a1", "a2", "a3"), ordered)
    }

    @Test
    fun deletingAttemptKeepsStableIdsOfTheRest() = runTest {
        database.gameRoundDao().insert(round("r1", createdAt = 100))
        val dao = database.guessAttemptDao()
        dao.insert(attempt("a1", "r1", createdAt = 100))
        dao.insert(attempt("a2", "r1", createdAt = 200))
        dao.insert(attempt("a3", "r1", createdAt = 300))

        dao.deleteById("a2")

        val remaining = dao.getAttempts("r1")
        assertEquals(listOf("a1", "a3"), remaining.map { it.id })
    }

    @Test
    fun cascadeDeleteRemovesAttemptsWithRound() = runTest {
        database.gameRoundDao().insert(round("r1", createdAt = 100))
        database.guessAttemptDao().insert(attempt("a1", "r1", createdAt = 100))

        database.gameRoundDao().deleteById("r1")

        assertTrue(database.guessAttemptDao().getAttempts("r1").isEmpty())
    }

    @Test
    fun pendingDeletionRowsAreHiddenFromQueries() = runTest {
        database.gameRoundDao().insert(round("r1", createdAt = 100))
        database.gameRoundDao().markPendingDeletion("r1")

        assertTrue(database.gameRoundDao().observeRounds().first().isEmpty())
        assertNull(database.gameRoundDao().observeRound("r1").first())
        assertEquals(1, database.gameRoundDao().getRoundsPendingDeletion().size)
    }

    @Test
    fun roundAttemptCountsAreAggregated() = runTest {
        database.gameRoundDao().insert(round("r1", createdAt = 100))
        database.gameRoundDao().insert(round("r2", createdAt = 200))
        database.guessAttemptDao().insert(attempt("a1", "r1", createdAt = 100))
        database.guessAttemptDao().insert(attempt("a2", "r1", createdAt = 200))

        val counts = database.guessAttemptDao().observeAllCounts().first()
            .associate { it.roundId to it.count }
        assertEquals(2, counts["r1"])
        assertNull(counts["r2"])
    }

    private fun round(id: String, createdAt: Long) = GameRoundEntity(
        id = id,
        title = "",
        status = "IN_PROGRESS",
        createdAt = createdAt,
        updatedAt = createdAt,
        secretOriginalPath = null,
        secretReversedPath = null,
        secretDurationMs = null,
    )

    private fun attempt(id: String, roundId: String, createdAt: Long) = GuessAttemptEntity(
        id = id,
        roundId = roundId,
        title = "",
        createdAt = createdAt,
        originalAudioPath = "o_$id",
        reversedAudioPath = "r_$id",
        durationMs = 1000L,
    )
}
