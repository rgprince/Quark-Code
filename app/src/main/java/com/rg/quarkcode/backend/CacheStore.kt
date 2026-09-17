package com.rg.quarkcode.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rg.quarkcode.chat.CatalogModel
import com.rg.quarkcode.chat.ProviderOption
import com.rg.quarkcode.chat.RecentSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.cacheDataStore by preferencesDataStore(name = "quark_cache")

/**
 * Instant-launch illusion: the last-known model catalog + chat names, painted
 * immediately on open while the backend loads, then replaced by live truth.
 * Written on every successful catalog/recents load, so closing the app always
 * leaves a fresh snapshot behind.
 */
@Serializable
data class CachedSnapshot(
    val recents: List<RecentSession> = emptyList(),
    val catalog: List<CatalogModel> = emptyList(),
    val hiddenModels: List<CatalogModel> = emptyList(),
    val providers: List<ProviderOption> = emptyList(),
    val selectedModelKey: String = "auto",
    val selectedProviderId: String? = null,
    val modelLabel: String = "Auto (server default)",
    val variants: List<String> = emptyList()
)

class CacheStore(private val context: Context) {

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("snapshot")
    }

    suspend fun loadSnapshot(): CachedSnapshot? {
        val raw = runCatching {
            context.cacheDataStore.data.first()[Keys.SNAPSHOT]
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString<CachedSnapshot>(raw) }.getOrNull()
    }

    suspend fun saveSnapshot(snapshot: CachedSnapshot) {
        runCatching {
            context.cacheDataStore.edit { prefs ->
                prefs[Keys.SNAPSHOT] = json.encodeToString(snapshot)
            }
        }
    }

    suspend fun clear() {
        runCatching {
            context.cacheDataStore.edit { prefs -> prefs.remove(Keys.SNAPSHOT) }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
