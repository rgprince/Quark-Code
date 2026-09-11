package com.rg.quarkcode

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.rg.quarkcode.backend.ThemeMode
import com.rg.quarkcode.backend.ThemeStore
import com.rg.quarkcode.chat.ChatRoute
import com.rg.quarkcode.chat.ChatScreen
import com.rg.quarkcode.chat.ChatViewModel
import com.rg.quarkcode.chat.ReviewScreen
import com.rg.quarkcode.settings.ProvidersScreen
import com.rg.quarkcode.settings.ServerInfoScreen
import com.rg.quarkcode.connect.ConnectScreen
import com.rg.quarkcode.connect.ConnectViewModel
import com.rg.quarkcode.drawer.QuarkDrawer
import com.rg.quarkcode.schedules.SchedulesScreen
import com.rg.quarkcode.schedules.SchedulesViewModel
import com.rg.quarkcode.settings.SettingsScreen
import com.rg.quarkcode.settings.SettingsViewModel
import com.rg.quarkcode.theme.QuarkTheme
import kotlinx.coroutines.launch

private data object ConnectRoute
private data object SettingsRoute
private data object ProvidersRoute
private data object ServerInfoRoute
private data object SchedulesRoute
private data object DiffRoute

@Composable
fun QuarkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeStore = remember { ThemeStore(context.applicationContext) }
    val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> systemDark
    }

    QuarkTheme(dark = dark, mode = themeMode) {
        QuarkNavHost(modifier = modifier)
    }
}

@Composable
private fun QuarkNavHost(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Any>(ConnectRoute) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val connectVm: ConnectViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val schedulesVm: SchedulesViewModel = viewModel()

    fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun goChat() {
        backStack.removeAll { it is ChatRoute }
        if (backStack.none { it is ChatRoute }) {
            backStack.add(ChatRoute("local"))
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            QuarkDrawer(
                recents = chatVm.uiState.recents,
                hasSession = chatVm.hasSession,
                onNewChat = {
                    closeDrawer()
                    chatVm.newSession()
                    goChat()
                },
                onOpenSession = { id ->
                    closeDrawer()
                    chatVm.openSession(id)
                    goChat()
                },
                onOpenReview = {
                    closeDrawer()
                    backStack.add(DiffRoute)
                },
                onOpenSchedules = {
                    closeDrawer()
                    backStack.add(SchedulesRoute)
                },
                onOpenSettings = {
                    closeDrawer()
                    backStack.add(SettingsRoute)
                }
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key ->
                    when (key) {
                        is ConnectRoute -> NavEntry(key) {
                            ConnectScreen(
                                state = connectVm.uiState,
                                onHostChange = connectVm::onHostChange,
                                onUsernameChange = connectVm::onUsernameChange,
                                onPasswordChange = connectVm::onPasswordChange,
                                onConnect = dropUnlessResumed {
                                    connectVm.testConnection {
                                        val state = connectVm.uiState
                                        chatVm.attach(state.host, state.username, state.password)
                                        goChat()
                                    }
                                }
                            )
                        }
                        is ChatRoute -> NavEntry(key) {
                            ChatScreen(
                                state = chatVm.uiState,
                                onInputChange = chatVm::onInputChange,
                                onSend = dropUnlessResumed { chatVm.send() },
                                onAllow = chatVm::allow,
                                onDeny = chatVm::deny,
                                onRememberChange = chatVm::onRememberChange,
                                onToggleTodo = chatVm::toggleTodo,
                                onTogglePart = chatVm::togglePart,
                                onModelSheet = chatVm::setModelSheet,
                                onSpacesSheet = chatVm::setSpacesSheet,
                                onContextSheet = chatVm::setContextSheet,
                                onRuntimeChange = chatVm::onRuntimeChange,
                                onModelChange = chatVm::onModelChange,
                                onFavoriteToggle = chatVm::toggleFavorite,
                                onHiddenToggle = chatVm::toggleHidden,
                                onAgentSelect = chatVm::onAgentChange,
                                onRetry = chatVm::retry,
                                onAbort = chatVm::abort,
                                onOpenSession = chatVm::openSession,
                                onRetryCatalog = chatVm::retryCatalog,
                                onAnswer = chatVm::answerQuestion,
                                onModeChange = chatVm::onModeChange,
                                onVariantChange = chatVm::onVariantChange,
                                onSlashSelect = { suggestion ->
                                    chatVm.onInputChange(suggestion.name + " ")
                                },
                                onAtSelect = { file ->
                                    chatVm.insertAtFile(file.path)
                                },
                                onVoiceResult = chatVm::appendVoiceResult,
                                onSpeak = chatVm::toggleSpeak,
                                onDismissTodos = chatVm::dismissTodos,
                                onOpenSettings = { backStack.add(SettingsRoute) },
                                onMenu = { openDrawer() }
                            )
                        }
                        is SettingsRoute -> NavEntry(key) {
                            SettingsScreen(
                                state = settingsVm.uiState,
                                onBack = {
                                    backStack.removeLastOrNull()
                                    chatVm.refreshSelection()
                                },
                                onHostChange = settingsVm::onHostChange,
                                onUsernameChange = settingsVm::onUsernameChange,
                                onPasswordChange = settingsVm::onPasswordChange,
                                onTestAndSave = {
                                    settingsVm.testAndSave { connection ->
                                        chatVm.attach(
                                            connection.host,
                                            connection.username,
                                            connection.password
                                        )
                                    }
                                },
                                onThemeChange = settingsVm::setTheme,
                                providerSummary = settingsVm.uiState.providers
                                    .firstOrNull { it.id == settingsVm.uiState.selectedProviderId }
                                    ?.name?.let { "$it · ${settingsVm.uiState.providers.size} providers" }
                                    ?: "Choose default & manage API keys",
                                onOpenProviders = { backStack.add(ProvidersRoute) },
                                autoExpandReasoning = settingsVm.uiState.autoExpandReasoning,
                                onAutoExpandChange = { value ->
                                    settingsVm.setAutoExpand(value)
                                    chatVm.setAutoExpand(value)
                                },
                                detailedTools = settingsVm.uiState.detailedTools,
                                onDetailedChange = { value ->
                                    settingsVm.setDetailedTools(value)
                                    chatVm.setDetailedTools(value)
                                },
                                textScale = settingsVm.uiState.textScale,
                                onTextScaleChange = { value ->
                                    settingsVm.setTextScale(value)
                                    chatVm.setTextScale(value)
                                },
                                sendBehavior = chatVm.uiState.sendBehavior,
                                onSendBehaviorChange = chatVm::setSendBehavior,
                                autoSpeak = chatVm.uiState.autoSpeak,
                                onAutoSpeakChange = chatVm::setAutoSpeak,
                                serverVersion = settingsVm.uiState.serverVersion,
                                diagnosticsText = settingsVm.diagnosticsText(),
                                onOpenServerInfo = { backStack.add(ServerInfoRoute) },
                                onNewMcpNameChange = settingsVm::onNewMcpNameChange,
                                onNewMcpUrlChange = settingsVm::onNewMcpUrlChange,
                                onAddMcp = settingsVm::addMcp,
                                onToggleMcp = settingsVm::toggleMcp,
                                onOpen = { settingsVm.loadAll() }
                            )
                        }
                        is SchedulesRoute -> NavEntry(key) {
                            SchedulesScreen(
                                state = schedulesVm.uiState,
                                onBack = { backStack.removeLastOrNull() },
                                onAdd = { schedulesVm.startEditing() },
                                onEdit = { schedulesVm.startEditing(it) },
                                onEditingChange = schedulesVm::onEditChange,
                                onCancelEditing = schedulesVm::cancelEditing,
                                onSave = schedulesVm::saveEditing,
                                onDelete = schedulesVm::delete,
                                onToggle = schedulesVm::toggle
                            )
                        }
                        is DiffRoute -> NavEntry(key) {
                            ReviewScreen(
                                state = chatVm.reviewState,
                                onBack = { backStack.removeLastOrNull() },
                                onOpen = chatVm::openReview,
                                onRetry = chatVm::loadDiff,
                                onTitleChange = chatVm::onReviewTitleChange,
                                onSaveTitle = chatVm::saveReviewTitle,
                                onSummarize = chatVm::summarizeSession
                            )
                        }
                        is ProvidersRoute -> NavEntry(key) {
                            ProvidersScreen(
                                state = settingsVm.uiState,
                                onBack = {
                                    backStack.removeLastOrNull()
                                    chatVm.refreshSelection()
                                },
                                onOpen = settingsVm::loadProviders,
                                onProviderChange = settingsVm::onProviderChange,
                                onRetry = settingsVm::loadProviders,
                                onOpenDialog = settingsVm::openProviderDialog,
                                onAuthKeyChange = settingsVm::onAuthKeyChange,
                                onSaveKey = settingsVm::saveProviderKey,
                                onDisconnect = settingsVm::disconnectProvider,
                                onCloseDialog = settingsVm::closeProviderDialog
                            )
                        }
                        is ServerInfoRoute -> NavEntry(key) {
                            ServerInfoScreen(
                                state = settingsVm.uiState,
                                onBack = { backStack.removeLastOrNull() },
                                onOpen = settingsVm::loadServerInfoFull,
                                onStartEdit = settingsVm::startEditConfig,
                                onCancelEdit = settingsVm::cancelEditConfig,
                                onDraftChange = settingsVm::onConfigDraftChange,
                                onSave = settingsVm::saveConfig
                            )
                        }
                        else -> error("Unknown route: $key")
                    }
                }
            )
        }
    }
}
