package com.orthoo.ai.ui.screens.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.orthoo.ai.BuildConfig
import com.orthoo.ai.data.model.DeviceInfo
import com.orthoo.ai.services.hardware.HardwareInfoProvider
import com.orthoo.ai.ui.components.BrandMark
import com.orthoo.ai.ui.components.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    hardwareInfoProvider: HardwareInfoProvider,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit
) {
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        deviceInfo = hardwareInfoProvider.collectDeviceInfo()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("about_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = { Text("App Info", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandMark(size = 56.dp)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text("orthoo-ai", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Native Kotlin & Jetpack Compose rewrite preserving private chats, Pals, GGUF hub, remote servers, and hardware benchmarks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Privacy Pledge", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "All chat sessions, Pals, model configurations, and benchmark logs are stored locally on your device in Room and DataStore. Network requests only occur when you browse Hugging Face, connect a remote server, or explicitly opt in to Internet Search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            deviceInfo?.let { info ->
                item {
                    val summaryText = buildString {
                        appendLine("orthoo-ai ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Device: ${info.manufacturer} ${info.model}")
                        appendLine("Android: ${info.androidVersion} (API ${info.sdkInt})")
                        appendLine("Chipset: ${info.chipset} | Cores: ${info.cpuInfo.cores}")
                        appendLine("ABIs: ${info.abis.joinToString(", ")}")
                        appendLine("RAM: ${Formatters.formatBytes(info.totalMemoryBytes)}")
                        appendLine("GPU: ${info.gpuInfo.renderer.ifBlank { info.gpuInfo.gpuType }}")
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(summaryText.trim(), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(summaryText)) }
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Diagnostics")
                            }
                        }
                    }
                }
            }
        }
    }
}
