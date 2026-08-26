package com.kino.puber.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BluetoothAudioDelayTest {

    @Test
    fun increaseAndDecrease_useTenMillisecondStepsAndClampAtLimits() {
        assertEquals(BluetoothAudioDelay.fromMilliseconds(-10), BluetoothAudioDelay.OFF.decrease())
        assertEquals(BluetoothAudioDelay.OFF, BluetoothAudioDelay.fromMilliseconds(-10).increase())
        assertEquals(BluetoothAudioDelay.MS_500, BluetoothAudioDelay.MS_500.increase())
        assertEquals(BluetoothAudioDelay.NEGATIVE_MS_500, BluetoothAudioDelay.NEGATIVE_MS_500.decrease())
    }

    @Test
    fun fromMilliseconds_preservesFineValuesAndClampsCorruptStoredValue() {
        assertEquals(BluetoothAudioDelay.MS_200, BluetoothAudioDelay.fromMilliseconds(200))
        assertEquals(BluetoothAudioDelay.NEGATIVE_MS_200, BluetoothAudioDelay.fromMilliseconds(-200))
        assertEquals(175, BluetoothAudioDelay.fromMilliseconds(175).milliseconds)
        assertEquals(BluetoothAudioDelay.MS_500, BluetoothAudioDelay.fromMilliseconds(5_000))
    }
}
