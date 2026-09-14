package com.kino.puber.ui.feature.player.vm

import android.media.AudioDeviceInfo
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BluetoothAudioDelayTest {

    @Test
    fun isBluetoothOutputType_recognizesClassicAndSupportedBleOutputs() {
        assertTrue(
            BluetoothAudioRouteDetector.isBluetoothOutputType(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                Build.VERSION_CODES.N,
            )
        )
        assertTrue(
            BluetoothAudioRouteDetector.isBluetoothOutputType(
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                Build.VERSION_CODES.S,
            )
        )
        assertFalse(
            BluetoothAudioRouteDetector.isBluetoothOutputType(
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                Build.VERSION_CODES.R,
            )
        )
        assertFalse(
            BluetoothAudioRouteDetector.isBluetoothOutputType(
                AudioDeviceInfo.TYPE_HDMI,
                Build.VERSION_CODES.TIRAMISU,
            )
        )
    }

    @Test
    fun delayedVideoPresentationTimeUs_addsConfiguredCompensation() {
        assertEquals(1_250_000L, delayedVideoPresentationTimeUs(1_000_000L, 250))
        assertEquals(1_000_000L, delayedVideoPresentationTimeUs(1_000_000L, 0))
        assertEquals(1_000_000L, delayedVideoPresentationTimeUs(1_000_000L, -250))
        assertEquals(C.TIME_UNSET, delayedVideoPresentationTimeUs(C.TIME_UNSET, 250))
    }

    @Test
    fun audioDelayProcessor_insertsSilenceForNegativeDelayAndKeepsInputQueued() {
        val controller = PlaybackDelayController(initialDelayMs = -50)
        val processor = configuredProcessor(controller)
        val input = ByteBuffer.allocateDirect(400).apply {
            repeat(capacity()) { put(1) }
            flip()
        }

        processor.queueInput(input)

        val silence = processor.output
        assertEquals(200, silence.remaining())
        assertTrue(generateSequence { if (silence.hasRemaining()) silence.get() else null }.all { it == 0.toByte() })
        assertEquals(400, input.remaining())
    }

    @Test
    fun audioDelayProcessor_dropsQueuedPcmWhenNegativeDelayIsReduced() {
        val controller = PlaybackDelayController(initialDelayMs = -50)
        val processor = configuredProcessor(controller)
        val input = ByteBuffer.allocateDirect(400).apply {
            repeat(capacity()) { put(1) }
            flip()
        }
        processor.queueInput(input)
        processor.output
        processor.queueInput(input)
        processor.output

        controller.delayMs = 0
        val nextInput = ByteBuffer.allocateDirect(400).apply {
            repeat(capacity()) { put(2) }
            flip()
        }
        processor.queueInput(nextInput)

        assertEquals(200, processor.output.remaining())
        assertEquals(0, nextInput.remaining())
    }

    private fun configuredProcessor(controller: PlaybackDelayController): AudioDelayProcessor {
        return AudioDelayProcessor(controller).apply {
            configure(AudioProcessor.AudioFormat(1_000, 2, C.ENCODING_PCM_16BIT))
            flush(AudioProcessor.StreamMetadata.DEFAULT)
        }
    }
}
