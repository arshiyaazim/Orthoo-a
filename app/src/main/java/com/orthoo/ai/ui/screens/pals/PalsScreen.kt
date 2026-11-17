package com.orthoo.ai.ui.screens.pals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.ui.viewmodel.PalsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PalsScreen(
    viewModel: PalsViewModel,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onStartChatWithPal: (PalEntity) -> Unit
) {
    val pals by viewModel.pals.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var editingPal by remember { mutableStateOf<PalEntity?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("pals_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = { Text("Pals", style = MaterialTheme.typography.headlineSmall) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPal = null
                    showCreateSheet = true
                },
                modifier = Modifier.testTag("create_pal_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create new Pal")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pals, key = { it.id }) { pal ->
                var menuOpen by remember { mutableStateOf(false) }
                val talents = viewModel.decodeTalents(pal)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingPal = pal
                            showCreateSheet = true
                        }
                        .testTag("pal_card_${pal.id}"),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(pal.colorPrimaryHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(pal.colorSecondaryHex))
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(pal.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    pal.palType.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Pal options")
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            editingPal = pal
                                            showCreateSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.duplicatePal(pal.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy JSON") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            clipboard.setText(AnnotatedString(viewModel.exportPalJson(pal)))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.deletePal(pal.id)
                                        }
                                    )
                                }
                            }
                        }

                        if (pal.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = pal.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (talents.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                talents.forEach { t ->
                                    AssistChip(onClick = {}, label = { Text(t, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onStartChatWithPal(pal) },
                            modifier = Modifier.testTag("chat_with_pal_${pal.id}")
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Chat")
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        PalSheet(
            viewModel = viewModel,
            existingPal = editingPal,
            models = models,
            onDismiss = { showCreateSheet = false }
        )
    }
}
