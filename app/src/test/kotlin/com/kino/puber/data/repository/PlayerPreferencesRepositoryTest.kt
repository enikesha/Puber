package com.kino.puber.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerPreferencesRepositoryTest {

    @Test
    fun media3PlaybackPreferences_useCurrentBehaviorDefaults() {
        val repository = fixture().repository

        assertTrue(repository.discardEmbeddedArtworkMetadata)
        assertFalse(repository.hagcPlaybackEnabled)
    }

    @Test
    fun media3PlaybackPreferences_persistIndependentValues() {
        val fixture = fixture()

        fixture.repository.discardEmbeddedArtworkMetadata = false
        fixture.repository.hagcPlaybackEnabled = true

        val restoredRepository = PlayerPreferencesRepository(fixture.context)
        assertFalse(restoredRepository.discardEmbeddedArtworkMetadata)
        assertTrue(restoredRepository.hagcPlaybackEnabled)
    }

    @Test
    fun trackPreferences_applyAcrossDifferentItems() {
        val fixture = fixture()

        fixture.repository.savePreferredAudioTrack(audioLang = "rus", audioLabel = "Russian")
        fixture.repository.savePreferredSubtitleTrack(subtitleLang = "eng", subtitleUrl = "english.vtt")

        val repository = PlayerPreferencesRepository(fixture.context)
        assertEquals("rus", repository.getPreferredAudioLang(itemId = 42))
        assertEquals("rus", repository.getPreferredAudioLang(itemId = 99))
        assertEquals("Russian", repository.getPreferredAudioLabel(itemId = 99))
        assertEquals("eng", repository.getPreferredSubtitleLang(itemId = 42))
        assertEquals("eng", repository.getPreferredSubtitleLang(itemId = 99))
        assertEquals("english.vtt", repository.getPreferredSubtitleUrl(itemId = 99))
    }

    @Test
    fun subtitleOff_isPersistedAsExplicitGlobalPreference() {
        val fixture = fixture()

        fixture.repository.savePreferredSubtitleTrack(subtitleLang = "", subtitleUrl = "")

        val repository = PlayerPreferencesRepository(fixture.context)
        assertEquals("", repository.getPreferredSubtitleLang(itemId = 42))
        assertEquals("", repository.getPreferredSubtitleLang(itemId = 99))
        assertEquals("", repository.getPreferredSubtitleUrl(itemId = 99))
    }

    private fun fixture(): Fixture {
        val preferences = TestPreferences()
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(any(), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            context = context,
            repository = PlayerPreferencesRepository(context),
        )
    }

    private data class Fixture(
        val context: Context,
        val repository: PlayerPreferencesRepository,
    )
}

private class TestPreferences {
    private val booleanValues: MutableMap<String, Boolean> = mutableMapOf()
    private val intValues: MutableMap<String, Int> = mutableMapOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val editor: SharedPreferences.Editor = mockk()
    private val stringValues: MutableMap<String, String?> = mutableMapOf()

    init {
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            booleanValues[firstArg()] ?: secondArg()
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            intValues[firstArg()] ?: secondArg()
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            booleanValues[firstArg()] = secondArg()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            intValues[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } returns Unit
        every { sharedPreferences.getString(any(), any()) } answers {
            if (stringValues.containsKey(firstArg())) stringValues[firstArg()] else secondArg()
        }
        every { sharedPreferences.contains(any()) } answers {
            stringValues.containsKey(firstArg())
        }
        every { editor.putString(any(), any()) } answers {
            stringValues[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            stringValues.remove(firstArg())
            editor
        }
    }
}
