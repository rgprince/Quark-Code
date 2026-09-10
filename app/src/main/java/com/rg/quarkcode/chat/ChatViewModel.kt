package com.rg.quarkcode.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rg.quarkcode.backend.MessagePart
import com.rg.quarkcode.backend.OpenCodeService
import com.rg.quarkcode.backend.PermissionResponse
import com.rg.quarkcode.backend.SendMessageBody
import com.rg.quarkcode.backend.ServeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel : ViewModel() {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var service: OpenCodeService? = null
    private var sessionId: String? = null
    private var pollJob: Job? = null
    private val countedTokens = mutableSetOf<String>()

    fun attach(host: String, username: String, password: String) {
        service = ServeClient.service(host, username, password)
        sessionId = null
        countedTokens.clear()
        uiState = uiState.copy(connected = true, messages = emptyList(), sessionTodos = emptyList())
        loadCatalog()
        loadRecents()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            val response = runCatching {
                withContext(Dispatchers.IO) { service?.providers() }
            }.getOrNull() ?: return@launch
            val flat = response.providers.flatMap { provider ->
                provider.models.map { model ->
                    CatalogModel(
                        id = "${provider.id}/${model.id}",
                        label = "${provider.name.ifEmpty { provider.id }} / ${model.name.ifEmpty { model.id }}",
                        providerId = provider.id,
                        modelId = model.id
                    )
                }
            }
            if (flat.isNotEmpty()) {
                uiState = uiState.copy(catalog = flat)
            }
        }
    }

    private fun loadRecents() {
        viewModelScope.launch {
            val sessions = runCatching {
                withContext(Dispatchers.IO) { service?.sessions() }
            }.getOrNull() ?: return@launch
            uiState = uiState.copy(
                recents = sessions.take(20).map {
                    RecentSession(it.id, it.title.ifEmpty { it.id.take(8) })
                }
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
                uiState = uiState.copy(
                    sending = false,
                    messages = (list ?: emptyList()).map { mapServerMessage(it) }
                )
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

    private fun selectedRef(): com.rg.quarkcode.backend.ModelRef? {
        val found = uiState.catalog.firstOrNull { it.label == uiState.model }
        return if (found != null && found.providerId.isNotEmpty() && found.modelId.isNotEmpty()) {
            com.rg.quarkcode.backend.ModelRef(found.providerId, found.modelId)
        } else {
            null
        }
    }

    fun onInputChange(value: String) {
        uiState = uiState.copy(input = value)
    }

    fun setModelSheet(open: Boolean) {
        uiState = uiState.copy(modelSheet = open)
    }

    fun setSpacesSheet(open: Boolean) {
        uiState = uiState.copy(spacesSheet = open)
    }

    fun setContextSheet(open: Boolean) {
        uiState = uiState.copy(contextSheet = open)
    }

    fun onRuntimeChange(runtime: Runtime) {
        uiState = uiState.copy(runtime = runtime)
    }

    fun onModelChange(model: String) {
        uiState = uiState.copy(model = model, modelSheet = false)
    }

    fun onAgentChange(agent: String) {
        uiState = uiState.copy(agent = agent, spacesSheet = false)
    }

    fun toggleFavorite(model: String) {
        val current = uiState.favorites
        uiState = uiState.copy(
            favorites = if (current.contains(model)) current - model else current + model
        )
    }

    fun toggleTools(messageId: String) {
        val current = uiState.toolsExpanded[messageId] == true
        uiState = uiState.copy(
            toolsExpanded = uiState.toolsExpanded + (messageId to !current)
        )
    }

    fun toggleTodo(messageId: String, todoId: String) {
        uiState = uiState.copy(
            messages = uiState.messages.map { message ->
                if (message.id != messageId) {
                    message
                } else {
                    message.copy(
                        todos = message.todos.map { todo ->
                            if (todo.id != todoId) todo else todo.copy(done = !todo.done)
                        }
                    )
                }
            }
        )
    }

    fun onRememberChange(permissionId: String, remember: Boolean) {
        uiState = uiState.copy(
            messages = uiState.messages.map { message ->
                val permission = message.permission
                if (permission == null || permission.id != permissionId) {
                    message
                } else {
                    message.copy(permission = permission.copy(remember = remember))
                }
            }
        )
    }

    fun allow(permissionId: String) {
        respondPermission(permissionId, "allow")
    }

    fun deny(permissionId: String) {
        respondPermission(permissionId, "deny")
    }

    private fun respondPermission(permissionId: String, response: String) {
        val id = sessionId ?: return
        val remember = uiState.messages
            .mapNotNull { it.permission }
            .firstOrNull { it.id == permissionId }
            ?.remember == true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service?.respondPermission(id, permissionId, PermissionResponse(response, remember))
                }
            }
            uiState = uiState.copy(
                messages = uiState.messages.map { message ->
                    if (message.permission?.id == permissionId) {
                        message.copy(
                            permission = null,
                            text = message.text + "\n\n_" + response.replaceFirstChar { it.uppercase() } + "ed._"
                        )
                    } else {
                        message
                    }
                }
            )
        }
    }

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
                withContext(Dispatchers.IO) {
                    val api = service ?: error("Not connected")
                    val id = sessionId ?: api.createSession().also { sessionId = it.id }.id
                    val body = SendMessageBody(model = selectedRef(), parts = listOf(MessagePart(text = text)))
                    api.sendMessageAsync(id, body)
                    pollUntilIdle(api, id)
                }
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

    // The blocking POST can hang while the server waits on approvals, so V2
    // fires prompt_async and polls status + messages instead.
    private suspend fun pollUntilIdle(
        api: OpenCodeService,
        id: String
    ) {
        var lastCount = -1
        var idleRounds = 0
        repeat(150) {
            delay(2000)
            val status = runCatching { api.statuses()[id]?.type }.getOrNull()
            val list = runCatching { api.messages(id) }.getOrNull() ?: emptyList()
            if (list.size != lastCount) {
                lastCount = list.size
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
            if ((status == null || status == "idle") && idleRounds >= 1 && lastCount >= 0) return
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

    private fun mapServerMessage(message: com.rg.quarkcode.backend.MessageWithParts): ChatMessage {
        val isUser = message.info.role == "user"
        val texts = message.parts.filter { it.type == "text" }.map { it.text }
        val tools = message.parts.filter { it.type == "tool" }
        val body = texts.joinToString("\n\n").ifEmpty { if (isUser) "" else "(no text reply)" }
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

    companion object {
        private val FILE_TOOLS = setOf("read", "glob", "grep", "ls", "find")
    }
}
