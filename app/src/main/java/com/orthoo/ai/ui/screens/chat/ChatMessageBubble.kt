package com.orthoo.ai.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orthoo.ai.data.db.ChatMessageEntity
import com.orthoo.ai.data.model.ToolCall
import com.orthoo.ai.data.model.ToolOutcome
import com.orthoo.ai.ui.components.MarkdownText
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    imageUris: List<String>,
    toolCalls: List<ToolCall>,
    toolOutcomes: List<ToolOutcome>,
    isSpeakingThisMessage: Boolean,
    isStreamingThisMessage: Boolean,
    onEditUserMessage: () -> Unit,
    onRetryAssistant: () -> Unit,
    onToggleSpeak: () -> Unit,
    onOpenHtmlPreview: (title: String, html: String) -> Unit
) {
    val isUser = message.role == "user"
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var thinkingExpanded by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                modifier = Modifier.widthIn(max = 310.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (imageUris.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = if (message.text.isNotBlank()) 8.dp else 0.dp)
                        ) {
                            imageUris.forEach { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Attached image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                copied = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        IconButton(
                            onClick = onEditUserMessage,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit and regenerate",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                if (!message.modelName.isNullOrBlank()) {
                    Text(
                        text = message.modelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (!message.reasoningContent.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { thinkingExpanded = !thinkingExpanded },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isStreamingThisMessage) "Thinking…" else "Thought process",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = if (thinkingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (thinkingExpanded) "Collapse thinking" else "Expand thinking",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = thinkingExpanded) {
                                Text(
                                    text = message.reasoningContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (toolCalls.isNotEmpty() || toolOutcomes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        toolCalls.forEach { tc ->
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text(tc.name, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                        toolOutcomes.forEach { outcome ->
                            if (!outcome.htmlPreview.isNullOrBlank()) {
                                AssistChip(
                                    onClick = { onOpenHtmlPreview("Interactive Preview", outcome.htmlPreview) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("Open HTML Preview", style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (message.text.isNotBlank()) {
                            MarkdownText(
                                markdown = message.text,
                                textColor = if (message.isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        } else if (isStreamingThisMessage) {
                            Text(
                                text = "Generating response…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                copied = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRetryAssistant,
                            enabled = !isStreamingThisMessage,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("retry_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Regenerate response",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("speak_message_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeakingThisMessage) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = if (isSpeakingThisMessage) "Stop reading aloud" else "Read aloud",
                                modifier = Modifier.size(16.dp),
                                tint = if (isSpeakingThisMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val metrics = buildList {
                        message.tokensPerSecond?.let { add(String.format(Locale.US, "%.1f tok/s", it)) }
                        message.ttftMs?.let { add("${it}ms TTFT") }
                        message.tokensPredicted?.takeIf { it > 0 }?.let { add("$it tok") }
                    }.joinToString(" • ")

                    if (metrics.isNotEmpty()) {
                        Text(
                            text = metrics,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
