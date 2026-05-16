package com.yueming.baby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    codeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
) {
    val textColor = style.color ?: Color.Unspecified
    val lines = markdown.split("\n")
    val isInCodeBlock = { s: String -> s.trimStart().startsWith("```") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Fenced code block
                trimmed.startsWith("```") -> {
                    val language = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // skip closing ```
                    if (codeLines.isNotEmpty()) {
                        CodeBlock(codeLines.joinToString("\n"), language, codeBackgroundColor)
                    }
                }
                // Heading
                trimmed.startsWith("### ") -> {
                    Text(
                        trimmed.removePrefix("### "),
                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = (style.fontSize * 1.08f)),
                        color = textColor
                    )
                    i++
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        trimmed.removePrefix("## "),
                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = (style.fontSize * 1.15f)),
                        color = textColor
                    )
                    i++
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        trimmed.removePrefix("# "),
                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = (style.fontSize * 1.22f)),
                        color = textColor
                    )
                    i++
                }
                // Unordered list
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val prefix = if (trimmed.startsWith("- ")) "- " else "* "
                    Row(Modifier.padding(start = 8.dp)) {
                        Text("•  ", style = style, color = textColor)
                        RichLine(trimmed.removePrefix(prefix), style, textColor)
                    }
                    i++
                }
                // Ordered list
                Regex("^\\d+\\.\\s").find(trimmed) != null -> {
                    val match = Regex("^(\\d+\\.)\\s").find(trimmed)!!
                    val num = match.groupValues[1]
                    Row(Modifier.padding(start = 8.dp)) {
                        Text("$num  ", style = style, color = textColor)
                        RichLine(trimmed.removePrefix(num).trim(), style, textColor)
                    }
                    i++
                }
                // Horizontal rule
                trimmed.matches(Regex("^-{3,}$")) || trimmed.matches(Regex("^\\*{3,}$")) -> {
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).padding(vertical = 4.dp)
                            .background(textColor.copy(alpha = 0.15f))
                    )
                    i++
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    Row(
                        Modifier.padding(start = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            Modifier.width(3.dp).fillMaxHeight().padding(end = 6.dp)
                                .background(textColor.copy(alpha = 0.2f))
                        )
                        RichLine(trimmed.removePrefix("> "), style, textColor)
                    }
                    i++
                }
                // Empty line
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(4.dp))
                    i++
                }
                // Table
                isTableRow(trimmed) && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim()) -> {
                    val headerCells = parseTableRow(trimmed)
                    val dataRows = mutableListOf<List<String>>()
                    i += 2 // skip header and separator
                    while (i < lines.size && isTableRow(lines[i].trim())) {
                        dataRows.add(parseTableRow(lines[i].trim()))
                        i++
                    }
                    // i now points to first non-table line, don't increment here
                    MarkdownTable(headerCells, dataRows)
                }
                // Regular paragraph
                else -> {
                    RichLine(line, style, textColor)
                    i++
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String, bgColor: Color) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(10.dp)
    ) {
        if (language.isNotEmpty()) {
            Text(
                language,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.endsWith("|") &&
           trimmed.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty()
}

private fun parseTableRow(line: String): List<String> {
    return line.trim().removeSurrounding("|").split("|").map { it.trim() }
}

@Composable
private fun MarkdownTable(headerCells: List<String>, dataRows: List<List<String>>) {
    val colorScheme = MaterialTheme.colorScheme
    val headerBg = colorScheme.surfaceVariant
    val dividerColor = colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(4.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(headerBg, RoundedCornerShape(4.dp))
            ) {
                headerCells.forEachIndexed { index, cell ->
                    if (index > 0) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            cell,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = textColor
                        )
                    }
                }
            }
            // Data rows
            dataRows.forEachIndexed { rowIndex, row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                val rowBg = if (rowIndex % 2 == 1)
                    colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else
                    Color.Transparent
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(rowBg)
                ) {
                    row.forEachIndexed { cellIndex, cell ->
                        if (cellIndex > 0) {
                            Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
                        }
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                cell,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RichLine(text: String, baseStyle: TextStyle, textColor: Color) {
    val annotated = remember(text) { buildMarkdownAnnotatedString(text, baseStyle, textColor) }
    Text(annotated, style = baseStyle)
}

private fun buildMarkdownAnnotatedString(
    text: String,
    baseStyle: TextStyle,
    textColor: Color
): AnnotatedString = buildAnnotatedString {
    val fontSize = baseStyle.fontSize
    var i = 0
    while (i < text.length) {
        when {
            // Bold with **
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            // Bold with __
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            // Italic with *
            text.startsWith("*", i) && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf("*", i + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            // Italic with _
            text.startsWith("_", i) && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf("_", i + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            // Inline code with `
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize.value - 1).sp,
                        background = textColor.copy(alpha = 0.08f)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            // Strikethrough with ~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            // Link [text](url) — render text part only
            text.startsWith("[", i) -> {
                val closeBracket = text.indexOf("](", i)
                val closeParen = if (closeBracket >= 0) text.indexOf(")", closeBracket) else -1
                if (closeBracket >= 0 && closeParen >= 0) {
                    val linkText = text.substring(i + 1, closeBracket)
                    withStyle(SpanStyle(
                        color = Color(0xFF42A5F5),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )) {
                        append(linkText)
                    }
                    i = closeParen + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
