package com.rg.quarkcode.chat

data class ChatRoute(val id: String)

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

data class CatalogModel(
    val id: String,
    val label: String,
    val providerId: String = "",
    val modelId: String = ""
)

data class ProviderOption(
    val id: String,
    val name: String
)

data class RecentSession(
    val id: String,
    val title: String
)

data class ChatUiState(
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val model: String = "Auto (server default)",
    val selectedModelKey: String = "auto",
    val selectedProviderId: String? = null,
    val providers: List<ProviderOption> = emptyList(),
    val agent: String = "Native opencode",
    val catalog: List<CatalogModel> = listOf(CatalogModel("auto", "Auto (server default)")),
    val modelRecents: List<String> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val recents: List<RecentSession> = emptyList(),
    val sessionTodos: List<TodoItem> = emptyList(),
    val connected: Boolean = false,
    val runtime: Runtime = Runtime.NATIVE,
    val favorites: Set<String> = emptySet(),
    val stats: ContextStats = ContextStats(),
    val sending: Boolean = false,
    val thinking: Boolean = false,
    val expandedParts: Set<String> = emptySet(),
    val modelSheet: Boolean = false,
    val spacesSheet: Boolean = false,
    val contextSheet: Boolean = false,
    val project: String = "local"
)
