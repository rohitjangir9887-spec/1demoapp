package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraIndigo
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import kotlinx.coroutines.delay

/**
 * High-performance, streaming-safe Markdown AST renderer designed specifically for Jetpack Compose.
 *
 * Supports:
 * - Headings (#, ##, ###, ####) with theme typography
 * - Bold (** or __) and Italic (* or _)
 * - Inline code chips and multi-line fenced code blocks with language badge and working Copy button
 * - Real horizontally-scrollable Markdown tables (converts raw pipe tables into readable cards)
 * - Numbered and bulleted lists with task checklists (- [x], - [ ])
 * - Blockquotes with accent line
 * - Clickable HTTPS hyperlinks
 * - Streaming resilience (gracefully closes partial code blocks, tables, and bold tokens)
 * - Copy/Select text integration
 */

sealed interface MarkdownNode {
    data class Heading(val level: Int, val text: String) : MarkdownNode
    data class Paragraph(val text: String) : MarkdownNode
    data class CodeBlock(val language: String, val code: String) : MarkdownNode
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownNode
    data class ListItem(
        val text: String,
        val prefix: String,
        val isTask: Boolean = false,
        val isChecked: Boolean = false
    ) : MarkdownNode
    data class BlockQuote(val text: String) : MarkdownNode
    data object Divider : MarkdownNode
}

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onOpenLink: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveOpenLink: (String) -> Unit = onOpenLink ?: { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // Parse Markdown lines into structured nodes (cached per markdown string)
    val nodes = remember(markdown, isStreaming) {
        parseMarkdownToNodes(markdown, isStreaming)
    }

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            nodes.forEach { node ->
                when (node) {
                    is MarkdownNode.Heading -> RenderHeading(node)
                    is MarkdownNode.Paragraph -> RenderParagraph(node.text, effectiveOpenLink)
                    is MarkdownNode.CodeBlock -> RenderCodeBlock(node.language, node.code)
                    is MarkdownNode.Table -> RenderTable(node.headers, node.rows)
                    is MarkdownNode.ListItem -> RenderListItem(node, effectiveOpenLink)
                    is MarkdownNode.BlockQuote -> RenderBlockQuote(node.text, effectiveOpenLink)
                    is MarkdownNode.Divider -> HorizontalDivider(
                        color = ObsidianCardBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderHeading(heading: MarkdownNode.Heading) {
    val (style, color, topPad) = when (heading.level) {
        1 -> Triple(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp), PearlWhite, 8.dp)
        2 -> Triple(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), AuraCyan, 6.dp)
        3 -> Triple(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), PearlWhite, 4.dp)
        else -> Triple(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), SlateMist, 2.dp)
    }
    Text(
        text = heading.text,
        style = style,
        color = color,
        modifier = Modifier.padding(top = topPad, bottom = 2.dp)
    )
}

@Composable
private fun RenderParagraph(text: String, onOpenLink: (String) -> Unit) {
    val annotated = remember(text) { buildFormattedAnnotatedString(text) }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = PearlWhite,
            lineHeight = 23.sp
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onOpenLink(annotation.item)
                }
        }
    )
}

@Composable
private fun RenderListItem(item: MarkdownNode.ListItem, onOpenLink: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (item.isTask) {
            Icon(
                imageVector = if (item.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (item.isChecked) "Completed task" else "Incomplete task",
                tint = if (item.isChecked) AuraNimGreen else SlateMist,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Text(
                text = item.prefix,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = AuraCyan
                ),
                modifier = Modifier.padding(top = 1.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        val annotated = remember(item.text) { buildFormattedAnnotatedString(item.text) }
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = PearlWhite,
                lineHeight = 21.sp
            ),
            modifier = Modifier.weight(1f),
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onOpenLink(annotation.item)
                    }
            }
        )
    }
}

@Composable
private fun RenderBlockQuote(text: String, onOpenLink: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        color = ObsidianSurfaceElevated.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, ObsidianCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(AuraCyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            val annotated = remember(text) { buildFormattedAnnotatedString(text) }
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    color = SlateMist,
                    lineHeight = 20.sp
                ),
                onClick = { offset ->
                    annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            onOpenLink(annotation.item)
                        }
                }
            )
        }
    }
}

@Composable
private fun RenderCodeBlock(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000L)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF090D18),
        border = BorderStroke(1.dp, ObsidianCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Code block header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1524))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    ),
                    color = AuraCyan
                )

                Surface(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("code_snippet", code))
                        copied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (copied) AuraNimGreen.copy(alpha = 0.2f) else ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, if (copied) AuraNimGreen else ObsidianCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) AuraNimGreen else SlateMist,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (copied) "Copied!" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copied) AuraNimGreen else SlateMist
                        )
                    }
                }
            }

            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFE2E8F0)
                    )
                )
            }
        }
    }
}

@Composable
private fun RenderTable(headers: List<String>, rows: List<List<String>>) {
    if (headers.isEmpty() && rows.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(4.dp)
        ) {
            // Header Row
            if (headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(
                            color = AuraBlue.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.forEachIndexed { idx, header ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 220.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = header.trim(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = PearlWhite
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Data Rows
            rows.forEachIndexed { rowIndex, row ->
                val rowBackground = if (rowIndex % 2 == 0) {
                    ObsidianSurfaceElevated.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground, RoundedCornerShape(6.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.indices.forEach { colIdx ->
                        val cellText = row.getOrNull(colIdx)?.trim().orEmpty()
                        Box(
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 220.dp)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                ),
                                color = if (colIdx == 0) AuraCyan else PearlWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds an [AnnotatedString] supporting:
 * - Bold: `**text**` or `__text__`
 * - Italic: `*text*` or `_text_`
 * - Inline Code: `` `code` ``
 * - Clickable links: `[title](https://...)`
 * - Bare HTTPS URLs: `https://...`
 */
fun buildFormattedAnnotatedString(rawText: String): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val len = rawText.length

        while (idx < len) {
            when {
                // Link: [label](url)
                rawText[idx] == '[' && rawText.indexOf(']', idx) > idx -> {
                    val closeBracket = rawText.indexOf(']', idx)
                    if (closeBracket + 1 < len && rawText[closeBracket + 1] == '(') {
                        val closeParen = rawText.indexOf(')', closeBracket + 1)
                        if (closeParen > closeBracket + 1) {
                            val label = rawText.substring(idx + 1, closeBracket)
                            val url = rawText.substring(closeBracket + 2, closeParen)
                            val startAnnotation = length
                            pushStringAnnotation(tag = "URL", annotation = url)
                            pushStyle(
                                SpanStyle(
                                    color = AuraCyan,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(label)
                            pop()
                            pop()
                            idx = closeParen + 1
                            continue
                        }
                    }
                    append(rawText[idx])
                    idx++
                }

                // Inline code: `code`
                rawText[idx] == '`' -> {
                    val endCode = rawText.indexOf('`', idx + 1)
                    if (endCode > idx) {
                        val code = rawText.substring(idx + 1, endCode)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF1E293B),
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp
                            )
                        )
                        append(" $code ")
                        pop()
                        idx = endCode + 1
                    } else {
                        // Dangling backtick (streaming)
                        append(rawText[idx])
                        idx++
                    }
                }

                // Bold: **text** or __text__
                (rawText.startsWith("**", idx) || rawText.startsWith("__", idx)) -> {
                    val marker = rawText.substring(idx, idx + 2)
                    val endBold = rawText.indexOf(marker, idx + 2)
                    if (endBold > idx + 1) {
                        val boldContent = rawText.substring(idx + 2, endBold)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PearlWhite))
                        append(boldContent)
                        pop()
                        idx = endBold + 2
                    } else {
                        // Dangling bold in streaming text
                        val remainder = rawText.substring(idx + 2)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PearlWhite))
                        append(remainder)
                        pop()
                        idx = len
                    }
                }

                // Italic: *text* (excluding bold check)
                rawText[idx] == '*' && (idx + 1 < len && rawText[idx + 1] != '*') -> {
                    val endItalic = rawText.indexOf('*', idx + 1)
                    if (endItalic > idx) {
                        val italicContent = rawText.substring(idx + 1, endItalic)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(italicContent)
                        pop()
                        idx = endItalic + 1
                    } else {
                        append(rawText[idx])
                        idx++
                    }
                }

                // Raw HTTPS Link: https://...
                rawText.startsWith("https://", idx) || rawText.startsWith("http://", idx) -> {
                    val spaceIdx = rawText.indexOfAny(charArrayOf(' ', '\n', '\t', ')', ']'), idx)
                    val url = if (spaceIdx != -1) rawText.substring(idx, spaceIdx) else rawText.substring(idx)
                    val cleanUrl = url.trimEnd('.', ',', ';')
                    val startAnnotation = length
                    pushStringAnnotation(tag = "URL", annotation = cleanUrl)
                    pushStyle(
                        SpanStyle(
                            color = AuraCyan,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(cleanUrl)
                    pop()
                    pop()
                    idx += cleanUrl.length
                }

                else -> {
                    append(rawText[idx])
                    idx++
                }
            }
        }
    }
}

/**
 * Parses raw Markdown text into structured [MarkdownNode] items.
 * Handles multiline code fences, tables, headers, blockquotes, lists, and paragraphs.
 */
fun parseMarkdownToNodes(rawText: String, isStreaming: Boolean): List<MarkdownNode> {
    if (rawText.isBlank()) return emptyList()

    val nodes = mutableListOf<MarkdownNode>()
    val lines = rawText.lines()
    var lineIdx = 0
    val totalLines = lines.size

    while (lineIdx < totalLines) {
        val line = lines[lineIdx]
        val trimmed = line.trim()

        // 1. Code Block Fence (```lang or ~~~)
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val language = trimmed.removePrefix("```").removePrefix("~~~").trim()
            val codeLines = mutableListOf<String>()
            lineIdx++
            var closed = false
            while (lineIdx < totalLines) {
                val cl = lines[lineIdx]
                if (cl.trim().startsWith("```") || cl.trim().startsWith("~~~")) {
                    closed = true
                    lineIdx++
                    break
                }
                codeLines.add(cl)
                lineIdx++
            }
            nodes.add(MarkdownNode.CodeBlock(language = language, code = codeLines.joinToString("\n")))
            continue
        }

        // 2. Horizontal Divider (--- or ***)
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            nodes.add(MarkdownNode.Divider)
            lineIdx++
            continue
        }

        // 3. Headings (#, ##, ###, ####)
        if (trimmed.startsWith("#")) {
            val hashCount = trimmed.takeWhile { it == '#' }.length
            if (hashCount in 1..6 && trimmed.length > hashCount && trimmed[hashCount] == ' ') {
                val headingText = trimmed.substring(hashCount + 1).trim()
                nodes.add(MarkdownNode.Heading(level = hashCount, text = headingText))
                lineIdx++
                continue
            }
        }

        // 4. Blockquote (> ...)
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (lineIdx < totalLines && lines[lineIdx].trim().startsWith(">")) {
                quoteLines.add(lines[lineIdx].trim().removePrefix(">").trim())
                lineIdx++
            }
            nodes.add(MarkdownNode.BlockQuote(text = quoteLines.joinToString(" ")))
            continue
        }

        // 5. Table (| Header 1 | Header 2 |)
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && lineIdx + 1 < totalLines) {
            val nextLine = lines[lineIdx + 1].trim()
            if (nextLine.startsWith("|") && nextLine.contains("---")) {
                // Confirmed Markdown table
                val headerCells = trimmed.split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                lineIdx += 2 // skip header & divider row

                val rows = mutableListOf<List<String>>()
                while (lineIdx < totalLines) {
                    val rowLine = lines[lineIdx].trim()
                    if (!rowLine.startsWith("|") || !rowLine.endsWith("|")) break
                    val cells = rowLine.split("|")
                        .map { it.trim() }
                        .filterIndexed { index, _ -> index > 0 && index <= headerCells.size }
                    rows.add(cells)
                    lineIdx++
                }
                nodes.add(MarkdownNode.Table(headers = headerCells, rows = rows))
                continue
            }
        }

        // 6. List Item (bullet, numbered, or checklist)
        val isTask = trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") ||
                trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ")
        val isBullet = !isTask && (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• "))
        val isNumbered = !isTask && !isBullet && Regex("^\\d+\\.\\s+").containsMatchIn(trimmed)

        if (isTask) {
            val isChecked = trimmed.startsWith("- [x] ") || trimmed.startsWith("* [x] ")
            val content = trimmed.substring(6).trim()
            nodes.add(MarkdownNode.ListItem(text = content, prefix = "", isTask = true, isChecked = isChecked))
            lineIdx++
            continue
        }

        if (isBullet) {
            val content = trimmed.substring(2).trim()
            nodes.add(MarkdownNode.ListItem(text = content, prefix = "•", isTask = false))
            lineIdx++
            continue
        }

        if (isNumbered) {
            val dotIdx = trimmed.indexOf('.')
            val numPrefix = trimmed.substring(0, dotIdx + 1)
            val content = trimmed.substring(dotIdx + 1).trim()
            nodes.add(MarkdownNode.ListItem(text = content, prefix = numPrefix, isTask = false))
            lineIdx++
            continue
        }

        // 7. Regular Paragraph Text (accumulate contiguous non-empty lines)
        if (trimmed.isNotEmpty()) {
            val pLines = mutableListOf<String>()
            while (lineIdx < totalLines) {
                val cur = lines[lineIdx].trim()
                if (cur.isEmpty() ||
                    cur.startsWith("#") ||
                    cur.startsWith("```") ||
                    cur.startsWith(">") ||
                    (cur.startsWith("|") && cur.endsWith("|")) ||
                    cur.startsWith("- ") ||
                    cur.startsWith("* ") ||
                    cur == "---" ||
                    Regex("^\\d+\\.\\s+").containsMatchIn(cur)
                ) {
                    break
                }
                pLines.add(lines[lineIdx])
                lineIdx++
            }
            if (pLines.isNotEmpty()) {
                nodes.add(MarkdownNode.Paragraph(text = pLines.joinToString("\n")))
            }
            continue
        }

        lineIdx++
    }

    return nodes
}
