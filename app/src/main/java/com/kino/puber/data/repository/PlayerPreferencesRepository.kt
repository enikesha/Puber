package com.kino.puber.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.domain.model.BluetoothAudioDelay
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.domain.model.TrackPreferenceScope
import com.kino.puber.ui.feature.player.model.BufferPreset

class PlayerPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var trackPreferenceScope: TrackPreferenceScope
        get() {
            val ordinal = prefs.getInt(KEY_TRACK_PREFERENCE_SCOPE, DEFAULT_TRACK_PREFERENCE_SCOPE.ordinal)
            return TrackPreferenceScope.entries.getOrElse(ordinal) { DEFAULT_TRACK_PREFERENCE_SCOPE }
        }
        set(value) = prefs.edit().putInt(KEY_TRACK_PREFERENCE_SCOPE, value.ordinal).apply()

    fun getPreferredAudioLang(itemId: Int): String? {
        return readScoped(itemId, KEY_AUDIO_LANG, KEY_AUDIO_LANG_PREFIX)
    }

    fun getPreferredAudioLabel(itemId: Int): String? {
        return readScoped(itemId, KEY_AUDIO_LABEL, KEY_AUDIO_LABEL_PREFIX)
    }

    /**
     * True when the remembered audio track was the original one. The original track carries a
     * different language in every title, so it is restored by kind instead of by language.
     */
    fun isPreferredAudioOriginal(itemId: Int): Boolean {
        return readScoped(itemId, KEY_AUDIO_ORIGINAL, KEY_AUDIO_ORIGINAL_PREFIX).toBoolean()
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return readScoped(itemId, KEY_SUBTITLE_LANG, KEY_SUBTITLE_LANG_PREFIX)
    }

    fun getPreferredSubtitleUrl(itemId: Int): String? {
        return readScoped(itemId, KEY_SUBTITLE_URL, KEY_SUBTITLE_URL_PREFIX)
    }

    fun savePreferredAudioTrack(
        itemId: Int,
        audioLang: String?,
        audioLabel: String?,
        isOriginal: Boolean,
    ) {
        prefs.edit().apply {
            writeScoped(itemId, KEY_AUDIO_LANG, KEY_AUDIO_LANG_PREFIX, audioLang)
            writeScoped(itemId, KEY_AUDIO_LABEL, KEY_AUDIO_LABEL_PREFIX, audioLabel)
            writeScoped(
                itemId,
                KEY_AUDIO_ORIGINAL,
                KEY_AUDIO_ORIGINAL_PREFIX,
                isOriginal.toString().takeIf { isOriginal },
            )
            apply()
        }
    }

    fun savePreferredSubtitleTrack(itemId: Int, subtitleLang: String, subtitleUrl: String) {
        prefs.edit().apply {
            writeScoped(itemId, KEY_SUBTITLE_LANG, KEY_SUBTITLE_LANG_PREFIX, subtitleLang)
            writeScoped(itemId, KEY_SUBTITLE_URL, KEY_SUBTITLE_URL_PREFIX, subtitleUrl)
            apply()
        }
    }

    /**
     * Reads the entry the active scope owns and falls back to the other one, so switching the
     * scope keeps whatever was already remembered instead of starting from nothing. The
     * per-video scope owns the item entry alone and has nothing to fall back to.
     */
    private fun readScoped(itemId: Int, globalKey: String, itemKeyPrefix: String): String? {
        val key = scopedKeys(itemId, globalKey, itemKeyPrefix).firstOrNull { prefs.contains(it) }
            ?: return null
        return prefs.getString(key, null)
    }

    private fun SharedPreferences.Editor.writeScoped(
        itemId: Int,
        globalKey: String,
        itemKeyPrefix: String,
        value: String?,
    ) {
        val key = scopedKeys(itemId, globalKey, itemKeyPrefix).first()
        if (value != null) putString(key, value) else remove(key)
    }

    private fun scopedKeys(itemId: Int, globalKey: String, itemKeyPrefix: String): List<String> {
        val itemKey = "$itemKeyPrefix$itemId"
        return when (trackPreferenceScope) {
            TrackPreferenceScope.GLOBAL -> listOf(globalKey, itemKey)
            TrackPreferenceScope.PER_TITLE -> listOf(itemKey, globalKey)
            TrackPreferenceScope.PER_VIDEO -> listOf(itemKey)
        }
    }

    fun getSubtitleSize(): SubtitleSize {
        val ordinal = prefs.getInt(KEY_SUBTITLE_SIZE, SubtitleSize.MEDIUM.ordinal)
        return SubtitleSize.entries.getOrElse(ordinal) { SubtitleSize.MEDIUM }
    }

    fun saveSubtitleSize(size: SubtitleSize) {
        prefs.edit().putInt(KEY_SUBTITLE_SIZE, size.ordinal).apply()
    }

    var skipIntroEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_INTRO, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_INTRO, value).apply()

    var skipRecapEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_RECAP, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_RECAP, value).apply()

    var skipCreditsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CREDITS, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CREDITS, value).apply()

    var debugOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_OVERLAY, value).apply()

    var preferSurroundAudio: Boolean
        get() = prefs.getBoolean(KEY_PREFER_SURROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_SURROUND, value).apply()

    var bufferPreset: BufferPreset
        get() {
            val ordinal = prefs.getInt(KEY_BUFFER_PRESET, BufferPreset.AUTO.ordinal)
            return BufferPreset.entries.getOrElse(ordinal) { BufferPreset.AUTO }
        }
        set(value) = prefs.edit().putInt(KEY_BUFFER_PRESET, value.ordinal).apply()

    var fastDnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAST_DNS, true)
        set(value) = prefs.edit().putBoolean(KEY_FAST_DNS, value).apply()

    var watchedIndicatorsEnabled: Boolean
        get() = prefs.getBoolean(KEY_WATCHED_INDICATORS, true)
        set(value) = prefs.edit().putBoolean(KEY_WATCHED_INDICATORS, value).apply()

    var discardEmbeddedArtworkMetadata: Boolean
        get() = prefs.getBoolean(KEY_DISCARD_EMBEDDED_ARTWORK_METADATA, true)
        set(value) = prefs.edit().putBoolean(KEY_DISCARD_EMBEDDED_ARTWORK_METADATA, value).apply()

    var hagcPlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAGC_PLAYBACK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HAGC_PLAYBACK_ENABLED, value).apply()

    var bluetoothAudioDelay: BluetoothAudioDelay
        get() = BluetoothAudioDelay.fromMilliseconds(
            prefs.getInt(KEY_BLUETOOTH_AUDIO_DELAY_MS, BluetoothAudioDelay.OFF.milliseconds)
        )
        set(value) = prefs.edit().putInt(KEY_BLUETOOTH_AUDIO_DELAY_MS, value.milliseconds).apply()

    var bluetoothSyncControlsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH_SYNC_CONTROLS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUETOOTH_SYNC_CONTROLS_ENABLED, value).apply()

    private companion object {
        val DEFAULT_TRACK_PREFERENCE_SCOPE = TrackPreferenceScope.PER_VIDEO
        const val PREFS_NAME = "player_preferences"
        const val KEY_AUDIO_LANG_PREFIX = "audio_lang_"
        const val KEY_AUDIO_LABEL_PREFIX = "audio_label_"
        const val KEY_SUBTITLE_LANG_PREFIX = "subtitle_lang_"
        const val KEY_SUBTITLE_URL_PREFIX = "subtitle_url_"
        const val KEY_AUDIO_LANG = "preferred_audio_lang"
        const val KEY_AUDIO_LABEL = "preferred_audio_label"
        const val KEY_SUBTITLE_LANG = "preferred_subtitle_lang"
        const val KEY_SUBTITLE_URL = "preferred_subtitle_url"
        const val KEY_AUDIO_ORIGINAL = "preferred_audio_original"
        const val KEY_AUDIO_ORIGINAL_PREFIX = "audio_original_"
        const val KEY_TRACK_PREFERENCE_SCOPE = "track_preference_scope"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_SKIP_INTRO = "skip_intro_enabled"
        const val KEY_SKIP_RECAP = "skip_recap_enabled"
        const val KEY_SKIP_CREDITS = "skip_credits_enabled"
        const val KEY_DEBUG_OVERLAY = "debug_overlay_enabled"
        const val KEY_PREFER_SURROUND = "prefer_surround_audio"
        const val KEY_BUFFER_PRESET = "buffer_preset"
        const val KEY_FAST_DNS = "fast_dns_enabled"
        const val KEY_WATCHED_INDICATORS = "watched_indicators_enabled"
        const val KEY_DISCARD_EMBEDDED_ARTWORK_METADATA = "discard_embedded_artwork_metadata"
        const val KEY_HAGC_PLAYBACK_ENABLED = "hagc_playback_enabled"
        const val KEY_BLUETOOTH_AUDIO_DELAY_MS = "bluetooth_audio_delay_ms"
        const val KEY_BLUETOOTH_SYNC_CONTROLS_ENABLED = "bluetooth_sync_controls_enabled"
    }
}
