package com.rg.quarkcode.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.chat.RecentSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Qwen-style drawer: identity header, search, action rows ABOVE the chats
// (New chat / Workspace files / Review), then day-grouped chats with large
// titles and a selected pill. No ModalDrawerSheet — the parent push-panel
// hosts this Column at a fixed 320.dp width.
@Composable
fun QuarkDrawer(
    recents: List<RecentSession>,
    recentsError: String? = null,
    hasSession: Boolean,
    selectedId: String? = null,
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshRecents: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        val context = LocalContext.current
        // Real version, not a hardcoded string (was stale v0.1.0).
        val versionLine = remember(context) {
            runCatching {
                val pm = context.packageManager
                val pkg = if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.TIRAMISU
                ) {
                    pm.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }
                "native opencode · v${pkg.versionName}"
            }.getOrDefault("native opencode")
        }
        var query by remember { mutableStateOf("") }
        val filtered = remember(recents, query) {
            if (query.isBlank()) recents
            else recents.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quark Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = versionLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Actions above the chats: new, workspace, review.
            DrawerEntry(
                label = "New Chat",
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNewChat
            )
            DrawerEntry(
                label = "Workspace files",
                icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = onOpenFiles
            )
            if (hasSession) {
                DrawerEntry(
                    label = "Review changes",
                    icon = { Icon(Icons.Filled.Difference, contentDescription = null) },
                    onClick = onOpenReview
                )
            }
            if (filtered.isEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text(
                        text = if (query.isNotBlank()) "No chats match \"$query\"."
                        else recentsError ?: "No chats yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recentsError != null && query.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (query.isBlank()) {
                        TextButton(onClick = onRefreshRecents) {
                            Text(
                                text = "Tap to reload",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            } else {
                groupRecents(filtered).forEach { row ->
                    when (row) {
                        is DrawerRow.Header -> DrawerSectionHeader(title = row.title)
                        is DrawerRow.Chat -> key(row.session.id) {
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        row.session.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                selected = row.session.id == selectedId,
                                icon = {
                                    Icon(
                                        Icons.Filled.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = { onOpenSession(row.session.id) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DrawerSectionHeader(title = "System")
            DrawerEntry(
                label = "Settings",
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Day-grouped rows: Today / Yesterday / Previous 7 days / Previous 30 days /
// Older. Sessions without a timestamp (legacy cache) lead as "Recent".
private sealed interface DrawerRow {
    data class Header(val title: String) : DrawerRow
    data class Chat(val session: RecentSession) : DrawerRow
}

private fun groupRecents(recents: List<RecentSession>): List<DrawerRow> {
    if (recents.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun ageDays(ts: Long?): Long? {
        if (ts == null || ts <= 0L) return null
        return try {
            val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            today.toEpochDay() - day.toEpochDay()
        } catch (_: Exception) {
            null
        }
    }
    // Stable order: undated first ("Recent"), then newest first.
    val ordered = recents.sortedWith(
        compareBy<RecentSession> { ageDays(it.updated) != null }
            .thenByDescending { it.updated ?: Long.MAX_VALUE }
    )
    val rows = mutableListOf<DrawerRow>()
    var lastBucket: String? = null
    ordered.forEach { session ->
        val bucket = when (val age = ageDays(session.updated)) {
            null -> "Recent"
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..7L -> "Previous 7 days"
            in 8L..30L -> "Previous 30 days"
            else -> "Older"
        }
        if (bucket != lastBucket) {
            rows.add(DrawerRow.Header(bucket))
            lastBucket = bucket
        }
        rows.add(DrawerRow.Chat(session))
    }
    return rows
}

@Composable
private fun DrawerSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        selected = false,
        icon = icon,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp)
    )
}
