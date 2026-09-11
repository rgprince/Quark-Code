package com.rg.quarkcode.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatPrefsDataStore by preferencesDataStore(name = "quark_chat_prefs")

// Small chat prefs (AndCode Chat-settings parity): auto-expand reasoning,
// detailed tool cards. Separate store so model reconcile stays untouched.
class ChatPrefs(private val context: Context) {

    private object Keys {
        val AUTO_EXPAND = booleanPreferencesKey("auto_expand_reasoning")
        val DETAILED_TOOLS = booleanPreferencesKey("detailed_tools")
        val SEND_BEHAVIOR = stringPreferencesKey("send_behavior")
    }

    data class Prefs(
        val autoExpand: Boolean = false,
        val detailed: Boolean = false,
        val sendBehavior: String = "interrupt"
    )

    val prefs: Flow<Prefs> = context.chatPrefsDataStore.data.map {
        Prefs(
            autoExpand = it[Keys.AUTO_EXPAND] == true,
            detailed = it[Keys.DETAILED_TOOLS] == true,
            sendBehavior = it[Keys.SEND_BEHAVIOR] ?: "interrupt"
        )
    }

    suspend fun setAutoExpand(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.AUTO_EXPAND] = value }
    }

    suspend fun setDetailed(value: Boolean) {
        context.chatPrefsDataStore.edit { it[Keys.DETAILED_TOOLS] = value }
    }

    suspend fun setSendBehavior(value: String) {
        context.chatPrefsDataStore.edit { it[Keys.SEND_BEHAVIOR] = value }
    }
}
