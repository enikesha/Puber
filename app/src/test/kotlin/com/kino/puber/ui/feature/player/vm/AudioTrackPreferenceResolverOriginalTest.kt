package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AudioTrackPreferenceResolverOriginalTest {

    private val resolver = AudioTrackPreferenceResolver()

    @Test
    fun originalPreference_picksTheOriginalTrackOfADifferentLanguage() {
        val koreanTitle = listOf(
            AudioTrackUIState(0, "01. Дубляж (RUS)", "rus"),
            AudioTrackUIState(1, "02. Оригинал (KOR)", "kor", isOriginal = true),
        )

        val index = resolver.findAudioTrackIndex(
            tracks = koreanTitle,
            preferredLabel = "01. Оригинал (ENG)",
            preferredLang = "eng",
            preferOriginal = true,
        )

        assertEquals(1, index)
    }

    @Test
    fun originalPreference_isNotShadowedByADubInTheRememberedLanguage() {
        val tracks = listOf(
            AudioTrackUIState(0, "01. Дубляж (ENG)", "eng"),
            AudioTrackUIState(1, "02. Оригинал (KOR)", "kor", isOriginal = true),
        )

        val index = resolver.findAudioTrackIndex(
            tracks = tracks,
            preferredLabel = "01. Оригинал (ENG)",
            preferredLang = "eng",
            preferOriginal = true,
        )

        assertEquals(1, index)
    }

    @Test
    fun originalPreference_fallsBackToLanguageWhenTheTitleHasNoOriginalTrack() {
        val tracks = listOf(
            AudioTrackUIState(0, "01. Дубляж (RUS)", "rus"),
            AudioTrackUIState(1, "02. Многоголосый (ENG)", "eng"),
        )

        val index = resolver.findAudioTrackIndex(
            tracks = tracks,
            preferredLabel = "01. Оригинал (ENG)",
            preferredLang = "eng",
            preferOriginal = true,
        )

        assertEquals(1, index)
    }

    @Test
    fun withoutOriginalPreference_theRememberedLanguageStillWins() {
        val tracks = listOf(
            AudioTrackUIState(0, "01. Дубляж (ENG)", "eng"),
            AudioTrackUIState(1, "02. Оригинал (KOR)", "kor", isOriginal = true),
        )

        val index = resolver.findAudioTrackIndex(
            tracks = tracks,
            preferredLabel = "01. Дубляж (ENG)",
            preferredLang = "eng",
        )

        assertEquals(0, index)
    }
}
