package com.rg.quarkcode.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// Dependency-free rich text for chat: block parser + renderer tuned for
// streaming (an unclosed fence renders as-is, no flicker, no re-guessing).
// Callers wrap in runCatching and fall back to plain Text on failure.
private sealed interface RichBlock {
    data class Heading(val level: Int, val text: String) : RichBlock
    data class Para(val text: String) : RichBlock
    data class Code(val lang: String, val code: String) : RichBlock
    data class Quote(val text: String) : RichBlock
    data class Bullets(val items: List<String>) : RichBlock
    data class Numbered(val start: Int, val items: List<String>) : RichBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RichBlock
    data object Hr : RichBlock
}

private val bulletPattern = Regex("""^([-*+])\s+(.+)$""")
private val orderedPattern = Regex("""^(\d+)[.)]\s+(.+)$""")
private val tableSepPattern = Regex("""^\|?[\s:|-]+\|?$""")

private fun parseRichText(src: String): List<RichBlock> {
    val out = mutableListOf<RichBlock>()
    val lines = src.split("\n")
    val para = StringBuilder()
    var i = 0
    fun flushPara() {
        if (para.isNotBlank()) out.add(RichBlock.Para(para.toString().trim()))
        para.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flushPara()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trimStart().startsWith("```")) {
                        closed = true
                        break
                    }
                    code.appendLine(lines[i])
                    i++
                }
                // Render even when unclosed (streaming) — never blank the reply.
                out.add(RichBlock.Code(lang.ifBlank { "code" }, code.toString().trimEnd()))
                if (!closed) {
                    i = lines.size
                    continue
                }
            }
            t.length >= 2 && t[0] == '#' -> {
                val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                if (level < t.length && t[level] == ' ') {
                    flushPara()
                    out.add(RichBlock.Heading(level, t.drop(level + 1)))
                } else {
                    para.appendLine(line)
                }
            }
            t.length >= 3 && (t.all { it == '-' } || t.all { it == '*' } || t.all { it == '_' }) -> {
                flushPara()
                out.add(RichBlock.Hr)
            }
            t.startsWith("> ") || t == ">" -> {
                flushPara()
                val quote = StringBuilder()
                while (i < lines.size) {
                    val q = lines[i].trim()
                    if (!(q.startsWith("> ") || q == ">")) break
                    quote.appendLine(if (q == ">") "" else q.removePrefix("> "))
                    i++
                }
                out.add(RichBlock.Quote(quote.toString().trim()))
                continue
            }
            bulletPattern.matches(t) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = bulletPattern.matchEntire(lines[i].trim()) ?: break
                    items.add(m.groupValues[2])
                    i++
                }
                out.add(RichBlock.Bullets(items))
                continue
            }
            orderedPattern.matches(t) -> {
                flushPara()
                val items = mutableListOf<String>()
                var start = 1
                var first = true
                while (i < lines.size) {
                    val m = orderedPattern.matchEntire(lines[i].trim()) ?: break
                    if (first) {
                        start = m.groupValues[1].toIntOrNull() ?: 1
                        first = false
                    }
                    items.add(m.groupValues[2])
                    i++
                }
                out.add(RichBlock.Numbered(start, items))
                continue
            }
            t.contains('|') && i + 1 < lines.size &&
                tableSepPattern.matches(lines[i + 1].trim()) &&
                lines[i + 1].contains('-') -> {
                flushPara()
                val headers = t.trim('|').split('|').map { it.trim() }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows.add(lines[i].trim('|').split('|').map { it.trim() })
                    i++
                }
                out.add(RichBlock.Table(headers, rows))
                continue
            }
            t.isBlank() -> flushPara()
            else -> para.appendLine(line)
        }
        i++
    }
    flushPara()
    return out.ifEmpty { listOf(RichBlock.Para(src)) }
}

private val inlinePattern = Regex("""(`(.+?)`)|(\*\*(.+?)\*\*)|(~~(.+?)~~)|(\[([^\]]+)\]\(([^)]+)\))|(\*([^*\n]+?)\*)""")
private val bareUrlPattern = Regex("""https?://[^\s)>\]]+""")

private fun renderRichInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var rest = text
        while (rest.isNotEmpty()) {
            val m = inlinePattern.find(rest) ?: break
            appendLinkified(rest.substring(0, m.range.first))
            val code = m.groups[2]?.value
            val bold = m.groups[4]?.value
            val strike = m.groups[6]?.value
            val linkText = m.groups[8]?.value
            val linkHref = m.groups[9]?.value
            val italic = m.groups[11]?.value
            when {
                code != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace)
                ) { append(code) }
                bold != null -> withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold)
                ) { append(bold) }
                strike != null -> withStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough)
                ) { append(strike) }
                linkText != null -> {
                    appendLink(linkText, linkHref.orEmpty())
                }
                italic != null -> withStyle(
                    SpanStyle(fontStyle = FontStyle.Italic)
                ) { append(italic) }
            }
            rest = rest.substring(m.range.last + 1)
        }
        appendLinkified(rest)
    }
}

private fun AnnotatedString.Builder.appendLink(text: String, href: String) {
    val url = href.trim()
    if (url.isEmpty()) {
        append(text)
        return
    }
    pushStringAnnotation("url", url)
    withStyle(
        SpanStyle(
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        )
    ) { append(text) }
    pop()
}

/** Auto-links bare https:// URLs inside plain runs (trailing punctuation trimmed). */
private fun AnnotatedString.Builder.appendLinkified(text: String) {
    var rest = text
    while (rest.isNotEmpty()) {
        val m = bareUrlPattern.find(rest) ?: break
        append(rest.substring(0, m.range.first))
        var url = m.value
        while (url.isNotEmpty() && url.last() in ".,;:!?") url = url.dropLast(1)
        val trailing = m.value.substring(url.length)
        if (url.isNotEmpty()) appendLink(url, url)
        append(trailing)
        rest = rest.substring(m.range.last + 1)
    }
    append(rest)
}

// Inline text that makes links tappable. Blocks WITHOUT links keep plain
// Text (so selection still works); blocks WITH links use ClickableText and
// open the URL via the system handler. Previously the "url" annotations were
// written but never consumed, so links looked underlined yet did nothing.
@Composable
private fun RichInlineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotated = remember(text) {
        runCatching { renderRichInline(text) }.getOrElse { AnnotatedString(text) }
    }
    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations("url", 0, annotated.length).isNotEmpty()
    }
    if (hasLinks) {
        val uriHandler = LocalUriHandler.current
        ClickableText(
            text = annotated,
            style = style.copy(color = color),
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier.semantics {
                contentDescription = "Text with links"
            },
            onClick = { offset ->
                annotated.getStringAnnotations("url", offset, offset)
                    .firstOrNull()?.let { ann ->
                        runCatching { uriHandler.openUri(ann.item) }
                    }
            }
        )
    } else {
        Text(
            text = annotated,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    }
}

@Composable
fun RichTextDocument(
    text: String,
    textScale: Float = 1f,
    streaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) {
        runCatching { parseRichText(text) }.getOrElse {
            listOf(RichBlock.Para(text))
        }
    }
    val body = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
    )
    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is RichBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }.copy(
                            fontSize = when (block.level) {
                                1 -> MaterialTheme.typography.titleLarge.fontSize
                                2 -> MaterialTheme.typography.titleMedium.fontSize
                                else -> MaterialTheme.typography.titleSmall.fontSize
                            } * textScale
                        )
                        RichInlineText(
                            text = block.text,
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is RichBlock.Para -> RichInlineText(
                        text = block.text,
                        style = body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    is RichBlock.Code -> RichCodeBlock(
                        lang = block.lang,
                        code = block.code,
                        textScale = textScale
                    )
                    is RichBlock.Quote -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        VerticalDivider(
                            thickness = 3.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxHeight()
                        )
                        RichInlineText(
                            text = block.text,
                            style = body.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                    }
                    is RichBlock.Bullets -> Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        block.items.forEach { item ->
                            Row {
                                Text(
                                    text = "•",
                                    style = body,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.widthIn(min = 16.dp)
                                )
                                RichInlineText(
                                    text = item,
                                    style = body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    is RichBlock.Numbered -> Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        block.items.forEachIndexed { index, item ->
                            Row {
                                Text(
                                    text = "${block.start + index}.",
                                    style = body,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.widthIn(min = 24.dp)
                                )
                                RichInlineText(
                                    text = item,
                                    style = body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    is RichBlock.Table -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            block.headers.forEach { cell ->
                                RichInlineText(
                                    text = cell,
                                    style = body.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                        block.rows.take(12).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                repeat(block.headers.size) { index ->
                                    RichInlineText(
                                        text = row.getOrNull(index).orEmpty(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize * textScale
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                    is RichBlock.Hr -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            if (streaming) {
                StreamingCaret(style = body)
            }
        }
    }
}

@Composable
private fun StreamingCaret(
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )
    Text(
        text = "▍",
        style = style,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = modifier
    )
}

@Composable
private fun RichCodeBlock(
    lang: String,
    code: String,
    textScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * textScale
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
