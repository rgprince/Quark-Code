package com.rg.quarkcode.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rg.quarkcode.backend.EventParser
import com.rg.quarkcode.backend.ModelRef
import com.rg.quarkcode.backend.ModelStore
import com.rg.quarkcode.backend.OpenCodeAgent
import com.rg.quarkcode.backend.OpenCodeCommand
import com.rg.quarkcode.backend.OpenCodeModel
import com.rg.quarkcode.backend.OpenCodeProvider
import com.rg.quarkcode.backend.OpenCodeSkill
import com.rg.quarkcode.backend.ProviderCatalog
import com.rg.quarkcode.backend.ProvidersResponse
import com.rg.quarkcode.backend.ServeApi
import com.rg.quarkcode.backend.ServeEventStream
import com.rg.quarkcode.backend.ServerEvent
import com.rg.quarkcode.backend.ServerTodo
import com.rg.quarkcode.backend.SessionInfo
import com.rg.quarkcode.backend.sessionIdOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private val app = application
    private var api: ServeApi? = null
    private var modelStore: ModelStore? = null
    private var sessionId: String? = null
    private var pollJob: Job? = null
    private var eventJob: Job? = null
    private var streamHost = ""
    private var streamUser = ""
    private var streamPass = ""
    private var selProvider: String? = null
    private var selModel: String? = null
    private var providers: List<OpenCodeProvider> = emptyList()
    private var backendCommands: List<OpenCodeCommand> = emptyList()
    private var backendSkills: List<OpenCodeSkill> = emptyList()
    private var chatPrefs: ChatPrefs? = null
    private var lastSeenIds = mutableSetOf<String>()
    private val streamedParts = mutableMapOf<String, ChatPart>()

    val hasSession: Boolean get() = sessionId != null

    fun attach(host: String, username: String, password: String) {
        api = ServeApi(host, username, password)
        modelStore = ModelStore(app)
        chatPrefs = ChatPrefs(app)
        streamHost = host
        streamUser = username
        streamPass = password
        sessionId = null
        lastSeenIds.clear()
        streamedParts.clear()
        uiState = uiState.copy(
            connected = true,
            messages = emptyList(),
            sessionTodos = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { chatPrefs!!.prefs.first() }
            }.getOrNull()?.let { prefs ->
                uiState = uiState.copy(
                    autoExpandReasoning = prefs.autoExpand,
                    detailedTools = prefs.detailed
                )
            }
        }
        loadCatalog()
        loadRecents()
        startEvents()
    }

    // ---- Catalog + selection (AndCode reconcile) ----

    private fun loadCatalog() {
        uiState = uiState.copy(catalogLoading = true, catalogError = null)
        viewModelScope.launch {
            val client = api ?: return@launch
            val catalog = runCatching { client.get<ProviderCatalog>("provider") }
                .getOrNull()
                ?.takeIf { it.all.isNotEmpty() }
                ?: runCatching { client.get<ProvidersResponse>("config/providers") }
                    .getOrNull()
                    ?.let { fallback ->
                        ProviderCatalog(
                            all = fallback.providers.map { entry ->
                                OpenCodeProvider(
                                    id = entry.id,
                                    name = entry.name,
                                    models = entry.models.associate { model ->
                                        model.id to OpenCodeModel(id = model.id, name = model.name)
                                    }
                                )
                            },
                            default = fallback.default,
                            connected = fallback.providers.map { it.id }
                        )
                    }
                    ?.takeIf { it.all.isNotEmpty() }
                ?: run {
                    uiState = uiState.copy(
                        catalogLoading = false,
                        catalogError = "No providers found. Is the server connected to a provider?"
                    )
                    return@launch
                }
            providers = catalog.all
            val store = modelStore ?: return@launch
            val (providerId, modelId) = store.reconcile(catalog)
            selProvider = providerId
            selModel = modelId
            val snapshot = runCatching {
                withContext(Dispatchers.IO) { store.selection.first() }
            }.getOrNull()
            val limit = catalog.all.firstOrNull { it.id == providerId }
                ?.models?.get(modelId)?.limit?.context?.takeIf { it > 0 }
            uiState = uiState.copy(
                catalogLoading = false,
                catalogError = null,
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
                providers = catalog.all.map { ProviderOption(it.id, it.displayName()) },
                selectedProviderId = providerId,
                selectedModelKey = if (providerId != null && modelId != null) {
                    "$providerId/$modelId"
                } else {
                    "auto"
                },
                model = labelFor(providerId, modelId),
                variants = catalog.all.firstOrNull { it.id == providerId }
                    ?.models?.get(modelId)?.variants?.keys?.toList() ?: emptyList(),
                selectedVariant = null,
                favorites = snapshot?.favorites ?: emptySet(),
                modelRecents = snapshot?.recents ?: emptyList(),
                stats = if (limit != null) uiState.stats.copy(limit = limit) else uiState.stats
            )
            refreshSlashCatalog()
            loadAgents()
        }
    }

    fun retryCatalog() {
        loadCatalog()
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
                withContext(Dispatchers.IO) { modelStore!!.selection.first() }
            }.getOrNull()
            val limit = providers.firstOrNull { it.id == providerId }
                ?.models?.get(modelId)?.limit?.context?.takeIf { it > 0 }
            uiState = uiState.copy(
                model = labelFor(providerId, modelId),
                selectedModelKey = id,
                selectedProviderId = providerId,
                modelSheet = false,
                // A reasoning variant belongs to the old model — clear on switch (AndCode parity).
                selectedVariant = null,
                variants = providers.firstOrNull { it.id == providerId }
                    ?.models?.get(modelId)?.variants?.keys?.toList() ?: emptyList(),
                modelRecents = snapshot?.recents ?: uiState.modelRecents,
                stats = if (limit != null) uiState.stats.copy(limit = limit) else uiState.stats
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

    fun onProviderChange(providerId: String) {
        val first = uiState.catalog.firstOrNull { it.providerId == providerId }?.id ?: return
        onModelChange(first)
    }

    fun onModeChange(mode: String) {
        uiState = uiState.copy(mode = mode.ifBlank { null })
    }

    fun onVariantChange(variant: String?) {
        uiState = uiState.copy(selectedVariant = variant)
    }

    fun setAutoExpand(value: Boolean) {
        uiState = uiState.copy(autoExpandReasoning = value)
        viewModelScope.launch { chatPrefs?.setAutoExpand(value) }
    }

    fun setDetailedTools(value: Boolean) {
        uiState = uiState.copy(detailedTools = value)
        viewModelScope.launch { chatPrefs?.setDetailed(value) }
    }

    // Re-reads ModelStore after Settings changes provider (Settings has its own VM).
    fun refreshSelection() {
        viewModelScope.launch {
            val sel = runCatching {
                withContext(Dispatchers.IO) { modelStore!!.selection.first() }
            }.getOrNull() ?: return@launch
            val providerId = sel.providerId
            val modelId = sel.modelId
            if (providerId.isNullOrBlank() || modelId.isNullOrBlank()) return@launch
            if (providerId == selProvider && modelId == selModel) {
                uiState = uiState.copy(
                    favorites = sel.favorites,
                    modelRecents = sel.recents
                )
                return@launch
            }
            selProvider = providerId
            selModel = modelId
            val limit = providers.firstOrNull { it.id == providerId }
                ?.models?.get(modelId)?.limit?.context?.takeIf { it > 0 }
            uiState = uiState.copy(
                model = labelFor(providerId, modelId),
                selectedModelKey = "$providerId/$modelId",
                selectedProviderId = providerId,
                selectedVariant = null,
                variants = providers.firstOrNull { it.id == providerId }
                    ?.models?.get(modelId)?.variants?.keys?.toList() ?: emptyList(),
                favorites = sel.favorites,
                modelRecents = sel.recents,
                stats = if (limit != null) uiState.stats.copy(limit = limit) else uiState.stats
            )
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            val client = api ?: return@launch
            val agents = runCatching { client.getList<OpenCodeAgent>("agent") }
                .getOrNull()
                ?.map { it.name }
                ?.filter { it.isNotBlank() }
                ?: return@launch
            // Keep the build/plan picker first; fall back to full list (AndCode parity).
            val modes = (agents.filter { it == "build" || it == "plan" }.ifEmpty { agents })
            uiState = uiState.copy(
                modes = modes,
                mode = uiState.mode?.takeIf { modes.contains(it) } ?: modes.firstOrNull()
            )
        }
    }

    private fun refreshSlashCatalog() {
        viewModelScope.launch {
            val client = api ?: return@launch
            val commands = runCatching { client.getList<OpenCodeCommand>("command") }.getOrNull()
            val skills = runCatching { client.getList<OpenCodeSkill>("skill") }.getOrNull()
            commands?.let { backendCommands = it }
            skills?.let { backendSkills = it }
            uiState = uiState.copy(
                slashCommands = SlashCommands.suggestions("", backendCommands, backendSkills)
            )
        }
    }

    fun slashSuggestions(query: String): List<SlashSuggestion> =
        SlashCommands.suggestions(query, backendCommands, backendSkills)

    /** Routes `/…` input: app commands locally, backend commands via POST command. */
    fun handleSlashInput(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return false
        val firstToken = trimmed.substringBefore(" ").substringBefore("\n")
        when (firstToken) {
            "/new" -> { newSession(); return true }
            "/model" -> { uiState = uiState.copy(modelSheet = true); return true }
            "/agent" -> { uiState = uiState.copy(spacesSheet = true); return true }
            "/help" -> {
                uiState = uiState.copy(
                    messages = uiState.messages + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        parts = listOf(
                            ChatPart.Text(UUID.randomUUID().toString(), slashHelpText())
                        )
                    )
                )
                return true
            }
        }
        val match = SlashCommands.matchBackend(trimmed, backendCommands, backendSkills)
        if (match != null) {
            sendSlashCommand(match.first, match.second)
            return true
        }
        return false
    }

    private fun slashHelpText(): String = buildString {
        appendLine("Commands:")
        SlashCommands.app.forEach { appendLine("${it.name} — ${it.description}") }
        backendCommands.sortedBy { it.name }.forEach {
            appendLine("/${it.name}" + (it.description?.let { d -> " — $d" } ?: ""))
        }
        backendSkills.sortedBy { it.name }.forEach {
            appendLine("/${it.name}" + (it.description?.let { d -> " — $d" } ?: " (skill)"))
        }
    }

    private fun sendSlashCommand(command: String, arguments: String) {
        pollJob?.cancel()
        val displayText = "/$command${arguments.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            parts = listOf(ChatPart.Text(UUID.randomUUID().toString(), displayText))
        )
        uiState = uiState.copy(
            input = "",
            sending = true,
            thinking = true,
            messages = uiState.messages + userMessage
        )
        pollJob = viewModelScope.launch {
            val idsBeforeSend = lastSeenIds.toSet()
            val failed = runCatching {
                val client = api ?: error("Not connected")
                val id = sessionId ?: client.createSession(displayText.take(60)).also { sessionId = it }
                val body = buildJsonObject {
                    put("command", command)
                    put("arguments", arguments)
                    uiState.mode?.takeIf { it.isNotBlank() }?.let { put("agent", it) }
                    uiState.selectedVariant?.takeIf { it.isNotBlank() }?.let { put("variant", it) }
                }
                client.postUnit("session/${client.encodePath(id)}/command", body)
                pollUntilDone(client, id, idsBeforeSend)
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(sending = false, thinking = false)
                }
                refreshCost(id)
                refreshTodos()
                loadRecents()
            }
            failed.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) return@launch
                uiState = uiState.copy(
                    sending = false,
                    thinking = false,
                    messages = uiState.messages + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        parts = listOf(ChatPart.Error(UUID.randomUUID().toString(), err.message ?: "unknown error"))
                    )
                )
            }
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
                api?.getList<com.rg.quarkcode.backend.SessionInfo>("session")
            }.getOrNull() ?: return@launch
            sessions.forEach { lastSeenIds.add(it.id) }
            uiState = uiState.copy(
                recents = sessions
                    .filter { it.time.archived == null }
                    .take(20)
                    .map { RecentSession(it.id, it.title.ifEmpty { it.id.take(8) }) }
            )
        }
    }

    fun openSession(id: String) {
        pollJob?.cancel()
        sessionId = id
        streamedParts.clear()
        uiState = uiState.copy(
            sending = true,
            thinking = false,
            messages = emptyList(),
            sessionTodos = emptyList(),
            todosVisible = true,
            spacesSheet = false
        )
        viewModelScope.launch {
            val loaded = runCatching { api?.messages(id) }
            loaded.onSuccess { list ->
                val mapped = (list ?: emptyList()).mapNotNull { it.toUiMessage() }
                mapped.forEach { lastSeenIds.add(it.id) }
                uiState = uiState.copy(sending = false, messages = mapped)
                refreshTodos()
                refreshCost(id)
            }.onFailure { err ->
                uiState = uiState.copy(
                    sending = false,
                    messages = listOf(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            parts = listOf(
                                ChatPart.Error(
                                    id = UUID.randomUUID().toString(),
                                    message = err.message ?: "unknown error"
                                )
                            )
                        )
                    )
                )
            }
        }
    }

    // ---- Send (AndCode flow: async fire, stream merge, transcript completion) ----

    fun send() {
        val text = uiState.input.trim()
        if (text.isEmpty() || uiState.sending) return
        if (handleSlashInput(text)) {
            uiState = uiState.copy(input = "")
            return
        }
        startRun(text)
    }

    fun retry(messageId: String) {
        if (uiState.sending) return
        val failed = uiState.messages.firstOrNull { it.id == messageId } ?: return
        val text = uiState.messages
            .takeWhile { it.id != messageId }
            .lastOrNull { it.isUser }
            ?.text ?: failed.text
        if (text.isBlank()) return
        startRun(text)
    }

    private fun startRun(text: String) {
        pollJob?.cancel()
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            parts = listOf(ChatPart.Text(UUID.randomUUID().toString(), text))
        )
        uiState = uiState.copy(
            input = "",
            sending = true,
            thinking = true,
            messages = uiState.messages.filterNot { message ->
                message.parts.filterIsInstance<ChatPart.Error>().isNotEmpty() && !message.isUser
            } + userMessage,
            stats = uiState.stats.copy(used = uiState.stats.used + estimateTokens(text))
        )
        pollJob = viewModelScope.launch {
            val idsBeforeSend = lastSeenIds.toSet()
            val failed = runCatching {
                val client = api ?: error("Not connected")
                val id = sessionId ?: client.createSession(text.take(60)).also { sessionId = it }
                // NOTE: uiState.agent ("Native opencode") is a local runtime label —
                // never sent. uiState.mode (build/plan from GET agent) IS the server agent.
                val ref = selectedRef()
                val body = buildJsonObject {
                    uiState.mode?.takeIf { it.isNotBlank() }?.let { put("agent", it) }
                    uiState.selectedVariant?.takeIf { it.isNotBlank() }?.let { put("variant", it) }
                    if (ref != null) {
                        put(
                            "model",
                            buildJsonObject {
                                put("providerID", ref.providerId)
                                put("modelID", ref.modelId)
                            }
                        )
                    }
                    put(
                        "parts",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", text)
                                }
                            )
                        }
                    )
                }
                client.postUnit("session/${client.encodePath(id)}/prompt_async", body)
                pollUntilDone(client, id, idsBeforeSend)
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(sending = false, thinking = false)
                }
                refreshCost(id)
                refreshTodos()
                loadRecents()
            }
            failed.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) return@launch
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    parts = listOf(
                        ChatPart.Error(UUID.randomUUID().toString(), err.message ?: "unknown error")
                    )
                )
                uiState = uiState.copy(
                    sending = false,
                    thinking = false,
                    messages = uiState.messages + assistant
                )
            }
        }
    }

    private suspend fun ServeApi.createSession(title: String): String {
        val body = buildJsonObject {
            title.takeIf { it.isNotBlank() }?.let { put("title", it) }
        }
        return post<com.rg.quarkcode.backend.SessionInfo>("session", body).id
    }

    private suspend fun ServeApi.messages(id: String): List<com.rg.quarkcode.backend.MessageWithParts> =
        getList("session/${encodePath(id)}/message")

    // Completion is read off the transcript: a fresh assistant message ends it.
    private suspend fun pollUntilDone(
        client: ServeApi,
        id: String,
        idsBeforeSend: Set<String>
    ) {
        val stillActive = { sessionId == id }
        val finished = withTimeoutOrNull(120_000L) {
            while (stillActive() && uiState.sending) {
                delay(3000L)
                if (!stillActive() || !uiState.sending) return@withTimeoutOrNull
                val retain = streamedParts.keys.toSet()
                runCatching { client.messages(id) }.onSuccess { serverMessages ->
                    if (!stillActive()) return@onSuccess
                    val mapped = serverMessages.mapNotNull { it.toUiMessage() }
                    val merged = mergeReloadedMessages(mapped, uiState.messages, retain)
                    if (merged.isNotEmpty() && merged != uiState.messages) {
                        uiState = uiState.copy(
                            messages = merged,
                            thinking = merged.none {
                                !it.isUser && it.parts.any { part ->
                                    part is ChatPart.Text && part.text.isNotBlank()
                                }
                            } && uiState.sending
                        )
                    }
                    mapped.forEach { lastSeenIds.add(it.id) }
                    if (turnFinished(serverMessages, idsBeforeSend)) {
                        uiState = uiState.copy(sending = false, thinking = false)
                    }
                }
            }
        }
        if (stillActive() && (uiState.sending || finished == null)) {
            runCatching { client.messages(id) }.onSuccess { serverMessages ->
                if (!stillActive()) return@onSuccess
                val retained = streamedParts.keys.toSet()
                streamedParts.clear()
                val mapped = serverMessages.mapNotNull { it.toUiMessage() }
                val hasResponse = serverMessages.any {
                    it.info.role == "assistant" && it.info.id !in idsBeforeSend
                }
                if (hasResponse || finished == null) {
                    uiState = uiState.copy(
                        messages = mergeReloadedMessages(mapped, uiState.messages, retained),
                        sending = false,
                        thinking = false
                    )
                } else {
                    uiState = uiState.copy(sending = false, thinking = false)
                }
                mapped.forEach { lastSeenIds.add(it.id) }
            }
        }
    }

    fun abort() {
        val id = sessionId ?: return
        pollJob?.cancel()
        viewModelScope.launch {
            runCatching {
                api?.postUnit(
                    "session/${api!!.encodePath(id)}/abort",
                    JsonObject(emptyMap())
                )
            }
            uiState = uiState.copy(sending = false, thinking = false)
            refreshTodos()
        }
    }

    fun newSession() {
        pollJob?.cancel()
        sessionId = null
        streamedParts.clear()
        uiState = uiState.copy(
            sending = false,
            thinking = false,
            messages = emptyList(),
            sessionTodos = emptyList(),
            todosVisible = true,
            spacesSheet = false
        )
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            runCatching {
                api?.delete<Boolean>("session/${api!!.encodePath(id)}")
            }
            if (sessionId == id) {
                newSession()
            }
            loadRecents()
        }
    }

    // ---- Review (AndCode IA: session diff + rename + summarize) ----

    var reviewState by mutableStateOf(ReviewState())
        private set

    fun openReview() {
        val id = sessionId
        reviewState = reviewState.copy(
            title = uiState.recents.firstOrNull { it.id == id }?.title.orEmpty()
        )
        loadDiff()
    }

    fun loadDiff() {
        val id = sessionId ?: run {
            reviewState = reviewState.copy(error = "No open session.")
            return
        }
        reviewState = reviewState.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                api?.getList<com.rg.quarkcode.backend.OpenCodeFileChange>(
                    "session/${api!!.encodePath(id)}/diff"
                )
            }.onSuccess { files ->
                reviewState = reviewState.copy(loading = false, files = files ?: emptyList())
            }.onFailure { err ->
                reviewState = reviewState.copy(
                    loading = false,
                    error = err.message ?: "Could not load diff"
                )
            }
        }
    }

    fun onReviewTitleChange(value: String) {
        reviewState = reviewState.copy(title = value)
    }

    fun saveReviewTitle() {
        val id = sessionId ?: return
        val title = reviewState.title.trim()
        if (title.isEmpty()) return
        reviewState = reviewState.copy(savingTitle = true)
        viewModelScope.launch {
            runCatching {
                api?.patch<JsonObject>(
                    "session/${api!!.encodePath(id)}",
                    buildJsonObject { put("title", title) }
                )
            }.onSuccess {
                reviewState = reviewState.copy(savingTitle = false)
                loadRecents()
            }.onFailure { err ->
                reviewState = reviewState.copy(
                    savingTitle = false,
                    error = err.message ?: "Could not rename"
                )
            }
        }
    }

    fun summarizeSession() {
        val id = sessionId ?: return
        val ref = selectedRef()
        reviewState = reviewState.copy(summarizing = true, error = null)
        viewModelScope.launch {
            runCatching {
                api?.post<Boolean>(
                    "session/${api!!.encodePath(id)}/summarize",
                    buildJsonObject {
                        put("providerID", ref?.providerId ?: "")
                        put("modelID", ref?.modelId ?: "")
                    }
                )
            }.onSuccess {
                reviewState = reviewState.copy(summarizing = false)
                refreshMessages()
            }.onFailure { err ->
                reviewState = reviewState.copy(
                    summarizing = false,
                    error = err.message ?: "Could not summarize"
                )
            }
        }
    }

    // ---- Live events ----

    private fun startEvents() {
        eventJob?.cancel()
        val client = api ?: return
        val stream = ServeEventStream(streamHost, streamUser, streamPass, client)
        eventJob = viewModelScope.launch {
            stream.events().collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: ServerEvent) {
        val id = sessionId
        when (event) {
            is ServerEvent.PartDelta -> {
                if (event.sessionId != id) return
                if (event.field != "text" && event.field != "reasoning") return
                val existing = streamedParts[event.partId]
                streamedParts[event.partId] = when {
                    existing is ChatPart.Text -> existing.copy(text = existing.text + event.delta)
                    existing is ChatPart.Reasoning -> existing.copy(text = existing.text + event.delta)
                    event.field == "reasoning" -> ChatPart.Reasoning(event.partId, event.delta)
                    else -> ChatPart.Text(event.partId, event.delta)
                }
                upsertStreamedPart(event.messageId, streamedParts[event.partId]!!)
                if (event.field == "text") {
                    uiState = uiState.copy(thinking = false)
                }
            }
            is ServerEvent.PartUpdated -> {
                val part = event.part
                if (part.sessionId != null && part.sessionId != id) return
                val chatPart = part.toChatPart() ?: return
                streamedParts[chatPart.id] = chatPart
                upsertStreamedPart(part.messageId ?: return, chatPart)
            }
            is ServerEvent.MessageUpdated -> {
                if (event.info.sessionId != id && event.info.sessionId.isNotEmpty()) return
                refreshMessages()
            }
            is ServerEvent.PermissionAsked -> {
                if (event.ask.sessionId != id) return
                val ask = event.ask
                val summary = (ask.patterns + ask.metadata.entries.map { (k, v) ->
                    "$k: ${v.toString().take(200)}"
                }).filter { it.isNotBlank() }.joinToString("\n").ifEmpty {
                    "The agent wants to run this action."
                }
                uiState = uiState.copy(
                    sending = false,
                    thinking = false,
                    messages = uiState.messages + ChatMessage(
                        id = "perm-${ask.id}",
                        isUser = false,
                        permission = PermissionRequest(
                            id = ask.id,
                            tool = ask.permission,
                            summary = summary
                        )
                    )
                )
            }
            is ServerEvent.PermissionReplied -> {
                if (event.sessionId != id) return
                uiState = uiState.copy(
                    messages = uiState.messages.map { message ->
                        if (message.permission?.id == event.requestId) {
                            message.copy(permission = null)
                        } else {
                            message
                        }
                    }
                )
            }
            is ServerEvent.QuestionAsked -> {
                if (event.ask.sessionId != id) return
                // Displayed with answer buttons (QuestionCard); replies go to
                // POST question/{id}/reply.
                uiState = uiState.copy(
                    messages = uiState.messages + ChatMessage(
                        id = "q-${event.ask.id}",
                        isUser = false,
                        parts = event.ask.questions.flatMap { question ->
                            listOf(ChatPart.Text(UUID.randomUUID().toString(), question.question)) +
                                question.options.map { option ->
                                    ChatPart.QuestionOption(
                                        id = UUID.randomUUID().toString(),
                                        requestId = event.ask.id,
                                        label = option.label,
                                        description = option.description
                                    )
                                }
                        }
                    )
                )
            }
            is ServerEvent.SessionIdle, is ServerEvent.SessionError -> {
                val error = (event as? ServerEvent.SessionError)
                val errorSession = when (event) {
                    is ServerEvent.SessionIdle -> event.sessionId
                    is ServerEvent.SessionError -> event.sessionId
                    else -> null
                }
                if (errorSession != id) return
                if (error != null && !error.isAbort && error.message != null) {
                    uiState = uiState.copy(
                        messages = uiState.messages + ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            parts = listOf(
                                ChatPart.Error(UUID.randomUUID().toString(), error.message)
                            )
                        )
                    )
                }
                uiState = uiState.copy(sending = false, thinking = false)
                streamedParts.clear()
                refreshMessages()
                refreshTodos()
            }
            is ServerEvent.StatusChanged -> {
                if (event.sessionId != id) return
                if (event.status == "idle") {
                    uiState = uiState.copy(sending = false, thinking = false)
                    refreshMessages()
                    refreshTodos()
                }
            }
            is ServerEvent.SessionCreated -> loadRecents()
            else -> Unit
        }
    }

    private fun upsertStreamedPart(messageId: String, part: ChatPart) {
        // Kill the tail "Thinking…" the moment real content streams in —
        // waiting for poll/idle caused the visible lag after answers arrived.
        val hasContent = when (part) {
            is ChatPart.Text -> part.text.isNotBlank()
            is ChatPart.Reasoning -> part.text.isNotBlank()
            is ChatPart.Tool, is ChatPart.Patch, is ChatPart.Image -> true
            else -> false
        }
        val current = uiState.messages
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) {
            uiState = uiState.copy(
                messages = current + ChatMessage(
                    id = messageId,
                    isUser = false,
                    parts = listOf(part),
                    isStreaming = true
                ),
                thinking = if (hasContent) false else uiState.thinking
            )
        } else {
            val message = current[index]
            val partIndex = message.parts.indexOfFirst { it.id == part.id }
            val updated = if (partIndex < 0) {
                message.copy(parts = message.parts + part, isStreaming = true)
            } else {
                message.copy(
                    parts = message.parts.toMutableList().also { it[partIndex] = part },
                    isStreaming = true
                )
            }
            uiState = uiState.copy(
                messages = current.toMutableList().also { it[index] = updated },
                thinking = if (hasContent) false else uiState.thinking
            )
        }
    }

    private fun refreshMessages() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val list = runCatching { api?.messages(id) }.getOrNull() ?: return@launch
            val mapped = list.mapNotNull { it.toUiMessage() }
            val fresh = mapped.filter { lastSeenIds.add(it.id) }
            val added = fresh.sumOf { estimateTokens(it.text) }
            val answered = mapped.any { message ->
                !message.isUser && message.parts.any { part ->
                    (part is ChatPart.Text && part.text.isNotBlank()) || part is ChatPart.Tool
                }
            }
            uiState = uiState.copy(
                messages = mergeReloadedMessages(mapped, uiState.messages, streamedParts.keys.toSet()),
                thinking = if (answered) false else uiState.thinking,
                stats = uiState.stats.copy(
                    used = uiState.stats.used + added,
                    output = uiState.stats.output + added
                )
            )
        }
    }

    // ---- Permissions / questions / misc actions ----

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

    fun onAgentChange(agent: String) {
        uiState = uiState.copy(agent = agent, spacesSheet = false)
    }

    fun togglePart(partId: String) {
        val expanded = uiState.expandedParts
        uiState = uiState.copy(
            expandedParts = if (expanded.contains(partId)) expanded - partId else expanded + partId
        )
    }

    fun toggleTodo(messageId: String, todoId: String) {
        uiState = uiState.copy(
            messages = uiState.messages.map { message ->
                if (message.id != messageId) {
                    message
                } else {
                    message.copy(
                        parts = message.parts.map { part ->
                            if (part is ChatPart.Tool) {
                                part.copy(
                                    todos = part.todos.map { todo ->
                                        if (todo.id != todoId) todo else todo.copy(done = !todo.done)
                                    }
                                )
                            } else {
                                part
                            }
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
        respondPermission(permissionId, "once")
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
        val apiResponse = if (remember && response == "once") "always" else response
        viewModelScope.launch {
            runCatching {
                api?.postUnit(
                    "session/${api!!.encodePath(permissionId)}/permissions/$permissionId",
                    buildJsonObject { put("response", apiResponse) }
                )
            }
            uiState = uiState.copy(
                messages = uiState.messages.map { message ->
                    if (message.permission?.id == permissionId) {
                        message.copy(permission = null)
                    } else {
                        message
                    }
                }
            )
        }
    }

    fun answerQuestion(requestId: String, label: String) {
        viewModelScope.launch {
            runCatching {
                api?.postUnit(
                    "question/${api!!.encodePath(requestId)}/reply",
                    buildJsonObject {
                        put(
                            "answers",
                            buildJsonArray {
                                add(
                                    buildJsonArray {
                                        add(JsonPrimitive(label))
                                    }
                                )
                            }
                        )
                    }
                )
            }
            uiState = uiState.copy(
                messages = uiState.messages.map { message ->
                    message.copy(
                        parts = message.parts.filterNot { part ->
                            part is ChatPart.QuestionOption && part.requestId == requestId
                        }
                    )
                }
            )
        }
    }

    private fun refreshCost(id: String) {
        viewModelScope.launch {
            val info = runCatching { api?.get<SessionInfo>("session/${api!!.encodePath(id)}") }
                .getOrNull() ?: return@launch
            val tokens = info.tokens
            uiState = uiState.copy(
                stats = uiState.stats.copy(
                    cost = info.cost,
                    used = tokens?.contextUsed ?: uiState.stats.used,
                    input = tokens?.input ?: uiState.stats.input,
                    output = tokens?.output ?: uiState.stats.output
                )
            )
        }
    }

    private fun refreshTodos() {
        val id = sessionId ?: return
        viewModelScope.launch {
            val todos = runCatching {
                api?.getList<ServerTodo>("session/${api!!.encodePath(id)}/todo")
            }.getOrNull() ?: return@launch
            val mapped = todos.mapIndexed { index, todo ->
                TodoItem(
                    id = "srv-$index",
                    text = todo.content,
                    done = todo.status.equals("completed", ignoreCase = true) ||
                        todo.status.equals("done", ignoreCase = true)
                )
            }
            uiState = uiState.copy(
                sessionTodos = mapped,
                // Re-show the strip when the list actually changes after a dismiss.
                todosVisible = if (mapped != uiState.sessionTodos) true else uiState.todosVisible
            )
        }
    }

    fun dismissTodos() {
        uiState = uiState.copy(todosVisible = false)
    }

    private fun estimateTokens(text: String): Long = (text.length / 4).toLong()

    override fun onCleared() {
        pollJob?.cancel()
        eventJob?.cancel()
        super.onCleared()
    }
}
