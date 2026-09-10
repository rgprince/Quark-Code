package com.quark.agent.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quarkDataStore by preferencesDataStore(name = "quark_connection")

data class Connection(
    val host: String = "http://localhost:4096",
    val username: String = "opencode",
    val password: String = ""
)

class ConnectionStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    val connection: Flow<Connection> = context.quarkDataStore.data.map { prefs ->
        Connection(
            host = prefs[Keys.HOST] ?: "http://localhost:4096",
            username = prefs[Keys.USERNAME] ?: "opencode",
            password = prefs[Keys.PASSWORD] ?: ""
        )
    }

    suspend fun save(connection: Connection) {
        context.quarkDataStore.edit { prefs ->
            prefs[Keys.HOST] = connection.host
            prefs[Keys.USERNAME] = connection.username
            prefs[Keys.PASSWORD] = connection.password
        }
    }
}
