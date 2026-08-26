package com.kino.puber.ui.feature.player.vm

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.nio.ByteBuffer

internal object BluetoothAudioRouteDetector {

    fun hasConnectedOutput(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                isBluetoothOutputType(device.type, Build.VERSION.SDK_INT)
            }
        }.getOrDefault(false)
    }

    internal fun isBluetoothOutputType(type: Int, sdkInt: Int): Boolean {
        return when {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            sdkInt >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID -> true
            sdkInt >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> true
            sdkInt >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            sdkInt >= Build.VERSION_CODES.TIRAMISU && type == AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
            else -> false
        }
    }
}

@OptIn(UnstableApi::class)
internal class BluetoothSyncRenderersFactory(
    context: Context,
    private val delayController: PlaybackDelayController,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(AudioDelayProcessor(delayController)))
            .build()
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val initialRendererCount = out.size
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )

        val relativeRendererIndex = out
            .subList(initialRendererCount, out.size)
            .indexOfFirst { it is MediaCodecVideoRenderer }
        if (relativeRendererIndex == -1) {
            return
        }
        val defaultRendererIndex = initialRendererCount + relativeRendererIndex

        val builder = MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)

        out[defaultRendererIndex] = DelayedMediaCodecVideoRenderer(builder, delayController)
    }
}

internal class PlaybackDelayController(initialDelayMs: Int) {

    @Volatile
    var delayMs: Int = initialDelayMs
}

@OptIn(UnstableApi::class)
private class DelayedMediaCodecVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder,
    private val delayController: PlaybackDelayController,
) : MediaCodecVideoRenderer(builder) {

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format,
    ): Boolean {
        return super.processOutputBuffer(
            positionUs,
            elapsedRealtimeUs,
            codec,
            buffer,
            bufferIndex,
            bufferFlags,
            sampleCount,
            delayedVideoPresentationTimeUs(bufferPresentationTimeUs, delayController.delayMs),
            isDecodeOnlyBuffer,
            isLastBuffer,
            format,
        )
    }
}

internal fun delayedVideoPresentationTimeUs(presentationTimeUs: Long, delayMs: Int): Long {
    if (presentationTimeUs == C.TIME_UNSET) {
        return presentationTimeUs
    }
    return presentationTimeUs + delayMs.coerceAtLeast(0) * 1_000L
}

/**
 * Keeps negative sync values on the audio path. Increasing the requested audio delay inserts
 * silence; reducing it drops the same amount of not-yet-played PCM so the change takes effect
 * without rebuilding the player or seeking the movie.
 */
@OptIn(UnstableApi::class)
internal class AudioDelayProcessor(
    private val delayController: PlaybackDelayController,
) : BaseAudioProcessor() {

    private var appliedDelayBytes = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val targetDelayBytes = targetDelayBytes()
        when {
            appliedDelayBytes < targetDelayBytes -> {
                val silenceByteCount = targetDelayBytes - appliedDelayBytes
                val outputBuffer = replaceOutputBuffer(silenceByteCount)
                repeat(silenceByteCount) { outputBuffer.put(0) }
                outputBuffer.flip()
                appliedDelayBytes = targetDelayBytes
            }

            appliedDelayBytes > targetDelayBytes -> {
                val bytesToDrop = minOf(
                    appliedDelayBytes - targetDelayBytes,
                    inputBuffer.remaining().alignedToFrame(),
                )
                inputBuffer.position(inputBuffer.position() + bytesToDrop)
                appliedDelayBytes -= bytesToDrop
                if (inputBuffer.hasRemaining() && appliedDelayBytes == targetDelayBytes) {
                    copyToOutput(inputBuffer)
                }
            }

            else -> copyToOutput(inputBuffer)
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        appliedDelayBytes = 0
    }

    override fun onReset() {
        appliedDelayBytes = 0
    }

    private fun targetDelayBytes(): Int {
        val delayMs = (-delayController.delayMs).coerceAtLeast(0)
        val bytesPerSecond = inputAudioFormat.sampleRate.toLong() * inputAudioFormat.bytesPerFrame
        return ((bytesPerSecond * delayMs / MILLIS_PER_SECOND) / inputAudioFormat.bytesPerFrame *
            inputAudioFormat.bytesPerFrame).toInt()
    }

    private fun Int.alignedToFrame(): Int {
        return this / inputAudioFormat.bytesPerFrame * inputAudioFormat.bytesPerFrame
    }

    private fun copyToOutput(inputBuffer: ByteBuffer) {
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
