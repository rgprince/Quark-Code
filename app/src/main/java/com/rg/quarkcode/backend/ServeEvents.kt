package com.rg.quarkcode.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.Request
import java.io.IOException

// Typed server events. Envelope {payload} (global) vs direct.
sealed interface ServerEvent {
    data object Connected : ServerEvent
    data class MessageUpdated(val info: MessageInfo) : ServerEvent
    data class PartUpdated(val part: MessagePart) : ServerEvent
    data class PartDelta(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val field: String,
        val delta: String
    ) : ServerEvent

    data class PermissionAsked(val ask: PermissionAsk) : ServerEvent
    data class PermissionReplied(val sessionId: String, val requestId: String) : ServerEvent
    data class QuestionAsked(val ask: QuestionAsk) : ServerEvent
    data class SessionIdle(val sessionId: String) : ServerEvent
    data class SessionCreated(val info: SessionInfo) : ServerEvent
    data class SessionUpdated(val info: SessionInfo) : ServerEvent
    data class StatusChanged(val sessionId: String, val status: String) : ServerEvent
    data class SessionError(val sessionId: String?, val message: String?, val name: String? = null) : ServerEvent {
        val isAbort: Boolean
            get() = name == "MessageAbortedError" || name == "AbortError"
    }

    data class Unknown(val type: String) : ServerEvent
}

fun ServerEvent.sessionIdOrNull(): String? = when (this) {
    is ServerEvent.MessageUpdated -> info.sessionId.ifEmpty { null }
    is ServerEvent.PartUpdated -> part.sessionId
    is ServerEvent.PartDelta -> sessionId
    is ServerEvent.PermissionAsked -> ask.sessionId
    is ServerEvent.PermissionReplied -> sessionId
    is ServerEvent.QuestionAsked -> ask.sessionId
    is ServerEvent.SessionIdle -> sessionId
    is ServerEvent.SessionCreated -> info.id
    is ServerEvent.SessionUpdated -> info.id
    is ServerEvent.StatusChanged -> sessionId
    is ServerEvent.SessionError -> sessionId
    ServerEvent.Connected -> null
    is ServerEvent.Unknown -> null
}

object EventParser {

    fun parse(raw: String): ServerEvent {
        val envelope = runCatching {
            ServeApi.json.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return ServerEvent.Unknown("invalid")
        val root = envelope["payload"] as? JsonObject ?: envelope
        val type = root["type"]?.jsonPrimitive?.content
            ?: return ServerEvent.Unknown("missing-type")
        val props = root["properties"] as? JsonObject ?: JsonObject(emptyMap())
        return runCatching {
            when (type) {
                "server.connected" -> ServerEvent.Connected
                "message.updated" -> ServerEvent.MessageUpdated(
                    ServeApi.json.decodeFromJsonElement(
                        MessageInfo.serializer(),
                        props["info"]!!.jsonObject
                    )
                )
                "message.part.updated" -> ServerEvent.PartUpdated(
                    ServeApi.json.decodeFromJsonElement(
                        MessagePart.serializer(),
                        props["part"]!!.jsonObject
                    )
                )
                "message.part.delta" -> ServerEvent.PartDelta(
                    sessionId = props["sessionID"]!!.jsonPrimitive.content,
                    messageId = props["messageID"]!!.jsonPrimitive.content,
                    partId = props["partID"]!!.jsonPrimitive.content,
                    field = props["field"]!!.jsonPrimitive.content,
                    delta = props["delta"]!!.jsonPrimitive.content
                )
                "permission.asked" -> ServerEvent.PermissionAsked(
                    PermissionAsk(
                        id = props["id"]!!.jsonPrimitive.content,
                        sessionId = props["sessionID"]!!.jsonPrimitive.content,
                        permission = props["permission"]!!.jsonPrimitive.content,
                        patterns = (props["patterns"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.content }
                            .orEmpty(),
                        metadata = (props["metadata"] as? JsonObject)
                            ?.entries?.associate { (k, v) -> k to v }
                            .orEmpty()
                    )
                )
                "question.asked" -> {
                    val questions = (props["questions"] as? JsonArray)
                        ?.mapNotNull { parseQuestion(it) }
                        .orEmpty()
                    require(questions.isNotEmpty())
                    ServerEvent.QuestionAsked(
                        QuestionAsk(
                            id = props["id"]!!.jsonPrimitive.content,
                            sessionId = props["sessionID"]!!.jsonPrimitive.content,
                            questions = questions,
                            directory = (envelope["directory"] as? JsonPrimitive)?.content
                        )
                    )
                }
                "permission.replied" -> ServerEvent.PermissionReplied(
                    sessionId = props["sessionID"]!!.jsonPrimitive.content,
                    requestId = props["requestID"]!!.jsonPrimitive.content
                )
                "session.idle" -> ServerEvent.SessionIdle(
                    props["sessionID"]!!.jsonPrimitive.content
                )
                "session.created" -> ServerEvent.SessionCreated(
                    ServeApi.json.decodeFromJsonElement(
                        SessionInfo.serializer(),
                        props["info"]!!.jsonObject
                    )
                )
                "session.updated" -> ServerEvent.SessionUpdated(
                    ServeApi.json.decodeFromJsonElement(
                        SessionInfo.serializer(),
                        props["info"]!!.jsonObject
                    )
                )
                "session.status" -> ServerEvent.StatusChanged(
                    sessionId = props["sessionID"]!!.jsonPrimitive.content,
                    status = props["status"]!!.jsonObject["type"]!!.jsonPrimitive.content
                )
                "session.error" -> {
                    val errorObject = props["error"] as? JsonObject
                    ServerEvent.SessionError(
                        sessionId = (props["sessionID"] as? JsonPrimitive)?.content,
                        message = describeError(props["error"]),
                        name = (errorObject?.get("name") as? JsonPrimitive)?.content
                    )
                }
                else -> ServerEvent.Unknown(type)
            }
        }.getOrElse { ServerEvent.Unknown(type) }
    }

    private fun describeError(element: kotlinx.serialization.json.JsonElement?): String? {
        if (element == null) return null
        (element as? JsonPrimitive)?.let { return it.content }
        val error = element as? JsonObject ?: return element.toString()
        val message = ((error["data"] as? JsonObject)?.get("message") as? JsonPrimitive)
            ?.content?.takeIf { it.isNotBlank() }
        val name = (error["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return when {
            message != null && name != null -> "$name: $message"
            message != null -> message
            name != null -> name
            else -> error.toString()
        }
    }

    private fun parseQuestion(element: kotlinx.serialization.json.JsonElement): QuestionPrompt? =
        when {
            element is JsonPrimitive && element.isString ->
                QuestionPrompt(question = element.content)
            element is JsonObject -> {
                val question = (element["question"] as? JsonPrimitive)?.content
                    ?: return null
                QuestionPrompt(
                    question = question,
                    header = (element["header"] as? JsonPrimitive)?.content,
                    options = (element["options"] as? JsonArray)
                        ?.mapNotNull {
                            val label = (it as? JsonObject)?.get("label") as? JsonPrimitive
                                ?: (it as? JsonPrimitive)?.takeIf { p -> p.isString }
                            label?.content?.let { text ->
                                QuestionOption(
                                    label = text,
                                    description = ((it as? JsonObject)
                                        ?.get("description") as? JsonPrimitive)?.content
                                )
                            }
                        }
                        .orEmpty(),
                    multiple = (element["multiple"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    custom = (element["custom"] as? JsonPrimitive)?.booleanOrNull ?: true
                )
            }
            else -> null
        }
}

// Event stream with global/event primary + /event fallback.
class ServeEventStream(
    private val host: String,
    private val username: String,
    private val password: String,
    private val api: ServeApi
) {

    @Volatile
    private var path = "global/event"

    fun events(): Flow<ServerEvent> =
        kotlinx.coroutines.flow.flow { emitAll(singleStream(path)) }.retryWhen { cause, attempt ->
            if (path == "global/event" && cause is ServeApi.HttpException &&
                cause.status in setOf(400, 404, 405, 501)
            ) {
                path = "event"
                return@retryWhen true
            }
            val retryable = cause !is ServeApi.HttpException || cause.status >= 500
            if (!retryable) return@retryWhen false
            val backoff = (500L * (1L shl attempt.toInt().coerceAtMost(5))).coerceAtMost(15_000L)
            delay(backoff)
            true
        }

    private fun singleStream(eventPath: String): Flow<ServerEvent> =
        channelFlow {
            val request = Request.Builder()
                .url(apiBase(eventPath))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .apply {
                    if (password.isNotEmpty()) {
                        header(
                            "Authorization",
                            Credentials.basic(username.ifBlank { "opencode" }, password)
                        )
                    }
                }
                .get()
                .build()
            val call = api.streamHttp.newCall(request)
            val reader = launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ServeApi.HttpException(
                                response.code,
                                "OpenCode event stream failed (HTTP ${response.code})"
                            )
                        }
                        val source = requireNotNull(response.body) { "empty event body" }.source()
                        source.use {
                            val data = StringBuilder()
                            while (isActive) {
                                val line = it.readUtf8Line() ?: break
                                when {
                                    line.isEmpty() -> {
                                        if (data.isNotEmpty()) {
                                            send(EventParser.parse(data.toString()))
                                            data.setLength(0)
                                        }
                                    }
                                    line.startsWith("data:") -> {
                                        if (data.isNotEmpty()) data.append('\n')
                                        data.append(line.removePrefix("data:").removePrefix(" "))
                                    }
                                }
                            }
                            if (data.isNotEmpty()) send(EventParser.parse(data.toString()))
                        }
                        throw IOException("OpenCode event stream closed")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    close(error)
                }
            }
            awaitClose {
                call.cancel()
                reader.cancel()
            }
        }.buffer(512)

    private fun apiBase(eventPath: String): String =
        host.trimEnd('/') + "/" + eventPath
}
