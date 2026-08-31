package com.kino.puber.data.repository

import android.content.Context
import androidx.core.content.edit

internal class MyShowsPreferencesRepository(
    context: Context,
    private val cryptoPreferences: ICryptoPreferenceRepository,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val token: String?
        get() = cryptoPreferences.getMyShowsToken()?.trim()?.takeIf(String::isNotEmpty)

    val isConnected: Boolean
        get() = token != null

    var isSyncEnabled: Boolean
        get() = isConnected && preferences.getBoolean(SYNC_ENABLED_KEY, true)
        set(value) {
            preferences.edit { putBoolean(SYNC_ENABLED_KEY, value && isConnected) }
        }

    fun connect(token: String) {
        cryptoPreferences.saveMyShowsToken(token.trim())
        preferences.edit { putBoolean(SYNC_ENABLED_KEY, true) }
    }

    fun disconnect() {
        cryptoPreferences.clearMyShowsToken()
        preferences.edit { putBoolean(SYNC_ENABLED_KEY, false) }
    }

    private companion object {
        const val PREFS_NAME = "MYSHOWS_PREFERENCES"
        const val SYNC_ENABLED_KEY = "watched_sync_enabled"
    }
}
