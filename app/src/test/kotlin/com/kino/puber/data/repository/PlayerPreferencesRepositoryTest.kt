package com.kino.puber.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.domain.model.BluetoothAudioDelay
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
        assertEquals(BluetoothAudioDelay.OFF, repository.bluetoothAudioDelay)
        assertFalse(repository.bluetoothSyncControlsEnabled)
    }

    @Test
    fun media3PlaybackPreferences_persistIndependentValues() {
        val fixture = fixture()

        fixture.repository.discardEmbeddedArtworkMetadata = false
        fixture.repository.hagcPlaybackEnabled = true
        fixture.repository.bluetoothAudioDelay = BluetoothAudioDelay.NEGATIVE_MS_250
        fixture.repository.bluetoothSyncControlsEnabled = true

        val restoredRepository = PlayerPreferencesRepository(fixture.context)
        assertFalse(restoredRepository.discardEmbeddedArtworkMetadata)
        assertTrue(restoredRepository.hagcPlaybackEnabled)
        assertEquals(BluetoothAudioDelay.NEGATIVE_MS_250, restoredRepository.bluetoothAudioDelay)
        assertTrue(restoredRepository.bluetoothSyncControlsEnabled)
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
    }
}
