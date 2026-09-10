package com.rg.quarkcode.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.chat.RecentSession

// Drawer structure ported from AndCode (header, new chat, agents,
// projects, recents, schedules, settings) in Quark styling.
@Composable
fun QuarkDrawer(
    agents: List<String>,
    selectedAgent: String,
    projects: List<String>,
    selectedProject: String,
    recents: List<RecentSession>,
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = "Quark Code",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            DrawerEntry(
                label = "New chat",
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNewChat
            )
            if (agents.size > 1) {
                DrawerHeader(title = "Agents")
                agents.forEach { agent ->
                    NavigationDrawerItem(
                        label = { Text(agent) },
                        selected = agent == selectedAgent,
                        onClick = { onSelectAgent(agent) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            DrawerHeader(title = "Projects")
            projects.forEach { project ->
                NavigationDrawerItem(
                    label = { Text(project) },
                    selected = project == selectedProject,
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    onClick = { onSelectProject(project) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (recents.isNotEmpty()) {
                DrawerHeader(title = "Recent chats")
                recents.forEach { recent ->
                    NavigationDrawerItem(
                        label = { Text(recent.title) },
                        selected = false,
                        onClick = { onOpenSession(recent.id) },
                        badge = {
                            androidx.compose.material3.TextButton(
                                onClick = { onDeleteSession(recent.id) }
                            ) {
                                Text("Delete")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
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
