package com.vorontsov.audioreverser.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vorontsov.audioreverser.presentation.theme.AudioReverserTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioPlaybackRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsVariantLabelAndFormattedTime() {
        composeRule.setContent {
            AudioReverserTheme {
                AudioPlaybackRow(
                    label = "Reversed",
                    isPlaying = false,
                    isEnabled = true,
                    isAvailable = true,
                    positionMs = 0L,
                    durationMs = 65_000L,
                    onToggle = {},
                )
            }
        }

        composeRule.onNodeWithText("Reversed").assertIsDisplayed()
        composeRule.onNodeWithText("0:00 / 1:05").assertIsDisplayed()
    }

    @Test
    fun unavailableAssetShowsFallback() {
        composeRule.setContent {
            AudioReverserTheme {
                AudioPlaybackRow(
                    label = "Original",
                    isPlaying = false,
                    isEnabled = true,
                    isAvailable = false,
                    positionMs = 0L,
                    durationMs = 1000L,
                    onToggle = {},
                )
            }
        }

        composeRule.onNodeWithText("File unavailable").assertIsDisplayed()
    }

    @Test
    fun disabledRowDoesNotInvokeToggle() {
        var clicked = false
        composeRule.setContent {
            AudioReverserTheme {
                AudioPlaybackRow(
                    label = "Reversed",
                    isPlaying = false,
                    isEnabled = false,
                    isAvailable = true,
                    positionMs = 0L,
                    durationMs = 1000L,
                    onToggle = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Reversed").performClick()
        assertTrue(!clicked)
    }
}
