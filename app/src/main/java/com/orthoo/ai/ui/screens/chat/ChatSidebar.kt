package com.orthoo.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orthoo.ai.data.db.ChatSessionEntity
import com.orthoo.ai.ui.components.BrandMark
import com.orthoo.ai.ui.viewmodel.SessionGroup

@Composable
fun ChatSidebarContent(
    groupedSessions: List<SessionGroup>,
    activeSessionId: String?,
    searchQuery: String,
    selectedIds: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onToggleSelectSession: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onTogglePinSession: (String) -> Unit,
    onDuplicateSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNavigatePals: () -> Unit,
    onNavigateModels: () -> Unit,
    onNavigateBenchmark: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit
) {
    var renamingSession by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 32.dp)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text("orthoo-ai", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Private AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("sidebar_new_chat_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New Chat")
            }
        }

        if (selectedIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${selectedIds.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row {
                        TextButton(onClick = onClearSelection) { Text("Cancel") }
                        TextButton(onClick = onDeleteSelected) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search chats…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("sidebar_search_input")
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            if (groupedSessions.isEmpty()) {
                item {
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            groupedSessions.forEach { group ->
                item(key = "header_${group.label}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                items(group.sessions, key = { it.id }) { session ->
                    SessionDrawerRow(
                        session = session,
                        isActive = session.id == activeSessionId,
                        isSelectionMode = selectedIds.isNotEmpty(),
                        isSelected = session.id in selectedIds,
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onToggleSelectSession(session.id)
                            } else {
                                onSelectSession(session.id)
                            }
                        },
                        onToggleSelect = { onToggleSelectSession(session.id) },
                        onRename = {
                            renamingSession = session
                            renameText = session.title
                        },
                        onTogglePin = { onTogglePinSession(session.id) },
                        onDuplicate = { onDuplicateSession(session.id) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SidebarNavLink("Pals", Icons.Outlined.Face, "drawer_nav_pals", onNavigatePals)
        SidebarNavLink("Models", Icons.Outlined.FolderOpen, "drawer_nav_models", onNavigateModels)
        SidebarNavLink("Benchmark", Icons.Outlined.Speed, "drawer_nav_benchmark", onNavigateBenchmark)
        SidebarNavLink("Settings", Icons.Outlined.Settings, "drawer_nav_settings", onNavigateSettings)
        SidebarNavLink("App Info", Icons.Outlined.Info, "drawer_nav_about", onNavigateAbout)
    }

    renamingSession?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Chat title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameSession(target.id, renameText)
                        renamingSession = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SessionDrawerRow(
    session: ChatSessionEntity,
    isActive: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            IconButton(onClick = onToggleSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = "Select chat",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (session.isPinned) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = "Pinned",
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
        }

        Text(
            text = session.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Chat options",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (session.isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select multiple") },
                    leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onToggleSelect()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun SidebarNavLink(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(46.dp)
            .testTag(testTag)
    )
}
