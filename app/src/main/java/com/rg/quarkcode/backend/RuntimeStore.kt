package com.rg.quarkcode.backend

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.runtimeDataStore by preferencesDataStore(name = "quark_runtime")

data class RuntimePrefs(
    val backendEnabled: Boolean = false,
    val runtimeVersion: String? = null,
    val debianVersion: String? = null,
    val opencodeVersion: String? = null,
    val serverPassword: String = ""
)

/**
 * On-device backend prefs. The APK stays lean: the opencode ELF + tools are
 * downloaded on first opt-in (Device tab), never bundled.
 */
class RuntimeStore(private val context: Context) {

    private object Keys {
        val BACKEND_ENABLED = booleanPreferencesKey("backend_enabled")
        val RUNTIME_VERSION = stringPreferencesKey("runtime_version")
        val DEBIAN_VERSION = stringPreferencesKey("debian_version")
        val OPENCODE_VERSION = stringPreferencesKey("opencode_version")
        val DEBIAN_ASSET = stringPreferencesKey("debian_asset")
        val OPENCODE_ASSET = stringPreferencesKey("opencode_asset")
        val SERVER_PASSWORD = stringPreferencesKey("server_password")
    }

    val prefs: Flow<RuntimePrefs> = context.runtimeDataStore.data.map { p ->
        RuntimePrefs(
            backendEnabled = p[Keys.BACKEND_ENABLED] == true,
            runtimeVersion = p[Keys.RUNTIME_VERSION],
            debianVersion = p[Keys.DEBIAN_VERSION],
            opencodeVersion = p[Keys.OPENCODE_VERSION],
            serverPassword = p[Keys.SERVER_PASSWORD] ?: ""
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.runtimeDataStore.edit { it[Keys.BACKEND_ENABLED] = value }
    }

    suspend fun setVersion(version: String?) {
        context.runtimeDataStore.edit {
            if (version == null) it.remove(Keys.RUNTIME_VERSION) else it[Keys.RUNTIME_VERSION] = version
        }
    }

    suspend fun setDebianVersion(version: String?) {
        context.runtimeDataStore.edit {
            if (version == null) it.remove(Keys.DEBIAN_VERSION) else it[Keys.DEBIAN_VERSION] = version
        }
    }

    suspend fun setOpencodeVersion(version: String?) {
        context.runtimeDataStore.edit {
            if (version == null) it.remove(Keys.OPENCODE_VERSION) else it[Keys.OPENCODE_VERSION] = version
        }
    }

    /** Last resolved assets — retries reuse them without another API call. */
    suspend fun saveDebianAsset(asset: DebianAsset) {
        context.runtimeDataStore.edit {
            it[Keys.DEBIAN_ASSET] = assetJson.encodeToString(asset)
        }
    }

    suspend fun cachedDebianAsset(): DebianAsset? {
        val raw: String = context.runtimeDataStore.data.map { it[Keys.DEBIAN_ASSET] }.first() ?: return null
        return runCatching { assetJson.decodeFromString<DebianAsset>(raw) }.getOrNull()
    }

    suspend fun saveOpencodeAsset(asset: OpencodeAsset) {
        context.runtimeDataStore.edit {
            it[Keys.OPENCODE_ASSET] = assetJson.encodeToString(asset)
        }
    }

    suspend fun cachedOpencodeAsset(): OpencodeAsset? {
        val raw: String = context.runtimeDataStore.data.map { it[Keys.OPENCODE_ASSET] }.first() ?: return null
        return runCatching { assetJson.decodeFromString<OpencodeAsset>(raw) }.getOrNull()
    }

    suspend fun clearOpencodeAsset() {
        context.runtimeDataStore.edit { it.remove(Keys.OPENCODE_ASSET) }
    }

    companion object {
        private val assetJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }

    /** Stable per-install password, generated once and reused across restarts. */
    suspend fun password(): String {
        val existing = context.runtimeDataStore.data.map { it[Keys.SERVER_PASSWORD] }.first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString().replace("-", "").take(24)
        context.runtimeDataStore.edit { it[Keys.SERVER_PASSWORD] = fresh }
        return fresh
    }
}
