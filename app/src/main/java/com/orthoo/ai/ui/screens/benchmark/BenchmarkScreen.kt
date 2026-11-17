package com.orthoo.ai.ui.screens.benchmark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthoo.ai.data.db.BenchmarkResultEntity
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.model.BenchmarkConfig
import com.orthoo.ai.data.model.DeviceInfo
import com.orthoo.ai.ui.components.Formatters
import com.orthoo.ai.ui.viewmodel.BenchmarkViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val progressMessage by viewModel.progressMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val readyModels = remember(models) { models.filter { it.isDownloaded } }
    var selectedModel by remember(readyModels) { mutableStateOf<ModelEntity?>(readyModels.firstOrNull()) }
    var ppTokens by remember { mutableIntStateOf(512) }
    var tgTokens by remember { mutableIntStateOf(128) }
    var repeatCount by remember { mutableIntStateOf(3) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("benchmark_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = { Text("Benchmark", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (results.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAllResults() }) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                deviceInfo?.let { HardwareCapabilityCard(it) }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Run Throughput Test", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Measures remote endpoint TTFT & tok/s (for Remote models) or on-device FP32 SIMD matrix-vector compute throughput.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        Text("Target", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            FilterChip(
                                selected = selectedModel == null,
                                onClick = { selectedModel = null },
                                label = { Text("Device CPU Kernel") }
                            )
                            readyModels.forEach { m ->
                                FilterChip(
                                    selected = selectedModel?.id == m.id,
                                    onClick = { selectedModel = m },
                                    label = { Text(m.name) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Prompt / Gen / Runs", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            listOf(256, 512, 1024).forEach { pp ->
                                FilterChip(
                                    selected = ppTokens == pp,
                                    onClick = { ppTokens = pp },
                                    label = { Text("pp=$pp") }
                                )
                            }
                            listOf(64, 128, 256).forEach { tg ->
                                FilterChip(
                                    selected = tgTokens == tg,
                                    onClick = { tgTokens = tg },
                                    label = { Text("tg=$tg") }
                                )
                            }
                            listOf(1, 3, 5).forEach { nr ->
                                FilterChip(
                                    selected = repeatCount == nr,
                                    onClick = { repeatCount = nr },
                                    label = { Text("x$nr") }
                                )
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (isRunning) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(progressMessage, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { viewModel.cancelBenchmark() }) {
                                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop")
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.runBenchmark(
                                        selectedModel,
                                        BenchmarkConfig(pp = ppTokens, tg = tgTokens, nr = repeatCount)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("run_benchmark_button")
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Run Benchmark")
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Text(
                        "History (${results.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(results, key = { it.id }) { res ->
                    BenchmarkResultCard(
                        result = res,
                        onDelete = { viewModel.deleteResult(res.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HardwareCapabilityCard(info: DeviceInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("${info.manufacturer} ${info.model}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android ${info.androidVersion} • ${info.chipset} • ${info.cpuInfo.cores} cores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "RAM: ${Formatters.formatBytes(info.availableMemoryBytes)} free / ${Formatters.formatBytes(info.totalMemoryBytes)}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Disk: ${Formatters.formatBytes(info.freeStorageBytes)} free",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("GPU: ${info.gpuInfo.gpuType}", style = MaterialTheme.typography.bodySmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (info.cpuInfo.hasFp16) AssistChip(onClick = {}, label = { Text("FP16", style = MaterialTheme.typography.labelSmall) })
                if (info.cpuInfo.hasDotProd) AssistChip(onClick = {}, label = { Text("DotProd", style = MaterialTheme.typography.labelSmall) })
                if (info.cpuInfo.hasI8mm) AssistChip(onClick = {}, label = { Text("I8MM", style = MaterialTheme.typography.labelSmall) })
                if (info.cpuInfo.hasSve) AssistChip(onClick = {}, label = { Text("SVE", style = MaterialTheme.typography.labelSmall) })
                if (info.gpuInfo.supportsOpenCl) AssistChip(onClick = {}, label = { Text("OpenCL", style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(
    result: BenchmarkResultEntity,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.modelName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${result.backend} • pp${result.ppTokens}/tg${result.tgTokens} (x${result.repeatCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete benchmark result", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Prompt Eval", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.1f tok/s", result.ppAvg),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("Token Gen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.1f tok/s", result.tgAvg),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Peak App Mem", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.0f MB", result.peakMemoryMb),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}
