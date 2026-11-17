package com.orthoo.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orthoo.ai.data.model.DownloadProgress

@Composable
fun DownloadBanner(
    downloads: Map<String, DownloadProgress>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = downloads.values.firstOrNull() ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Downloading model",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Downloading ${active.modelId.substringAfterLast("/")}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                IconButton(
                    onClick = { onCancel(active.modelId) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("cancel_download_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel download",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { active.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            val pct = (active.fraction * 100).toInt()
            val stats = buildString {
                append("$pct%")
                if (active.totalBytes > 0L) {
                    append(" • ${Formatters.formatBytes(active.downloadedBytes)} / ${Formatters.formatBytes(active.totalBytes)}")
                }
                if (active.speedBytesPerSec > 0L) {
                    append(" • ${Formatters.formatSpeed(active.speedBytesPerSec)}")
                }
            }
            Text(
                text = stats,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}
