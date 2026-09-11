package com.rg.quarkcode.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// OkHttp-direct Serve client, ported from AndCode's OpenCodeApiClient.
// Every failure carries the server's own error text (first 3 lines, 240
// chars) so a 400 never again reads as a bare number.
class ServeApi(
    host: String,
    private val username: String,
    private val password: String
) {

    class HttpException(val status: Int, message: String) : Exception(message)

    private val baseUrl = OpenCodeUrl.normalize(host).toHttpUrl()

    @PublishedApi
    internal val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val streamHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @PublishedApi
    internal fun builder(path: String, query: Map<String, String?> = emptyMap()): Request.Builder {
        val resolved = baseUrl.resolve(path.removePrefix("/"))
            ?: error("Invalid OpenCode API path")
        val url = resolved.newBuilder().apply {
            query.forEach { (name, value) ->
                if (!value.isNullOrBlank()) addQueryParameter(name, value)
            }
        }.build()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (password.isNotBlank()) {
                    header("Authorization", Credentials.basic(username.ifBlank { "opencode" }, password))
                }
            }
    }

    @PublishedApi
    internal fun fail(status: Int, body: String): Nothing {
        if (status == 401 || status == 403) {
            throw HttpException(status, "OpenCode request failed (HTTP $status)")
        }
        val snippet = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString(" ")
            .take(240)
        throw HttpException(
            status,
            if (snippet.isBlank()) {
                "OpenCode request failed (HTTP $status)"
            } else {
                "OpenCode request failed (HTTP $status): $snippet"
            }
        )
    }

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap()
    ): T = withContext(Dispatchers.IO) {
        val request = builder(path, query).get().build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) fail(response.code, text)
            json.decodeFromString<T>(text)
        }
    }

    suspend inline fun <reified T> getList(path: String, query: Map<String, String?> = emptyMap()): List<T> =
        get(path, query)

    suspend inline fun <reified T> post(path: String, body: JsonObject): T =
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) fail(response.code, text)
                json.decodeFromString<T>(text)
            }
        }

    suspend fun postUnit(path: String, body: JsonObject) {
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(response.code, response.body?.string().orEmpty())
                }
            }
        }
    }

    suspend inline fun <reified T> patch(path: String, body: JsonObject): T =
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .patch(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) fail(response.code, text)
                json.decodeFromString<T>(text)
            }
        }

    suspend inline fun <reified T> put(path: String, body: JsonObject): T =
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .put(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) fail(response.code, text)
                json.decodeFromString<T>(text)
            }
        }

    suspend fun putUnit(path: String, body: JsonObject) {
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .put(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(response.code, response.body?.string().orEmpty())
                }
            }
        }
    }

    suspend inline fun <reified T> delete(path: String): T =
        withContext(Dispatchers.IO) {
            val request = builder(path).delete().build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) fail(response.code, text)
                json.decodeFromString<T>(text)
            }
        }

    suspend fun deleteUnit(path: String) {
        withContext(Dispatchers.IO) {
            val request = builder(path).delete().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(response.code, response.body?.string().orEmpty())
                }
            }
        }
    }

    fun encodePath(value: String): String =
        value.replace("/", "%2F").replace("?", "%3F").replace("#", "%23")

    companion object {
        @PublishedApi
        internal val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        @PublishedApi
        internal val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
