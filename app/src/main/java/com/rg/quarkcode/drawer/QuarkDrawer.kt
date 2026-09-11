package com.rg.quarkcode.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.chat.RecentSession

// Expressive drawer: FAB new chat, recents (open only), review, schedules, settings.
@Composable
fun QuarkDrawer(
    recents: List<RecentSession>,
    hasSession: Boolean,
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Quark Code",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                text = "Native opencode, on your phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New chat") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (recents.isNotEmpty()) {
                DrawerHeader(title = "Recent chats")
                recents.forEach { recent ->
                    key(recent.id) {
                        NavigationDrawerItem(
                            label = { Text(recent.title, maxLines = 1) },
                            selected = false,
                            onClick = { onOpenSession(recent.id) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            if (hasSession) {
                DrawerEntry(
                    label = "Review changes",
                    icon = { Icon(Icons.Filled.Difference, contentDescription = null) },
                    onClick = onOpenReview
                )
            }
            DrawerEntry(
                label = "Schedules",
                icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                onClick = onOpenSchedules
            )
            DrawerEntry(
                label = "Settings",
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun DrawerHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp)
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
        label = { Text(label) },
        selected = false,
        icon = icon,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp)
    )
}
