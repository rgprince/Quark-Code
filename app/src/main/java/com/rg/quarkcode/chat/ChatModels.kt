package com.rg.quarkcode.chat

import kotlinx.serialization.Serializable

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

@Serializable
data class CatalogModel(
    val id: String,
    val label: String,
    val providerId: String = "",
    val modelId: String = ""
)

@Serializable
data class ProviderOption(
    val id: String,
    val name: String
)

data class SlashSuggestion(
    val name: String,
    val description: String,
    val isSkill: Boolean = false,
    val isApp: Boolean = false
)

data class AtFile(
    val path: String,
    val name: String
)

@Serializable
data class RecentSession(
    val id: String,
    val title: String
)

enum class UsagePeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    ALL("All time")
}

data class ModelUsage(
    val label: String,
    val provider: String,
    val tokens: Long = 0L,
    val cache: Long = 0L,
    val cost: Double = 0.0,
    val messages: Int = 0
) {
    // Same accounting as the chat ring: context = input + cache reads.
    val context: Long get() = tokens + cache
}

data class UsageTotals(
    val input: Long = 0L,
    val output: Long = 0L,
    val cache: Long = 0L,
    val cost: Double = 0.0,
    val sessions: Int = 0,
    val byModel: List<ModelUsage> = emptyList()
) {
    val total: Long get() = input + output + cache
}

data class UsageState(
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val confirmScan: Boolean = false,
    val error: String? = null,
    val scannedAt: Long = 0L,
    val period: UsagePeriod = UsagePeriod.ALL,
    val all: UsageTotals = UsageTotals(),
    val week: UsageTotals = UsageTotals(),
    val month: UsageTotals = UsageTotals()
)

data class ReviewState(
    val files: List<com.rg.quarkcode.backend.OpenCodeFileChange> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val savingTitle: Boolean = false,
    val summarizing: Boolean = false
)

data class ChatUiState(
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val model: String = "Auto (server default)",
    val selectedModelKey: String = "auto",
    val selectedProviderId: String? = null,
    val providers: List<ProviderOption> = emptyList(),
    val agent: String = "Native opencode",
    val modes: List<String> = listOf("build", "plan"),
    val mode: String? = null,
    val variants: List<String> = emptyList(),
    val selectedVariant: String? = null,
    val slashCommands: List<SlashSuggestion> = emptyList(),
    val detailedTools: Boolean = false,
    val textScale: Float = 1f,
    val autoScroll: Boolean = true,
    val playfulStatus: Boolean = true,
    val showThoughts: Boolean = true,
    val comfortable: Boolean = false,
    val showTimestamps: Boolean = true,
    val sendBehavior: String = "interrupt",
    val queuedCount: Int = 0,
    val hiddenModels: List<CatalogModel> = emptyList(),
    val atSuggestions: List<AtFile> = emptyList(),
    val autoSpeak: Boolean = false,
    val speakingId: String? = null,
    val catalog: List<CatalogModel> = listOf(CatalogModel("auto", "Auto (server default)")),
    val modelRecents: List<String> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val recents: List<RecentSession> = emptyList(),
    val recentsError: String? = null,
    val sessionTodos: List<TodoItem> = emptyList(),
    val todosVisible: Boolean = true,
    val connected: Boolean = false,
    val runtime: Runtime = Runtime.NATIVE,
    val favorites: Set<String> = emptySet(),
    val stats: ContextStats = ContextStats(),
    val sending: Boolean = false,
    val thinking: Boolean = false,
    // True from send until the first assistant content lands (or the turn
    // ends). Survives early idle/thinking clears so the glow, stop button
    // and status never pretend the model stopped mid-turn.
    val awaitingReply: Boolean = false,
    val expandedParts: Set<String> = emptySet(),
    val modelSheet: Boolean = false,
    val spacesSheet: Boolean = false,
    val contextSheet: Boolean = false,
    val project: String = "New chat"
)
