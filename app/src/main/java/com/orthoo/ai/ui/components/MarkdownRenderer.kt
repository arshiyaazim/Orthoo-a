package com.orthoo.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orthoo.ai.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeFence(val lang: String, val code: String) : MdBlock
    data class BulletItem(val text: String) : MdBlock
    data class NumberedItem(val number: String, val text: String) : MdBlock
    data class BlockQuote(val text: String) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeBorder = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (block in blocks) {
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }
                        Text(
                            text = parseInlineMarkdown(block.text, textColor, accent, codeBg),
                            style = style,
                            color = textColor
                        )
                    }
                    is MdBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text, textColor, accent, codeBg),
                            style = baseStyle,
                            color = textColor
                        )
                    }
                    is MdBlock.BulletItem -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•",
                                style = baseStyle.copy(fontWeight = FontWeight.Bold),
                                color = accent,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text, textColor, accent, codeBg),
                                style = baseStyle,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MdBlock.NumberedItem -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${block.number}.",
                                style = baseStyle.copy(fontWeight = FontWeight.SemiBold),
                                color = accent,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text, textColor, accent, codeBg),
                                style = baseStyle,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MdBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeBg.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(accent, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = parseInlineMarkdown(block.text, textColor.copy(alpha = 0.88f), accent, codeBg),
                                style = baseStyle.copy(fontStyle = FontStyle.Italic),
                                color = textColor.copy(alpha = 0.88f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MdBlock.CodeFence -> {
                        CodeBlockCard(
                            language = block.lang,
                            code = block.code,
                            backgroundColor = codeBg,
                            borderColor = codeBorder
                        )
                    }
                    MdBlock.Divider -> {
                        HorizontalDivider(color = codeBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    backgroundColor: Color,
    borderColor: Color
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(borderColor.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (copied) "Copied code" else "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = if (copied) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val lines = input.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    val paraBuffer = mutableListOf<String>()
    var inCode = false
    var codeLang = ""
    val codeLines = mutableListOf<String>()

    fun flushPara() {
        if (paraBuffer.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraBuffer.joinToString("\n")))
            paraBuffer.clear()
        }
    }

    for (raw in lines) {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushPara()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeLines.clear()
            } else {
                blocks.add(MdBlock.CodeFence(codeLang, codeLines.joinToString("\n")))
                inCode = false
                codeLang = ""
                codeLines.clear()
            }
            continue
        }

        if (inCode) {
            codeLines.add(raw)
            continue
        }

        if (trimmed.isEmpty()) {
            flushPara()
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushPara()
            blocks.add(MdBlock.Divider)
            continue
        }

        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushPara()
            blocks.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            continue
        }

        if (trimmed.startsWith("> ")) {
            flushPara()
            blocks.add(MdBlock.BlockQuote(trimmed.removePrefix("> ").trim()))
            continue
        }

        val bulletMatch = Regex("^[-*+]\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushPara()
            blocks.add(MdBlock.BulletItem(bulletMatch.groupValues[1]))
            continue
        }

        val numMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(trimmed)
        if (numMatch != null) {
            flushPara()
            blocks.add(MdBlock.NumberedItem(numMatch.groupValues[1], numMatch.groupValues[2]))
            continue
        }

        paraBuffer.add(raw)
    }

    if (inCode && codeLines.isNotEmpty()) {
        blocks.add(MdBlock.CodeFence(codeLang, codeLines.joinToString("\n")))
    }
    flushPara()
    return blocks
}

private fun parseInlineMarkdown(
    text: String,
    textColor: Color,
    accentColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            background = codeBg,
                            color = accentColor
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append('`')
                    i++
                }
            }
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = text.substring(i + 1, closeBracket)
                        withStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
                append('[')
                i++
            }
            text[i] == '*' && (i + 1 < text.length && text[i + 1] != ' ') -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append('*')
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
