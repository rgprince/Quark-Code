package com.rg.quarkcode.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// Kai9000-style MCP client: Streamable HTTP ONLY, in-process, no stdio child.
// Flow: POST initialize -> POST notifications/initialized -> POST tools/list.
// Session tracked via the Mcp-Session-Id response header. 60s tool timeout.
class McpClient(
    private val baseUrl: String,
    private val headers: Map<String, String> = emptyMap()
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var sessionId: String? = null

    @Serializable
    data class RpcRequest(
        val jsonrpc: String = "2.0",
        val id: Long,
        val method: String,
        val params: JsonObject? = null
    )

    @Serializable
    data class ToolMeta(
        val name: String,
        val description: String = "",
        val inputSchema: JsonObject? = null
    )

    private var nextId = 1L

    private fun post(payload: String): String {
        val conn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
        }
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        return unwrapSse(text)
    }

    // Servers may answer SSE (event:/data: lines) or plain JSON.
    private fun unwrapSse(text: String): String {
        if (!text.contains("data:")) return text
        return text.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .firstOrNull { it.startsWith("{") } ?: text
    }

    fun connect(): Boolean = runCatching {
        val init = RpcRequest(id = nextId++, method = "initialize")
        post(json.encodeToString(init))
        val notified = RpcRequest(id = nextId++, method = "notifications/initialized")
        runCatching { post(json.encodeToString(notified)) }
        true
    }.getOrDefault(false)

    fun listTools(): List<ToolMeta> = runCatching {
        val req = RpcRequest(id = nextId++, method = "tools/list")
        val raw = post(json.encodeToString(req))
        val result = json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject
            ?: return emptyList()
        val tools = result["tools"]?.toString() ?: return emptyList()
        json.decodeFromString<List<ToolMeta>>(tools)
    }.getOrDefault(emptyList())

    fun callTool(name: String, arguments: JsonObject): String = runCatching {
        val params = buildJsonObject(name, arguments)
        val req = RpcRequest(id = nextId++, method = "tools/call", params = params)
        post(json.encodeToString(req))
    }.getOrDefault("")

    private fun buildJsonObject(name: String, arguments: JsonObject): JsonObject =
        JsonObject(mapOf("name" to JsonPrimitive(name), "arguments" to arguments))

    fun toolNames(): List<String> = listTools().map { it.name }
}
