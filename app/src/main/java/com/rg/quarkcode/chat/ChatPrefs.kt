package com.rg.quarkcode.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatPrefsDataStore by preferencesDataStore(name = "quark_chat_prefs")

// Small chat prefs (AndCode Chat-settings parity): auto-expand reasoning,
// detailed tool cards. Separate store so model reconcile stays untouched.
class ChatPrefs(private val context: Context) {

    private object Keys {
        val DETAILED_TOOLS = booleanPreferencesKey("detailed_tools")
        val SEND_BEHAVIOR = stringPreferencesKey("send_behavior")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val PLAYFUL_STATUS = booleanPreferencesKey("playful_status")
        val SHOW_THOUGHTS = booleanPreferencesKey("show_thoughts")
        val COMFORTABLE = booleanPreferencesKey("comfortable_density")
        val SHOW_TIMESTAMPS = booleanPreferencesKey("show_timestamps")
    }

    data class Prefs(
        val detailed: Boolean = false,
        val sendBehavior: String = "interrupt",
        val textScale: Float = 1f,
        val autoScroll: Boolean = true,
        val playfulStatus: Boolean = true,
        val showThoughts: Boolean = true,
        val comfortable: Boolean = false,
        val showTimestamps: Boolean = true
    )

    val prefs: Flow<Prefs> = context.chatPrefsDataStore.data.map {
        Prefs(
            detailed = it[Keys.DETAILED_TOOLS] == true,
            sendBehavior = it[Keys.SEND_BEHAVIOR] ?: "interrupt",
            textScale = (it[Keys.TEXT_SCALE] ?: 1f).coerceIn(0.8f, 1.3f),
            autoScroll = it[Keys.AUTO_SCROLL] ?: true,
            playfulStatus = it[Keys.PLAYFUL_STATUS] ?: true,
            showThoughts = it[Keys.SHOW_THOUGHTS] ?: true,
            comfortable = it[Keys.COMFORTABLE] == true,
            showTimestamps = it[Keys.SHOW_TIMESTAMPS] ?: true
        )
    }

    suspend fun setDetailed(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.DETAILED_TOOLS] = value }
    }

    suspend fun setSendBehavior(value: String) {
        context.chatPrefsDataStore.edit { it[Keys.SEND_BEHAVIOR] = value }
    }

    suspend fun setTextScale(value: Float) {
        context.chatPrefsDataStore.edit { it[Keys.TEXT_SCALE] = value.coerceIn(0.8f, 1.3f) }
    }

    suspend fun setAutoScroll(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.AUTO_SCROLL] = value }
    }

    suspend fun setPlayfulStatus(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.PLAYFUL_STATUS] = value }
    }

    suspend fun setShowThoughts(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.SHOW_THOUGHTS] = value }
    }

    suspend fun setComfortable(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.COMFORTABLE] = value }
    }

    suspend fun setShowTimestamps(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.SHOW_TIMESTAMPS] = value }
    }
}
