package com.rg.quarkcode

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rg.quarkcode.backend.ConnectionStore
import com.rg.quarkcode.backend.LocalBackend
import com.rg.quarkcode.backend.QuarkBackendService
import com.rg.quarkcode.backend.RuntimeFiles
import com.rg.quarkcode.backend.RuntimeStore
import com.rg.quarkcode.backend.ThemeMode
import com.rg.quarkcode.backend.ThemeStore
import com.rg.quarkcode.chat.ChatRoute
import com.rg.quarkcode.chat.ChatScreen
import com.rg.quarkcode.chat.UsageScreen
import com.rg.quarkcode.chat.ChatViewModel
import com.rg.quarkcode.chat.ReviewScreen
import com.rg.quarkcode.settings.ProvidersScreen
import com.rg.quarkcode.connect.ConnectScreen
import com.rg.quarkcode.connect.ConnectViewModel
import com.rg.quarkcode.drawer.QuarkDrawer
import com.rg.quarkcode.settings.SettingsScreen
import com.rg.quarkcode.settings.SettingsViewModel
import com.rg.quarkcode.theme.QuarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data object ConnectRoute
private data object SettingsRoute
private data object ProvidersRoute
private data object UsageRoute
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
    // Chat first, always: the saved backend auto-attaches below, and the
    // backend URL stays editable anytime in Settings → Connection.
    val backStack = remember { mutableStateListOf<Any>(ChatRoute("local")) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectVm: ConnectViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        // On-device backend wins when enabled and downloaded: start the
        // keeper, wait for health, attach loopback. Falls through to the
        // saved remote backend when local isn't available.
        val runtime = runCatching {
            withContext(Dispatchers.IO) { RuntimeStore(appCtx).prefs.first() }
        }.getOrNull()
        if (runtime?.backendEnabled == true && RuntimeFiles.isPresent(appCtx)) {
            QuarkBackendService.start(appCtx)
            val ok = withContext(Dispatchers.IO) {
                repeat(50) {
                    if (LocalBackend.state.value is LocalBackend.State.Running) return@withContext true
                    delay(1000L)
                }
                LocalBackend.state.value is LocalBackend.State.Running
            }
            if (ok) {
                val pw = runCatching {
                    withContext(Dispatchers.IO) { RuntimeStore(appCtx).password() }
                }.getOrNull().orEmpty()
                if (!chatVm.uiState.connected) {
                    chatVm.attach("http://127.0.0.1:4096", LocalBackend.USERNAME, pw)
                }
                return@LaunchedEffect
            }
        }
        val saved = runCatching {
            withContext(Dispatchers.IO) { ConnectionStore(appCtx).connection.first() }
        }.getOrNull()
        if (saved != null && !chatVm.uiState.connected) {
            chatVm.attach(saved.host, saved.username, saved.password)
        }
    }

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
                recentsError = chatVm.uiState.recentsError,
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
                onOpenSettings = {
                    closeDrawer()
                    backStack.add(SettingsRoute)
                },
                onRefreshRecents = chatVm::refreshRecents
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
                                onOpenUsage = { backStack.add(UsageRoute) },
                                onNewMcpNameChange = settingsVm::onNewMcpNameChange,
                                onNewMcpUrlChange = settingsVm::onNewMcpUrlChange,
                                onAddMcp = settingsVm::addMcp,
                                onToggleMcp = settingsVm::toggleMcp,
                                onOpen = { settingsVm.loadAll() }
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
                        is UsageRoute -> NavEntry(key) {
                            UsageScreen(
                                state = chatVm.usageState,
                                onBack = { backStack.removeLastOrNull() },
                                onRequestScan = chatVm::requestUsageScan,
                                onDismissConfirm = chatVm::dismissUsageConfirm,
                                onConfirmScan = chatVm::scanUsage,
                                onCancelScan = chatVm::stopScan,
                                onPeriodChange = chatVm::setUsagePeriod
                            )
                        }
                        else -> error("Unknown route: $key")
                    }
                }
            )
        }
    }
}
