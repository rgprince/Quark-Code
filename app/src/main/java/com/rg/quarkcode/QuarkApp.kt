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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rg.quarkcode.chat.ChatPrefs
import com.rg.quarkcode.chat.ChatRoute
import com.rg.quarkcode.chat.ChatScreen
import com.rg.quarkcode.chat.UsageScreen
import com.rg.quarkcode.chat.ChatViewModel
import com.rg.quarkcode.chat.ReviewScreen
import com.rg.quarkcode.settings.ProvidersScreen
import com.rg.quarkcode.connect.ConnectScreen
import com.rg.quarkcode.connect.ConnectViewModel
import com.rg.quarkcode.drawer.QuarkDrawer
import com.rg.quarkcode.files.FilesScreen
import com.rg.quarkcode.files.FilesViewModel
import com.rg.quarkcode.files.SandboxScreen
import com.rg.quarkcode.settings.SettingsScreen
import com.rg.quarkcode.settings.WelcomeOverlay
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
private data object FilesRoute
private data object SandboxRoute

@Composable
fun QuarkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeStore = remember { ThemeStore(context.applicationContext) }
    val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.LIGHT)
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
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
    val filesVm: FilesViewModel = viewModel()
    val chatPrefs = remember(context.applicationContext) {
        ChatPrefs(context.applicationContext)
    }
    var showWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val seen = runCatching {
            withContext(Dispatchers.IO) { chatPrefs.prefs.first().welcomeSeen }
        }.getOrNull() ?: true
        showWelcome = !seen
    }

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
                onOpenFiles = {
                    closeDrawer()
                    backStack.add(FilesRoute)
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
                                comfortable = chatVm.uiState.comfortable,
                                onComfortableChange = chatVm::setComfortable,
                                showTimestamps = chatVm.uiState.showTimestamps,
                                onShowTimestampsChange = chatVm::setShowTimestamps,
                                sendBehavior = chatVm.uiState.sendBehavior,
                                onSendBehaviorChange = chatVm::setSendBehavior,
                                autoScroll = chatVm.uiState.autoScroll,
                                onAutoScrollChange = chatVm::setAutoScroll,
                                playfulStatus = chatVm.uiState.playfulStatus,
                                onPlayfulChange = chatVm::setPlayfulStatus,
                                showThoughts = chatVm.uiState.showThoughts,
                                onShowThoughtsChange = chatVm::setShowThoughts,
                                onClearCache = chatVm::clearLaunchCache,
                                onOpenUsage = { backStack.add(UsageRoute) },
                                onOpenFiles = { backStack.add(FilesRoute) },
                                onOpenSandbox = { backStack.add(SandboxRoute) },
                                onNewMcpNameChange = settingsVm::onNewMcpNameChange,
                                onNewMcpUrlChange = settingsVm::onNewMcpUrlChange,
                                onAddMcp = settingsVm::addMcp,
                                onToggleMcp = settingsVm::toggleMcp,
                                onShowWelcome = { showWelcome = true },
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
                        is FilesRoute -> NavEntry(key) {
                            FilesScreen(
                                state = filesVm.uiState,
                                onBack = { backStack.removeLastOrNull() },
                                onOpen = filesVm::open,
                                onSwitchRoot = filesVm::switchRoot,
                                onNavigate = filesVm::navigate,
                                onCrumb = filesVm::goCrumb,
                                onShowHidden = filesVm::setShowHidden,
                                onSortOpen = { filesVm.setSortOpen(true) },
                                onSort = filesVm::setSort,
                                onSortDismiss = { filesVm.setSortOpen(false) },
                                onMenu = filesVm::setMenu,
                                onSearching = filesVm::setSearching,
                                onQuery = filesVm::setQuery,
                                onToggleSelect = filesVm::toggleSelect,
                                onSelectAll = filesVm::selectAll,
                                onClearSelection = filesVm::clearSelection,
                                onCopy = filesVm::copySelection,
                                onCut = filesVm::cutSelection,
                                onPaste = filesVm::paste,
                                onClearClip = filesVm::clearClip,
                                onCreate = filesVm::setCreate,
                                onCreateConfirm = filesVm::confirmCreate,
                                onRename = filesVm::setRename,
                                onRenameConfirm = filesVm::confirmRename,
                                onDelete = filesVm::setDelete,
                                onDeleteConfirm = filesVm::confirmDelete,
                                onDeleteDismiss = { filesVm.setDelete(emptyList()) },
                                onProps = filesVm::setProps,
                                onTextOpen = filesVm::openText,
                                onTextEdit = filesVm::editText,
                                onTextSave = filesVm::saveText,
                                onTextClose = filesVm::closeText,
                                onZipOpen = filesVm::openZip,
                                onZipClose = filesVm::closeZip,
                                onZipExtract = filesVm::extractZip,
                                onImageOpen = filesVm::openImage,
                                onImageClose = filesVm::closeImage,
                                onNoticeShown = filesVm::clearNotice,
                                onImport = filesVm::importUris,
                                onExportRequest = filesVm::setExport,
                                onExportResult = filesVm::exportTo,
                                onShareSelection = filesVm::shareSelection,
                                onShareFile = filesVm::shareFile,
                                onOpenWith = filesVm::openWith
                            )
                        }
                        is SandboxRoute -> NavEntry(key) {
                            SandboxScreen(
                                onBack = { backStack.removeLastOrNull() }
                            )
                        }
                        else -> error("Unknown route: $key")
                    }
                }
            )
            if (showWelcome) {
                WelcomeOverlay(
                    onClose = {
                        showWelcome = false
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    chatPrefs.setWelcomeSeen(true)
                                }
                            }
                        }
                    },
                    onOpenSettings = { backStack.add(SettingsRoute) }
                )
            }
        }
    }
}
