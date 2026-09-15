package com.rg.quarkcode.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modelDataStore by preferencesDataStore(name = "quark_models")

// Model selection + persistence. Key format everywhere: "providerId/modelId".
class ModelStore(private val context: Context) {

    private object Keys {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
        val MODEL_ID = stringPreferencesKey("model_id")
        val FAVORITES = stringSetPreferencesKey("favorite_models")
        val HIDDEN = stringSetPreferencesKey("hidden_models")
        val RECENTS = stringPreferencesKey("recent_models")
    }

    data class Selection(
        val providerId: String? = null,
        val modelId: String? = null,
        val favorites: Set<String> = emptySet(),
        val hidden: Set<String> = emptySet(),
        val recents: List<String> = emptyList()
    )

    val selection: Flow<Selection> = context.modelDataStore.data.map { prefs ->
        Selection(
            providerId = prefs[Keys.PROVIDER_ID],
            modelId = prefs[Keys.MODEL_ID],
            favorites = prefs[Keys.FAVORITES] ?: emptySet(),
            hidden = prefs[Keys.HIDDEN] ?: emptySet(),
            recents = prefs[Keys.RECENTS]
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    suspend fun selectModel(providerId: String?, modelId: String?) {
        context.modelDataStore.edit { prefs ->
            if (providerId == null) prefs.remove(Keys.PROVIDER_ID) else prefs[Keys.PROVIDER_ID] = providerId
            if (modelId == null) prefs.remove(Keys.MODEL_ID) else prefs[Keys.MODEL_ID] = modelId
            if (providerId != null && modelId != null) {
                val key = "$providerId/$modelId"
                val updated = (listOf(key) + (prefs[Keys.RECENTS]
                    ?.split("\n")
                    ?.filter { it.isNotBlank() && it != key }
                    ?: emptyList())).take(MAX_RECENT_MODELS)
                prefs[Keys.RECENTS] = updated.joinToString("\n")
            }
        }
    }

    suspend fun toggleFavorite(providerId: String, modelId: String): Set<String> {
        val key = "$providerId/$modelId"
        var result = emptySet<String>()
        context.modelDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES] ?: emptySet()
            result = if (current.contains(key)) current - key else current + key
            prefs[Keys.FAVORITES] = result
        }
        return result
    }

    suspend fun toggleHidden(providerId: String, modelId: String): Set<String> {
        val key = "$providerId/$modelId"
        var result = emptySet<String>()
        context.modelDataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN] ?: emptySet()
            result = if (current.contains(key)) current - key else current + key
            prefs[Keys.HIDDEN] = result
        }
        return result
    }

    // Reconcile priority: stored-if-connected -> most-recent valid ->
    // "opencode"-if-connected -> first provider. Model: stored -> recent ->
    // catalog default -> first active. Agent stays untouched (local labels).
    suspend fun reconcile(catalog: ProviderCatalog): Pair<String?, String?> {
        if (catalog.all.isEmpty()) return null to null
        val current = selection.first()
        val connected = catalog.connected.toSet()
        val providerId = when {
            current.providerId != null && connected.contains(current.providerId) -> current.providerId
            else -> {
                val recent = current.recents.firstOrNull { key ->
                    connected.contains(key.substringBefore('/')) &&
                        catalog.all.any { it.id == key.substringBefore('/') }
                }?.substringBefore('/')
                recent
                    ?: if (connected.contains("opencode")) "opencode"
                    else catalog.all.firstOrNull()?.id
            }
        } ?: return null to null
        val provider = catalog.all.firstOrNull { it.id == providerId } ?: return null to null
        val recentForProvider = current.recents
            .filter { it.substringBefore('/') == providerId }
            .map { it.substringAfter('/') }
            .firstOrNull { id -> provider.models.containsKey(id) }
        val modelId = when {
            current.modelId != null && provider.models.containsKey(current.modelId) -> current.modelId
            recentForProvider != null -> recentForProvider
            else -> catalog.default[providerId]
                ?: provider.models.values.firstOrNull { it.isActive() }?.id
                ?: provider.models.keys.firstOrNull()
        }
        selectModel(providerId, modelId)
        return providerId to modelId
    }

    companion object {
        const val MAX_RECENT_MODELS = 3
    }
}
