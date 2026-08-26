package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AudioTrackPreferenceResolverTest {

    private val resolver = AudioTrackPreferenceResolver()

    @Test
    fun findSubtitleTrackIndex_usesLanguageForUrlLessManifestTrack() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "en", url = "", playerTrackId = "hls-english"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "eng",
            preferredUrl = "",
        )

        assertEquals(1, result)
    }

    @Test
    fun findSubtitleTrackIndex_preservesExplicitOffPreference() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "eng", url = "", playerTrackId = "hls-english"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "",
            preferredUrl = "",
        )

        assertEquals(0, result)
    }

    @Test
    fun findSubtitleTrackIndex_prefersCurrentPlayerIdentity_overLanguageFallback() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "", playerTrackId = "full"),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "forced"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = "",
            preferredPlayerTrackId = "forced",
        )

        assertEquals(2, result)
    }

    @Test
    fun findSubtitleTrackIndex_prefersSavedUrl_overCurrentPlayerIdentity() {
        val externalUrl = "https://cdn.test/subtitles/russian.vtt"
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = externalUrl),
            subtitleTrack(index = 2, language = "rus", url = "", playerTrackId = "hls-russian"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "rus",
            preferredUrl = externalUrl,
            preferredPlayerTrackId = "hls-russian",
        )

        assertEquals(1, result)
    }

    @Test
    fun findSubtitleTrackIndex_returnsNoMatch_untilPreferredManifestLanguageAppears() {
        val tracks = listOf(
            subtitleTrack(index = 0, language = "", url = ""),
            subtitleTrack(index = 1, language = "rus", url = "https://cdn.test/subtitles/russian.vtt"),
        )

        val result = resolver.findSubtitleTrackIndex(
            tracks = tracks,
            preferredLang = "ukr",
            preferredUrl = "",
        )

        assertEquals(-1, result)
    }

    private fun subtitleTrack(
        index: Int,
        language: String,
        url: String,
        playerTrackId: String? = null,
    ) = SubtitleTrackUIState(
        index = index,
        label = "Track $index",
        language = language,
        url = url,
        playerTrackId = playerTrackId,
        playerGroupIndex = playerTrackId?.let { index - 1 },
        playerTrackIndex = playerTrackId?.let { 0 },
    )
}
