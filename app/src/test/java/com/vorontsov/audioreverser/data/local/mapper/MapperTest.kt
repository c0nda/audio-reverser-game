package com.vorontsov.audioreverser.data.local.mapper

import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity
import com.vorontsov.audioreverser.domain.model.RoundStatus
import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {

    @Test
    fun `attempt numbering follows list order after deletion`() {
        // Attempts 1,2,3 existed; attempt 2 was deleted, so the remaining two rows
        // must be numbered 1 and 2 while keeping their stable ids.
        val remaining = listOf(
            attempt(id = "a", createdAt = 100),
            attempt(id = "b", createdAt = 200),
        )
        val numbered = remaining.toAttemptsWithNumbers()
        assertEquals(listOf("a", "b"), numbered.map { it.attempt.id })
        assertEquals(listOf(1, 2), numbered.map { it.number })
    }

    @Test
    fun `secret original is locked while the round is in progress`() {
        val round = round(status = RoundStatus.IN_PROGRESS, originalPath = "C:/missing.wav")
        assertNull(round.secretOriginalAsset())
    }

    @Test
    fun `secret original is exposed for a completed round`() {
        val round = round(status = RoundStatus.COMPLETED, originalPath = "C:/missing.wav")
        val asset = round.secretOriginalAsset()
        assertEquals("C:/missing.wav", asset?.path)
        // The file does not exist, so the asset is reported as unavailable but exposed.
        assertFalse(asset!!.exists)
    }

    @Test
    fun `secret reversed is available while the round is active`() {
        val round = round(status = RoundStatus.IN_PROGRESS, reversedPath = "C:/rev.wav")
        assertEquals("C:/rev.wav", round.secretReversedAsset()?.path)
    }

    @Test
    fun `has secret recording reflects both paths`() {
        assertTrue(round(status = RoundStatus.IN_PROGRESS, originalPath = "a", reversedPath = "b").toDomain().hasSecretRecording)
        assertFalse(round(status = RoundStatus.IN_PROGRESS, originalPath = "a").toDomain().hasSecretRecording)
        assertFalse(GameRoundEntity(
            id = "r",
            title = "",
            status = "IN_PROGRESS",
            createdAt = 0,
            updatedAt = 0,
            secretOriginalPath = null,
            secretReversedPath = null,
            secretDurationMs = null,
        ).toDomain().hasSecretRecording)
    }

    private fun round(
        status: RoundStatus,
        originalPath: String? = null,
        reversedPath: String? = null,
    ) = GameRoundEntity(
        id = "round",
        title = "",
        status = status.toStorage(),
        createdAt = 0,
        updatedAt = 0,
        secretOriginalPath = originalPath,
        secretReversedPath = reversedPath,
        secretDurationMs = 1000L,
    )

    private fun attempt(id: String, createdAt: Long) = GuessAttemptEntity(
        id = id,
        roundId = "round",
        title = "",
        createdAt = createdAt,
        originalAudioPath = "o_$id",
        reversedAudioPath = "r_$id",
        durationMs = 1000L,
    )
}
