package com.pocketops.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    smallFontSize: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val typography = if (smallFontSize) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val bodySize = if (smallFontSize) 13.sp else 14.sp
    val bodyLineHeight = if (smallFontSize) 18.sp else 21.sp

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(block.topPadding))
            when (block.type) {
                MarkdownBlockType.Heading -> {
                    Text(
                        text = parseInlineMarkdown(block.text, linkColor),
                        color = textColor,
                        style = typography,
                        fontSize = headingFontSize(block.level, smallFontSize),
                        fontWeight = FontWeight.Bold,
                        lineHeight = if (smallFontSize) 20.sp else 24.sp,
                    )
                }
                MarkdownBlockType.Bullet -> {
                    Row(Modifier.fillMaxWidth().padding(start = (block.indent * 14).dp)) {
                        Text("•", color = textColor, fontSize = bodySize, lineHeight = bodyLineHeight)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, linkColor),
                            color = textColor,
                            style = typography,
                            fontSize = bodySize,
                            lineHeight = bodyLineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MarkdownBlockType.Numbered -> {
                    Row(Modifier.fillMaxWidth().padding(start = (block.indent * 14).dp)) {
                        Text("${block.marker}.", color = textColor, fontSize = bodySize, lineHeight = bodyLineHeight)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, linkColor),
                            color = textColor,
                            style = typography,
                            fontSize = bodySize,
                            lineHeight = bodyLineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MarkdownBlockType.Quote -> {
                    Row(Modifier.fillMaxWidth()) {
                        Text("│", color = linkColor, fontSize = bodySize, lineHeight = bodyLineHeight)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, linkColor),
                            color = textColor.copy(alpha = 0.82f),
                            style = typography,
                            fontSize = bodySize,
                            fontStyle = FontStyle.Italic,
                            lineHeight = bodyLineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MarkdownBlockType.Code -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.text,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (smallFontSize) 12.sp else 13.sp,
                            lineHeight = if (smallFontSize) 17.sp else 19.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                MarkdownBlockType.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, linkColor),
                        color = textColor,
                        style = typography,
                        fontSize = bodySize,
                        lineHeight = bodyLineHeight,
                    )
                }
            }
        }
    }
}

private enum class MarkdownBlockType {
    Heading,
    Paragraph,
    Bullet,
    Numbered,
    Quote,
    Code,
}

private data class MarkdownBlock(
    val type: MarkdownBlockType,
    val text: String,
    val level: Int = 0,
    val marker: String = "",
    val indent: Int = 0,
) {
    val topPadding = when (type) {
        MarkdownBlockType.Heading -> 8.dp
        MarkdownBlockType.Code -> 8.dp
        else -> 5.dp
    }
}

private fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val lines = rawText
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .trim()
        .lines()
    if (lines.isEmpty()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCodeFence = false

    fun flushParagraph() {
        val value = paragraph.joinToString("\n").trim()
        if (value.isNotBlank()) blocks.add(MarkdownBlock(MarkdownBlockType.Paragraph, value))
        paragraph.clear()
    }

    fun flushCode() {
        blocks.add(MarkdownBlock(MarkdownBlockType.Code, code.joinToString("\n").trimEnd()))
        code.clear()
    }

    lines.forEach { originalLine ->
        val line = originalLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeFence) {
                flushCode()
                inCodeFence = false
            } else {
                flushParagraph()
                inCodeFence = true
            }
            return@forEach
        }
        if (inCodeFence) {
            code.add(originalLine)
            return@forEach
        }
        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val heading = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock(
                    type = MarkdownBlockType.Heading,
                    text = heading.groupValues[2].trim(),
                    level = heading.groupValues[1].length,
                ),
            )
            return@forEach
        }

        val quote = Regex("""^>\s*(.+)$""").find(trimmed)
        if (quote != null) {
            flushParagraph()
            blocks.add(MarkdownBlock(MarkdownBlockType.Quote, quote.groupValues[1].trim()))
            return@forEach
        }

        val bullet = Regex("""^(\s*)[-*•]\s+(.+)$""").find(line)
        if (bullet != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock(
                    type = MarkdownBlockType.Bullet,
                    text = bullet.groupValues[2].trim(),
                    indent = (bullet.groupValues[1].length / 2).coerceIn(0, 3),
                ),
            )
            return@forEach
        }

        val numbered = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""").find(line)
        if (numbered != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock(
                    type = MarkdownBlockType.Numbered,
                    text = numbered.groupValues[3].trim(),
                    marker = numbered.groupValues[2],
                    indent = (numbered.groupValues[1].length / 2).coerceIn(0, 3),
                ),
            )
            return@forEach
        }

        val tableSeparator = trimmed.matches(Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$"""))
        if (tableSeparator) {
            flushParagraph()
            return@forEach
        }

        val tableLike = trimmed.startsWith("|") && trimmed.endsWith("|")
        if (tableLike) {
            flushParagraph()
            blocks.add(MarkdownBlock(MarkdownBlockType.Paragraph, trimmed.trim('|').split('|').joinToString("  |  ") { it.trim() }))
            return@forEach
        }

        paragraph.add(line)
    }
    if (inCodeFence && code.isNotEmpty()) flushCode()
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock(MarkdownBlockType.Paragraph, rawText)) }
}

private fun parseInlineMarkdown(text: String, linkColor: Color) = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text.startsWith("__", index) -> {
                val end = text.indexOf("__", startIndex = index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf("](", startIndex = index + 1)
                val urlEnd = if (labelEnd > 0) text.indexOf(')', startIndex = labelEnd + 2) else -1
                if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append(text.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

private fun headingFontSize(level: Int, smallFontSize: Boolean): TextUnit {
    val base = if (smallFontSize) 13 else 14
    val extra = when (level) {
        1 -> 5
        2 -> 4
        3 -> 3
        else -> 2
    }
    return (base + extra).sp
}
