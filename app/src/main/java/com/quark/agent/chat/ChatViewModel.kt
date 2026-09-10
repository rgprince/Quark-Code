package com.quark.agent.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quark.agent.backend.MessagePart
import com.quark.agent.backend.OpenCodeService
import com.quark.agent.backend.PermissionResponse
import com.quark.agent.backend.SendMessageBody
import com.quark.agent.backend.ServeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel : ViewModel() {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var service: OpenCodeService? = null
    private var sessionId: String? = null

    fun attach(host: String, username: String, password: String) {
        service = ServeClient.service(host, username, password)
        sessionId = null
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
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = text
        )
        uiState = uiState.copy(
            input = "",
            sending = true,
            messages = uiState.messages + userMessage,
            stats = uiState.stats.copy(used = uiState.stats.used + estimateTokens(text))
        )
        viewModelScope.launch {
            val reply = runCatching {
                withContext(Dispatchers.IO) {
                    val api = service ?: error("Not connected")
                    val id = sessionId ?: api.createSession().also { sessionId = it.id }.id
                    api.sendMessage(id, SendMessageBody(parts = listOf(MessagePart(text = text))))
                }
            }
            reply.onSuccess { result ->
                val texts = result.parts.filter { it.type == "text" }.map { it.text }
                val tools = result.parts.filter { it.type == "tool" }
                val body = texts.joinToString("\n\n").ifEmpty { "(no text reply)" }
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = body,
                    toolRuns = tools.size,
                    filesRead = tools.count { it.tool in FILE_TOOLS },
                    todos = parseTodos(body),
                    toolLog = tools.take(20).map { tool ->
                        "${tool.tool ?: "tool"}: ${(tool.output ?: "").take(400)}"
                    }
                )
                uiState = uiState.copy(
                    sending = false,
                    messages = uiState.messages + assistant,
                    stats = uiState.stats.copy(
                        used = uiState.stats.used + estimateTokens(body),
                        output = uiState.stats.output + estimateTokens(body)
                    )
                )
                refreshCost()
            }.onFailure { err ->
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = "Request failed: ${err.message ?: "unknown error"}"
                )
                uiState = uiState.copy(sending = false, messages = uiState.messages + assistant)
            }
        }
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
