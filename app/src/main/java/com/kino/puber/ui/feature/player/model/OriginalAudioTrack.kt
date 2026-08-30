package com.kino.puber.ui.feature.player.model

/**
 * Markers KinoPub uses for the untranslated track, in the API audio type titles and in the
 * HLS manifest labels they are rendered into.
 */
private val ORIGINAL_AUDIO_MARKERS = listOf("оригинал", "ориг.", "original")

/**
 * Recognizes the original audio track from any descriptor the stream carries. The original
 * track speaks a different language in every title, so it can only be matched by kind.
 */
internal fun isOriginalAudioTrack(vararg descriptors: String?): Boolean {
    return descriptors.any { descriptor ->
        descriptor != null && ORIGINAL_AUDIO_MARKERS.any { descriptor.contains(it, ignoreCase = true) }
    }
}
