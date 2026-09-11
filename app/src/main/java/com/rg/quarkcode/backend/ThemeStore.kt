package com.rg.quarkcode.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "quark_theme")

enum class ThemeMode { SYSTEM, DARK, LIGHT, DYNAMIC }

class ThemeStore(private val context: Context) {

    private object Keys {
        val MODE = stringPreferencesKey("mode")
    }

    val mode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.MODE] ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.MODE] = mode.name
        }
    }
}
