package com.rg.quarkcode.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rg.quarkcode.backend.EventStream
import com.rg.quarkcode.backend.MessagePart
import com.rg.quarkcode.backend.MessageWithParts
import com.rg.quarkcode.backend.ModelRef
import com.rg.quarkcode.backend.ModelStore
import com.rg.quarkcode.backend.OpenCodeProvider
import com.rg.quarkcode.backend.OpenCodeService
import com.rg.quarkcode.backend.PermissionResponse
import com.rg.quarkcode.backend.ProviderCatalog
import com.rg.quarkcode.backend.SendMessageBody
import com.rg.quarkcode.backend.ServeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private val app = application
    private var service: OpenCodeService? = null
    private var modelStore: ModelStore? = null
    private var sessionId: String? = null
    private var pollJob: Job? = null
    private var eventJob: Job? = null
    private var eventStream: EventStream? = null
    private var selProvider: String? = null
    private var selModel: String? = null
    private var providers: List<OpenCodeProvider> = emptyList()
    private val countedTokens = mutableSetOf<String>()

    fun attach(host: String, username: String, password: String) {
        service = ServeClient.service(host, username, password)
        modelStore = ModelStore(app)
        sessionId = null
        countedTokens.clear()
        uiState = uiState.copy(
            connected = true,
            messages = emptyList(),
            sessionTodos = emptyList()
        )
        loadCatalog()
        loadRecents()
        startEvents(host, username, password)
    }

    // ---- Catalog + selection (ported from AndCode) ----

    private fun loadCatalog() {
        viewModelScope.launch {
            val api = service ?: return@launch
            val catalog = runCatching { withContext(Dispatchers.IO) { api.providers() } }
                .getOrNull()
                ?.takeIf { it.all.isNotEmpty() }
                ?: runCatching { withContext(Dispatchers.IO) { api.configProviders() } }
                    .getOrNull()
                    ?.let { fallback ->
                        ProviderCatalog(
                            all = fallback.providers.map { entry ->
                                OpenCodeProvider(
                                    id = entry.id,
                                    name = entry.name,
                                    models = entry.models.associate { model ->
                                        model.id to com.rg.quarkcode.backend.OpenCodeModel(
                                            id = model.id,
                                            name = model.name
                                        )
                                    }
                                )
                            },
                            default = fallback.default,
                            connected = fallback.providers.map { it.id }
                        )
                    }
                ?.takeIf { it.all.isNotEmpty() }
                ?: return@launch
            providers = catalog.all
            val store = modelStore ?: return@launch
            val (providerId, modelId) = store.reconcile(catalog)
            selProvider = providerId
            selModel = modelId
            val snapshot = runCatching {
                withContext(Dispatchers.IO) { first(store.selection) }
            }.getOrNull()
            val favs = snapshot?.favorites ?: emptySet()
            val recents = snapshot?.recents ?: emptyList()
            val label = labelFor(providerId, modelId)
            val limit = catalog.all.firstOrNull { it.id == providerId }
                ?.models?.get(modelId)?.limit?.context?.takeIf { it > 0 }
            uiState = uiState.copy(
                catalog = catalog.all.flatMap { provider ->
                    provider.models.values
                        .filter { it.status != "deprecated" }
                        .map { model ->
                            CatalogModel(
                                id = "${provider.id}/${model.id.ifEmpty { "?" }}",
                                label = "${provider.displayName()} / ${model.displayName()}",
                                providerId = provider.id,
                                modelId = model.id
                            )
                        }
                },
                model = label,
                selectedModelKey = if (providerId != null && modelId != null) {
                    "$providerId/$modelId"
                } else {
                    "auto"
                },
                favorites = favs,
                modelRecents = recents,
                stats = if (limit != null) {
                    uiState.stats.copy(limit = limit)
                } else {
                    uiState.stats
                }
            )
        }
    }

    private fun labelFor(providerId: String?, modelId: String?): String {
        if (providerId == null || modelId == null) return "Auto (server default)"
        val provider = providers.firstOrNull { it.id == providerId }
        val model = provider?.models?.get(modelId)
        return "${provider?.displayName() ?: providerId} / ${model?.displayName() ?: modelId}"
    }

    fun onModelChange(id: String) {
        val providerId = id.substringBefore('/')
        val modelId = id.substringAfter('/')
        if (providerId.isBlank() || modelId.isBlank() || modelId == "?") return
        viewModelScope.launch {
            modelStore?.selectModel(providerId, modelId)
            selProvider = providerId
            selModel = modelId
            val snapshot = runCatching {
                withContext(Dispatchers.IO) { first(modelStore!!.selection) }
            }.getOrNull()
            uiState = uiState.copy(
                model = labelFor(providerId, modelId),
                selectedModelKey = id,
                modelSheet = false,
                modelRecents = snapshot?.recents ?: uiState.modelRecents
            )
        }
    }

    fun toggleFavorite(id: String) {
        val providerId = id.substringBefore('/')
        val modelId = id.substringAfter('/')
        if (providerId.isBlank() || modelId.isBlank()) return
        viewModelScope.launch {
            val updated = modelStore?.toggleFavorite(providerId, modelId) ?: return@launch
            uiState = uiState.copy(favorites = updated)
        }
    }

    private fun selectedRef(): ModelRef? =
        if (!selProvider.isNullOrBlank() && !selModel.isNullOrBlank()) {
            ModelRef(selProvider!!, selModel!!)
        } else {
            null
        }

    // ---- Sessions ----

    private fun loadRecents() {
        viewModelScope.launch {
            val sessions = runCatching {
                withContext(Dispatchers.IO) { service?.sessions() }
            }.getOrNull() ?: return@launch
            uiState = uiState.copy(
                recents = sessions
                    .filter { it.time?.archived == null }
                    .take(20)
                    .map { RecentSession(it.id, it.title.ifEmpty { it.id.take(8) }) }
            )
        }
    }

    fun openSession(id: String) {
        pollJob?.cancel()
        sessionId = id
        countedTokens.clear()
        uiState = uiState.copy(
            sending = true,
            messages = emptyList(),
            sessionTodos = emptyList(),
            spacesSheet = false
        )
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) { service?.messages(id) }
            }
            loaded.onSuccess { list ->
                val mapped = (list ?: emptyList()).map { mapServerMessage(it) }
                mapped.forEach { countedTokens.add(it.id) }
                uiState = uiState.copy(sending = false, messages = mapped)
                refreshTodos()
            }.onFailure { err ->
                uiState = uiState.copy(
                    sending = false,
                    messages = listOf(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = "Could not load session: ${err.message ?: "unknown error"}",
                            isError = true
                        )
                    )
                )
            }
        }
    }

    // ---- Send (async + checked, AndCode-style) ----

    fun send() {
        val text = uiState.input.trim()
        if (text.isEmpty() || uiState.sending) return
        startRun(text)
    }

    fun retry(messageId: String) {
        if (uiState.sending) return
        val failed = uiState.messages.firstOrNull { it.id == messageId } ?: return
        val text = uiState.messages
            .takeWhile { it.id != messageId }
            .lastOrNull { it.isUser }
            ?.text ?: failed.text
        startRun(text)
    }

    private fun startRun(text: String) {
        pollJob?.cancel()
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = text
        )
        uiState = uiState.copy(
            input = "",
            sending = true,
            messages = uiState.messages.filterNot { it.isError } + userMessage,
            stats = uiState.stats.copy(used = uiState.stats.used + estimateTokens(text))
        )
        pollJob = viewModelScope.launch {
            val failed = runCatching {
                val (client, id) = withContext(Dispatchers.IO) {
                    val api = service ?: error("Not connected")
                    val sid = sessionId ?: api.createSession().also { sessionId = it.id }.id
                    val body = SendMessageBody(
                        model = selectedRef(),
                        parts = listOf(MessagePart(text = text))
                    )
                    val response = api.sendMessageAsync(sid, body)
                    if (!response.isSuccessful) error("Server ${response.code()}")
                    api to sid
                }
                pollUntilIdle(client, id)
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(sending = false)
                }
                refreshCost()
                refreshTodos()
                loadRecents()
            }
            failed.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) return@launch
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = "Request failed: ${err.message ?: "unknown error"}",
                    isError = true
                )
                uiState = uiState.copy(sending = false, messages = uiState.messages + assistant)
            }
        }
    }

    // Exit only on real idleness: idle status AND a fresh assistant message,
    // or several consecutive idle rounds. Unknown status never means idle.
    private suspend fun pollUntilIdle(
        api: OpenCodeService,
        id: String
    ) {
        var lastSize = -1
        var lastParts = -1
        var idleRounds = 0
        var grew = false
        repeat(150) {
            delay(2000)
            val status = runCatching {
                withContext(Dispatchers.IO) { api.statuses()[id]?.type }
            }.getOrNull()
            val list = runCatching {
                withContext(Dispatchers.IO) { api.messages(id) }
            }.getOrNull() ?: emptyList()
            val parts = list.sumOf { it.parts.size }
            if (list.size != lastSize || parts != lastParts) {
                if (lastSize >= 0 && list.size > lastSize) grew = true
                lastSize = list.size
                lastParts = parts
                idleRounds = 0
                val mapped = list.map { mapServerMessage(it) }
                val fresh = mapped.filter { countedTokens.add(it.id) }
                val added = fresh.sumOf { estimateTokens(it.text) }
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        messages = mapped,
                        stats = uiState.stats.copy(
                            used = uiState.stats.used + added,
                            output = uiState.stats.output + added
                        )
                    )
                }
            } else {
                idleRounds++
            }
            val lastIsAssistant = list.lastOrNull()?.info?.role == "assistant"
            if (status == "idle" && ((grew && lastIsAssistant) || idleRounds >= 4)) return
        }
    }

    fun abort() {
        val id = sessionId ?: return
        pollJob?.cancel()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { service?.abortSession(id) }
            }
            uiState = uiState.copy(sending = false)
            refreshTodos()
        }
    }

    // ---- Live events (ported from AndCode) ----

    private fun startEvents(host: String, username: String, password: String) {
        eventJob?.cancel()
        eventStream?.stop()
        val stream = EventStream(host, username, password)
        eventStream = stream
        eventJob = viewModelScope.launch {
            stream.collect { event ->
                withContext(Dispatchers.Main) { handleEvent(event) }
            }
        }
    }

    private fun handleEvent(event: EventStream.ServerEvent) {
        val payload = event.payload
        val type = payload["type"]?.jsonPrimitive?.content ?: return
        val session = payload["sessionID"]?.jsonPrimitive?.content
            ?: payload["sessionId"]?.jsonPrimitive?.content
        when {
            type.startsWith("message.") && (session == null || session == sessionId) -> {
                refreshMessages()
            }
            type == "permission.asked" && (session == null || session == sessionId) -> {
                val id = payload["id"]?.jsonPrimitive?.content ?: return
                val tool = payload["permission"]?.jsonPrimitive?.content ?: "tool"
                val summary = summarizePermission(payload)
                val request = PermissionRequest(id = id, tool = tool, summary = summary, remember = false)
                uiState = uiState.copy(
                    sending = false,
                    messages = uiState.messages + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = "Permission needed: $tool",
                        permission = request
                    )
                )
            }
            type == "question.asked" && (session == null || session == sessionId) -> {
                val questions = payload["questions"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["question"]?.jsonPrimitive?.content }
                    ?: emptyList()
                if (questions.isNotEmpty()) {
                    uiState = uiState.copy(
                        messages = uiState.messages + ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = questions.joinToString("\n\n")
                        )
                    )
                }
            }
            (type == "session.idle" || type == "session.error") &&
                (session == null || session == sessionId) -> {
                uiState = uiState.copy(sending = false)
                refreshMessages()
                refreshTodos()
            }
            type == "session.created" -> loadRecents()
        }
    }

    private fun summarizePermission(payload: JsonObject): String {
        val patterns = payload["patterns"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        val metadata = payload["metadata"]?.toString()?.take(300) ?: ""
        return (patterns + metadata).filter { it.isNotBlank() }.joinToString("\n").ifEmpty {
            "The agent wants to run this action."
        }
    }

    private fun refreshMessages() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val list = runCatching {
                withContext(Dispatchers.IO) { service?.messages(id) }
            }.getOrNull() ?: return@launch
            val mapped = list.map { mapServerMessage(it) }
            val fresh = mapped.filter { countedTokens.add(it.id) }
            val added = fresh.sumOf { estimateTokens(it.text) }
            uiState = uiState.copy(
                messages = mapped,
                stats = uiState.stats.copy(
                    used = uiState.stats.used + added,
                    output = uiState.stats.output + added
                )
            )
        }
    }

    // ---- Mapping / helpers ----

    private fun mapServerMessage(message: MessageWithParts): ChatMessage {
        val isUser = message.info.role == "user"
        val texts = message.parts.filter { it.type == "text" }.map { it.text }
        val thinking = message.parts.filter { it.type == "reasoning" }.map { it.text }
        val tools = message.parts.filter { it.type == "tool" }
        val body = (texts + thinking.map { "[thinking] $it" })
            .joinToString("\n\n")
            .ifEmpty {
                when {
                    isUser -> ""
                    tools.isNotEmpty() -> "(working…)"
                    else -> "(no text reply)"
                }
            }
        return ChatMessage(
            id = message.info.id,
            isUser = isUser,
            text = body,
            toolRuns = tools.size,
            filesRead = tools.count { it.tool in FILE_TOOLS },
            todos = parseTodos(body),
            toolLog = tools.take(20).map { tool ->
                "${tool.tool ?: "tool"}: ${(tool.output ?: "").take(400)}"
            }
        )
    }

    private fun refreshCost() {
        val id = sessionId ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { service?.sessions() }
            }.onSuccess { sessions ->
                sessions?.firstOrNull { it.id == id }?.let { info ->
                    uiState = uiState.copy(stats = uiState.stats.copy(cost = info.cost))
                }
            }
        }
    }

    private fun refreshTodos() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val todos = runCatching {
                withContext(Dispatchers.IO) { service?.todos(id) }
            }.getOrNull() ?: return@launch
            uiState = uiState.copy(
                sessionTodos = todos.mapIndexed { index, todo ->
                    TodoItem(
                        id = todo.id.ifEmpty { "srv-$index" },
                        text = todo.content,
                        done = todo.status.equals("completed", ignoreCase = true) ||
                            todo.status.equals("done", ignoreCase = true)
                    )
                }
            )
        }
    }

    private fun estimateTokens(text: String): Long = (text.length / 4).toLong()

    private fun parseTodos(body: String): List<TodoItem> {
        val pattern = Regex("""^-\s*\[( |x|X)]\s+(.+)$""", RegexOption.MULTILINE)
        return pattern.findAll(body).mapIndexed { index, match ->
            TodoItem(
                id = "todo-$index-${match.value.hashCode()}",
                text = match.groupValues[2].trim(),
                done = match.groupValues[1].trim().equals("x", ignoreCase = true)
            )
        }.toList()
    }

    override fun onCleared() {
        eventJob?.cancel()
        eventStream?.stop()
        super.onCleared()
    }

    companion object {
        private val FILE_TOOLS = setOf("read", "glob", "grep", "ls", "find")
    }
}
