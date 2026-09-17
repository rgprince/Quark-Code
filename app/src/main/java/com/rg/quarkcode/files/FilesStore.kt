package com.rg.quarkcode.files

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.filesDataStore by preferencesDataStore(name = "quark_files")

/** Persisted Files browser prefs (DataStore, same pattern as ThemeStore). */
class FilesStore(private val context: Context) {

    private object Keys {
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SORT = stringPreferencesKey("sort")
        val LAST_PATH = stringPreferencesKey("last_path")
    }

    val showHidden: Flow<Boolean> = context.filesDataStore.data.map { prefs ->
        prefs[Keys.SHOW_HIDDEN] ?: false
    }

    val sort: Flow<FilesRepo.SortMode> = context.filesDataStore.data.map { prefs ->
        FilesRepo.sortModeOf(prefs[Keys.SORT])
    }

    /** Last workspace relative path, so Files reopens where you left it. */
    val lastPath: Flow<String> = context.filesDataStore.data.map { prefs ->
        prefs[Keys.LAST_PATH] ?: ""
    }

    suspend fun setShowHidden(value: Boolean) {
        context.filesDataStore.edit { prefs -> prefs[Keys.SHOW_HIDDEN] = value }
    }

    suspend fun setSort(mode: FilesRepo.SortMode) {
        context.filesDataStore.edit { prefs -> prefs[Keys.SORT] = mode.name }
    }

    suspend fun setLastPath(relative: String) {
        context.filesDataStore.edit { prefs -> prefs[Keys.LAST_PATH] = relative }
    }
}
