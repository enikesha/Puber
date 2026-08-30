package com.kino.puber.ui.feature.player.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OriginalAudioTrackTest {

    @Test
    fun recognizesTheOriginalTrackInAnHlsLabel() {
        assertTrue(isOriginalAudioTrack("01. Оригинал (ENG)"))
    }

    @Test
    fun recognizesTheOriginalTrackInAnApiAudioType() {
        assertTrue(isOriginalAudioTrack(null, "Ориг.", null))
        assertTrue(isOriginalAudioTrack("Original"))
    }

    @Test
    fun doesNotTreatATranslatedTrackAsOriginal() {
        assertFalse(isOriginalAudioTrack("02. Многоголосый. Red Head Sound (RUS)"))
        assertFalse(isOriginalAudioTrack(null, null))
    }
}
