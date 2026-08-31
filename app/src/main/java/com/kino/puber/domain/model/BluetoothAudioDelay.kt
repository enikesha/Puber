package com.kino.puber.domain.model

import androidx.compose.runtime.Immutable

@Immutable
@JvmInline
value class BluetoothAudioDelay private constructor(val milliseconds: Int) {

    fun increase(): BluetoothAudioDelay = fromMilliseconds(milliseconds + STEP_MS)

    fun decrease(): BluetoothAudioDelay = fromMilliseconds(milliseconds - STEP_MS)

    companion object {
        const val STEP_MS = 10
        const val MAX_DELAY_MS = 500

        val OFF = BluetoothAudioDelay(0)
        val MS_200 = BluetoothAudioDelay(200)
        val MS_500 = BluetoothAudioDelay(500)
        val NEGATIVE_MS_200 = BluetoothAudioDelay(-200)
        val NEGATIVE_MS_250 = BluetoothAudioDelay(-250)
        val NEGATIVE_MS_500 = BluetoothAudioDelay(-500)

        fun fromMilliseconds(milliseconds: Int): BluetoothAudioDelay {
            return BluetoothAudioDelay(milliseconds.coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS))
        }
    }
}
