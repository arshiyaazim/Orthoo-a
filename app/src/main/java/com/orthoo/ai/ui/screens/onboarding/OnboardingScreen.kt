package com.orthoo.ai.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthoo.ai.data.model.AppTopics
import com.orthoo.ai.ui.components.BrandMark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onFinish: (selectedTopic: String?, navigateToModels: Boolean) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedTopic by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(size = 32.dp)
                        Text(
                            text = "orthoo-ai",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    TextButton(
                        onClick = { onFinish(selectedTopic, false) },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text("Skip")
                    }
                }

                AnimatedContent(
                    targetState = step,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    label = "onboarding_step"
                ) { currentStep ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (currentStep) {
                            0 -> {
                                BrandMark(size = 84.dp)
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Private AI, right in your pocket",
                                    style = MaterialTheme.typography.displayLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "No accounts, no forced cloud, and full control over every model and tool.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                FeatureRow(
                                    icon = Icons.Outlined.Lock,
                                    title = "Local-first storage",
                                    subtitle = "Chats, personas, and settings stay stored locally in Room & DataStore on your phone."
                                )
                                Spacer(Modifier.height(12.dp))
                                FeatureRow(
                                    icon = Icons.Outlined.CloudQueue,
                                    title = "Local GGUF + OpenAI-compatible servers",
                                    subtitle = "Inspect GGUF files from Hugging Face or stream live from Ollama, LM Studio, llama-server, or HF Router."
                                )
                            }

                            1 -> {
                                Text(
                                    text = "What will you use orthoo-ai for?",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Pick a focus area to tailor starter suggestions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppTopics.onboardingTopics.forEach { (id, label) ->
                                        val isSelected = selectedTopic == id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedTopic = if (isSelected) null else id },
                                            label = { Text(label) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            modifier = Modifier.testTag("onboarding_topic_$id")
                                        )
                                    }
                                }
                            }

                            2 -> {
                                Text(
                                    text = "Meet Pals, Talents & Voice",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Customize reusable AI assistants and roleplay characters with built-in tools.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                FeatureRow(
                                    icon = Icons.Outlined.AutoAwesome,
                                    title = "Pals & Mustache Personas",
                                    subtitle = "Create Assistant or Roleplay Pals with {{world}}, {{location}}, and custom talents."
                                )
                                Spacer(Modifier.height(12.dp))
                                FeatureRow(
                                    icon = Icons.Outlined.Psychology,
                                    title = "Calculator, Clock, HTML & Opt-in Search",
                                    subtitle = "Models can run exact math, read local time, render interactive HTML widgets, or search Wikipedia."
                                )
                                Spacer(Modifier.height(12.dp))
                                FeatureRow(
                                    icon = Icons.Outlined.Mic,
                                    title = "Hands-Free Voice Mode",
                                    subtitle = "Dictate prompts and hear responses read aloud with on-device speech recognition and TTS."
                                )
                            }

                            else -> {
                                Text(
                                    text = "Choose how to get started",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Connect an Ollama / llama-server / Hugging Face endpoint or download GGUF models right from the Models hub.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(28.dp))
                                Button(
                                    onClick = { onFinish(selectedTopic, true) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("onboarding_open_models_button")
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Browse Models & Connect Server")
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { onFinish(selectedTopic, false) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("onboarding_start_chat_button")
                                ) {
                                    Text("Go straight to Chat")
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        repeat(4) { idx ->
                            Surface(
                                color = if (idx == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.size(width = if (idx == step) 24.dp else 8.dp, height = 8.dp)
                            ) {}
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier.testTag("onboarding_back_button")
                            ) {
                                Text("Back")
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }

                        if (step < 3) {
                            Button(
                                onClick = { step++ },
                                modifier = Modifier.testTag("onboarding_next_button")
                            ) {
                                Text("Continue")
                                Spacer(Modifier.size(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
