package com.orthoo.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orthoo.ai.R
import com.orthoo.ai.ui.theme.AmberTertiary
import com.orthoo.ai.ui.theme.EmeraldSecondary
import com.orthoo.ai.ui.theme.IndigoPrimary
import java.util.Locale

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    useRasterMark: Boolean = true
) {
    if (useRasterMark) {
        Image(
            painter = painterResource(id = R.drawable.orthoo_ai_mark),
            contentDescription = "orthoo-ai logo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.25f))
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = this.size.minDimension * 0.14f
                val r = this.size.minDimension * 0.28f
                val cy = this.size.height * 0.52f
                drawCircle(
                    color = IndigoPrimary,
                    radius = r,
                    center = Offset(this.size.width * 0.36f, cy),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = EmeraldSecondary,
                    radius = r,
                    center = Offset(this.size.width * 0.64f, cy),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = AmberTertiary,
                    radius = this.size.minDimension * 0.09f,
                    center = Offset(this.size.width * 0.50f, this.size.height * 0.22f)
                )
            }
        }
    }
}

object Formatters {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatCount(count: Long): String = when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
