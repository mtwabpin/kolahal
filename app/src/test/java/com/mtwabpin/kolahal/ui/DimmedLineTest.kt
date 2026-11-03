package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which dimmed log lines fade part by part: only those that look the same as faded whole. */
class DimmedLineTest {
    @Test fun plainLatinTextFadesByPart() {
        assertTrue(fadesByPart(AnnotatedString("$ help · Open Kolahal's source code on GitHub")))
        assertTrue(fadesByPart(AnnotatedString("Café, naïve — “quoted” • 12:30\nsecond line")))
        // Colours and bold change nothing about overlap.
        assertTrue(fadesByPart(buildAnnotatedString { withStyle(SpanStyle(color = Color.Red)) { append("ok") } }))
    }

    @Test fun joinedScriptsMarksAndBoxDrawingFadeWhole() {
        // Letters that join or overlap would show seams faded one by one.
        assertFalse(fadesByPart(AnnotatedString("नमस्ते")))
        assertFalse(fadesByPart(AnnotatedString("مرحبا")))
        assertFalse(fadesByPart(AnnotatedString("é")))
        assertFalse(fadesByPart(AnnotatedString("── Yesterday ──")))
        assertFalse(fadesByPart(AnnotatedString("↑/↓ recall commands")))
        assertFalse(fadesByPart(AnnotatedString("weather 🌧")))
    }

    @Test fun anythingDrawnUnderOrOverTheLettersFadesWhole() {
        assertFalse(fadesByPart(buildAnnotatedString { withStyle(SpanStyle(background = Color.Yellow)) { append("found") } }))
        assertFalse(fadesByPart(buildAnnotatedString { withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("link") } }))
        // Slanted letters can lean into the next one.
        assertFalse(fadesByPart(buildAnnotatedString { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("aside") } }))
    }
}
