package com.banksms.expensetracker.ui.screens.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** Theme-derived colors the parser needs (kept out of the pure parser). */
data class MdPalette(
    val text: Color,
    val accent: Color,
    val secondary: Color,
    val codeBackground: Color
)

/**
 * Minimal Markdown subset the assistant is instructed to emit: ## headings,
 * **bold**, *italic*, `code`, - bullets, 1. numbered lists, > quotes.
 * Dependency-free and style-matched to the app.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val palette = MdPalette(
        text = color,
        accent = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val annotated = remember(markdown, palette) { parseMarkdown(markdown, palette) }
    Text(text = annotated, style = style, color = color, modifier = modifier)
}

private val INLINE_TOKEN =
    Regex("""(\*\*.+?\*\*|~~.+?~~|`[^`\n]+?`|\*[^*\n]+?\*)""")
private val NUMBERED_LINE = Regex("""^(\d+)\.\s+(.*)$""")

/** Pure line/block parser — fully unit-testable. */
fun parseMarkdown(markdown: String, palette: MdPalette): AnnotatedString {
    // Placeholder for the still-typing state.
    if (markdown == "…") {
        return buildAnnotatedString { append("…") }
    }
    return buildAnnotatedString {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        lines.forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) {
                append("\n")
                return@forEach
            }
            when {
                line.startsWith("### ") -> appendInline(line.removePrefix("### "), heading(17), palette)
                line.startsWith("## ") -> appendInline(line.removePrefix("## "), heading(18), palette)
                line.startsWith("# ") -> appendInline(line.removePrefix("# "), heading(19), palette)
                line.startsWith("> ") -> appendInline(
                    line.removePrefix("> "),
                    SpanStyle(fontStyle = FontStyle.Italic, color = palette.secondary),
                    palette
                )
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.accent))
                    append("• ")
                    pop()
                    appendInline(line.substring(2), null, palette)
                }
                NUMBERED_LINE.matches(line) -> {
                    val m = NUMBERED_LINE.find(line)!!
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.accent))
                    append("${m.groupValues[1]}. ")
                    pop()
                    appendInline(m.groupValues[2], null, palette)
                }
                else -> appendInline(line, null, palette)
            }
            append("\n")
        }
    }
}

private fun heading(sizeSp: Int) = SpanStyle(
    fontSize = sizeSp.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.2).sp
)

private fun AnnotatedString.Builder.appendInline(
    text: String,
    blockStyle: SpanStyle?,
    palette: MdPalette
) {
    var last = 0
    INLINE_TOKEN.findAll(text).forEach { match ->
        if (match.range.first > last) {
            appendStyled(text.substring(last, match.range.first), blockStyle)
        }
        val token = match.value
        when {
            token.startsWith("**") -> appendStyled(
                token.removePrefix("**").removeSuffix("**"),
                merge(blockStyle, SpanStyle(fontWeight = FontWeight.Bold))
            )
            token.startsWith("~~") -> appendStyled(
                token.removePrefix("~~").removeSuffix("~~"),
                merge(blockStyle, SpanStyle(textDecoration = TextDecoration.LineThrough))
            )
            token.startsWith("`") -> appendStyled(
                token.removePrefix("`").removeSuffix("`"),
                merge(
                    blockStyle,
                    SpanStyle(fontFamily = FontFamily.Monospace, background = palette.codeBackground)
                )
            )
            else -> appendStyled(
                token.removePrefix("*").removeSuffix("*"),
                merge(blockStyle, SpanStyle(fontStyle = FontStyle.Italic))
            )
        }
        last = match.range.last + 1
    }
    if (last < text.length) {
        appendStyled(text.substring(last), blockStyle)
    }
}

private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle?) {
    if (style == null) append(text)
    else {
        pushStyle(style)
        append(text)
        pop()
    }
}

private fun merge(base: SpanStyle?, extra: SpanStyle): SpanStyle = base?.merge(extra) ?: extra
