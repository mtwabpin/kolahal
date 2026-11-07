package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R

private const val SAMPLE_PROMPT = "$ ls ~"
private const val SAMPLE_GLYPHS = "0Oo1lI {}=>"

/**
 * A horizontal row of font previews in the terminal colours.
 * - The last card imports a font file.
 * - The custom card shows after a font is imported (or while `font = custom`).
 */
@Composable
fun FontChoices(selected: String, onPick: (String) -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val p = Term.palette
    // Read again when the selection changes. An import changes the selection.
    val custom = remember(selected, TermFonts.revision) { TermFonts.customFamily(context) }
    val names = TermFonts.names.filter { it != TermFonts.CUSTOM || custom != null || selected == TermFonts.CUSTOM }
    // A plain scrolling row, not a lazy one. A lazy row inside a Settings row crashed Compose when
    // Settings measured that row early while scrolling ("LayoutNode … not found in RectList").
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        names.forEach { name ->
            val family = if (name == TermFonts.CUSTOM) custom ?: FontFamily.Monospace else TermFonts.family(context, name) ?: FontFamily.Monospace
            val on = name == selected
            Card(
                onClick = { onPick(name) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = p.bg),
                border = if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, p.surface),
                modifier = Modifier.width(148.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    val sample = MaterialTheme.typography.bodyMedium.copy(fontFamily = family)
                    Text(SAMPLE_PROMPT, color = p.prompt, style = sample.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(SAMPLE_GLYPHS, color = p.fg, style = sample, maxLines = 1, overflow = TextOverflow.Clip)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (name == TermFonts.CUSTOM && custom == null) "Custom (missing)" else TermFonts.label(name),
                        color = if (name == TermFonts.CUSTOM && custom == null) p.error else p.dim,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        run {
            Card(
                onClick = onImport,
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = p.surface),
                border = BorderStroke(1.dp, p.surface),
                modifier = Modifier.width(148.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Icon(painterResource(R.drawable.ic_upload), null, tint = p.prompt, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Import .ttf / .otf…", color = p.fg, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                }
            }
        }
    }
}
