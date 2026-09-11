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
private data object SchedulesRoute

@Composable
fun QuarkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeStore = remember { ThemeStore(context.applicationContext) }
    val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    QuarkTheme(dark = dark) {
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
                agents = listOf(chatVm.uiState.agent),
                selectedAgent = chatVm.uiState.agent,
                projects = listOf(chatVm.uiState.project),
                selectedProject = chatVm.uiState.project,
                recents = chatVm.uiState.recents,
                onNewChat = {
                    closeDrawer()
                    chatVm.newSession()
                    goChat()
                },
                onSelectAgent = { agent ->
                    closeDrawer()
                    chatVm.onAgentChange(agent)
                    goChat()
                },
                onSelectProject = {
                    closeDrawer()
                    goChat()
                },
                onOpenSession = { id ->
                    closeDrawer()
                    chatVm.openSession(id)
                    goChat()
                },
                onDeleteSession = { id ->
                    chatVm.deleteSession(id)
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
                                onProviderChange = settingsVm::onProviderChange,
                                onRetryProviders = settingsVm::loadProviders,
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
                                serverVersion = settingsVm.uiState.serverVersion,
                                diagnosticsText = settingsVm.diagnosticsText(),
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
                        else -> error("Unknown route: $key")
                    }
                }
            )
        }
    }
}
