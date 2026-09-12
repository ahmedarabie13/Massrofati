package com.banksms.expensetracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.banksms.expensetracker.ui.screens.chat.MdPalette
import com.banksms.expensetracker.ui.screens.chat.parseMarkdown
import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {

    private val palette = MdPalette(
        text = Color.Black,
        accent = Color.Blue,
        secondary = Color.Gray,
        codeBackground = Color.LightGray
    )

    private fun hasSpan(
        text: androidx.compose.ui.text.AnnotatedString,
        sample: String,
        weight: FontWeight? = null,
        style: FontStyle? = null
    ): Boolean {
        val plain = text.text
        val start = plain.indexOf(sample)
        assertTrue("sample '$sample' missing in '$plain'", start >= 0)
        return text.spanStyles.any { span ->
            span.start <= start && span.end >= start + sample.length &&
                (weight == null || span.item.fontWeight == weight) &&
                (style == null || span.item.fontStyle == style)
        }
    }

    @Test
    fun boldAndPlain() {
        val out = parseMarkdown("spent **1,768.27 SAR** total", palette)
        assertEquals("spent 1,768.27 SAR total\n", out.text)
        assertTrue(hasSpan(out, "1,768.27 SAR", weight = FontWeight.Bold))
    }

    @Test
    fun headingStripsMarkers() {
        val out = parseMarkdown("## September spending", palette)
        assertEquals("September spending\n", out.text)
        assertTrue(hasSpan(out, "September spending", weight = FontWeight.ExtraBold))
    }

    @Test
    fun bulletsUseDotMarker() {
        val out = parseMarkdown("- 11 Sep — **3.00 SAR**\n- 04 Sep — 1.00 SAR", palette)
        assertTrue(out.text.startsWith("• 11 Sep"))
        assertTrue(out.text.contains("• 04 Sep"))
        assertTrue(hasSpan(out, "3.00 SAR", weight = FontWeight.Bold))
    }

    @Test
    fun numberedKeepsNumbers() {
        val out = parseMarkdown("1. first\n2. second", palette)
        assertEquals("1. first\n2. second\n", out.text)
    }

    @Test
    fun italicAndCode() {
        val out = parseMarkdown("say *hi* and `code`", palette)
        assertEquals("say hi and code\n", out.text)
        assertTrue(hasSpan(out, "hi", style = FontStyle.Italic))
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        val out = parseMarkdown("a ** broken line", palette)
        assertEquals("a ** broken line\n", out.text)
    }
}
