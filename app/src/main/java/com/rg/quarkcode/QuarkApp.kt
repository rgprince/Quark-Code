package com.rg.quarkcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.rg.quarkcode.chat.ChatRoute
import com.rg.quarkcode.chat.ChatScreen
import com.rg.quarkcode.chat.ChatViewModel
import com.rg.quarkcode.connect.ConnectScreen
import com.rg.quarkcode.connect.ConnectViewModel

private data object ConnectRoute

@Composable
fun QuarkApp(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Any>(ConnectRoute) }
    val connectVm: ConnectViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()

    NavDisplay(
        modifier = modifier,
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
                                backStack.add(ChatRoute("local"))
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
                        onAnswer = chatVm::answerQuestion
                    )
                }
                else -> error("Unknown route: $key")
            }
        }
    )
}
