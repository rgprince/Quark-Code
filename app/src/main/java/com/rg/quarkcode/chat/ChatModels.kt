package com.rg.quarkcode.chat

data class ChatRoute(val id: String)

data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean
)

data class PermissionRequest(
    val id: String,
    val tool: String,
    val summary: String,
    val remember: Boolean
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val toolRuns: Int = 0,
    val filesRead: Int = 0,
    val todos: List<TodoItem> = emptyList(),
    val permission: PermissionRequest? = null,
    val imageUrl: String? = null,
    val toolLog: List<String> = emptyList(),
    val isError: Boolean = false
)

data class CatalogModel(
    val id: String,
    val label: String,
    val providerId: String = "",
    val modelId: String = ""
)

data class RecentSession(
    val id: String,
    val title: String
)

enum class Runtime {
    NATIVE,
    SERVER
}

data class ContextStats(
    val limit: Long = 200_000L,
    val used: Long = 0L,
    val input: Long = 0L,
    val output: Long = 0L,
    val cost: Double = 0.0
)

data class ChatUiState(
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val model: String = "Auto (server default)",
    val selectedModelKey: String = "auto",
    val agent: String = "Native opencode",
    val catalog: List<CatalogModel> = listOf(CatalogModel("auto", "Auto (server default)")),
    val modelRecents: List<String> = emptyList(),
    val recents: List<RecentSession> = emptyList(),
    val sessionTodos: List<TodoItem> = emptyList(),
    val connected: Boolean = false,
    val runtime: Runtime = Runtime.NATIVE,
    val favorites: Set<String> = emptySet(),
    val stats: ContextStats = ContextStats(),
    val sending: Boolean = false,
    val toolsExpanded: Map<String, Boolean> = emptyMap(),
    val modelSheet: Boolean = false,
    val spacesSheet: Boolean = false,
    val contextSheet: Boolean = false,
    val project: String = "local"
)
