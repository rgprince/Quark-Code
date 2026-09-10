package com.rg.quarkcode.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// SSE stream ported from AndCode's OpenCodeApiClient.events():
// GET global/event first, fallback to /event on 400/404/405/501.
// Manual data:-accumulate parse; envelope {payload} vs direct object.
class EventStream(
    private val host: String,
    private val username: String,
    private val password: String
) {

    data class ServerEvent(
        val payload: JsonObject,
        val directory: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    var running = false
        private set

    fun stop() {
        running = false
    }

    suspend fun collect(onEvent: suspend (ServerEvent) -> Unit) {
        running = true
        var backoff = 500L
        val paths = listOf("global/event", "event")
        var pathIndex = 0
        while (running) {
            try {
                streamPath(paths[pathIndex], onEvent)
                backoff = 500L
            } catch (err: Exception) {
                if (err is CancellationException || !running) break
                val code = (err as? HttpStatusException)?.code
                if (code in listOf(400, 404, 405, 501) && pathIndex == 0) {
                    pathIndex = 1
                    continue
                }
                if (code != null && code < 500) break
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(backoff)
                }
                backoff = (backoff * 2).coerceAtMost(15_000L)
            }
        }
        running = false
    }

    private suspend fun streamPath(
        path: String,
        onEvent: suspend (ServerEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conn = (URL(host.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            if (password.isNotEmpty()) {
                setRequestProperty("Authorization", Credentials.basic(username, password))
            }
            connectTimeout = 15_000
            readTimeout = 0
        }
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw HttpStatusException(conn.responseCode)
        }
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        try {
            val dataLines = mutableListOf<String>()
            while (running) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) {
                    if (dataLines.isNotEmpty()) {
                        parseBlock(dataLines.joinToString("\n"))?.let { onEvent(it) }
                        dataLines.clear()
                    }
                } else if (line.startsWith("data:")) {
                    dataLines.add(line.removePrefix("data:").trim())
                }
            }
        } finally {
            reader.close()
            conn.disconnect()
        }
    }

    private fun parseBlock(data: String): ServerEvent? = runCatching {
        val element = json.parseToJsonElement(data).jsonObject
        if (element.containsKey("payload")) {
            val payload = element["payload"]?.jsonObject ?: return null
            val directory = element["directory"]?.jsonPrimitive?.content
            ServerEvent(payload, directory)
        } else {
            ServerEvent(element, null)
        }
    }.getOrNull()

    fun eventType(payload: JsonObject): String =
        payload["type"]?.jsonPrimitive?.content ?: ""
}

class HttpStatusException(val code: Int) : Exception("HTTP $code")
