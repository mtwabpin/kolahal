package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.terminal.ActionIcon
import com.mtwabpin.kolahal.terminal.LogAction

/**
 * A long-press menu. Its actions are in the system's font, as a menu; its [title] (the line pressed)
 * in the terminal's, as in the log, unless [systemFont] (quick actions, whose title is a heading).
 * [url]: the line's link, shown under the title so you see where Open link goes.
 */
data class ActionMenu(
    val title: String,
    val actions: List<LogAction>,
    val entryId: Long?,
    val systemFont: Boolean = false,
    val url: String? = null,
    /** Where the line came from and when, to the second ([com.mtwabpin.kolahal.terminal.lineDetail]). */
    val detail: String? = null,
)

/**
 * A bottom sheet of the terminal's: its surface colour, rounded only at the top, no drag handle.
 * It opens all the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TermSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
        containerColor = Term.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        content = content,
    )
}

/** Long-press actions as a compact bottom sheet: the line on top, destructive actions last in red. */
@Composable
fun ActionSheet(menu: ActionMenu, onDismiss: () -> Unit) {
    TermSheet(onDismiss) {
        // Scrolls when there are more actions than fit, so the last one can be reached.
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(vertical = 8.dp)) {
            // Sized with the terminal's text, like the icons.
            val menuStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (Term.fontSize + 2).sp,
                lineHeight = ((Term.fontSize + 2) * 1.4f).sp,
            )
            Text(
                menu.title,
                style = (if (menu.systemFont) menuStyle else Term.text).copy(color = Term.dim),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Where the line came from and when: what the log may leave off the line.
            menu.detail?.let { detail ->
                Text(
                    detail,
                    style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("menu-detail"),
                )
            }
            menu.url?.let { url ->
                Text(
                    url,
                    style = Term.text.copy(color = Term.link, fontSize = (Term.fontSize - 1).sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            var dividerShown = false
            // The header's icon size; the column is as wide as its battery, so labels line up.
            val iconSize = (Term.fontSize + 3).dp
            val iconColumn = menu.actions.any { it.icon != null }
            menu.actions.forEach { action ->
                if (action.destructive && !dividerShown) {
                    HorizontalDivider(color = Term.dim.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    dividerShown = true
                }
                val color = if (action.destructive) Term.error else Term.fg
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            onDismiss()
                            action.run()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (iconColumn) {
                        Box(Modifier.width(iconSize * 1.6f), contentAlignment = Alignment.Center) {
                            when (val icon = action.icon) {
                                is ActionIcon.Drawable -> Image(
                                    painter = painterResource(icon.id),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        when {
                                            icon.alert -> Term.error
                                            icon.profile != null -> Term.profileColor(icon.profile)
                                            else -> Term.dim
                                        },
                                    ),
                                    modifier = Modifier.size(iconSize),
                                )
                                is ActionIcon.Battery -> BatteryGlyph(
                                    icon.pct,
                                    icon.charging,
                                    when {
                                        icon.charging -> Term.prompt
                                        icon.pct <= 15 -> Term.error
                                        else -> Term.dim
                                    },
                                    iconSize,
                                )
                                null -> Unit
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(action.label, style = menuStyle.copy(color = color))
                }
            }
        }
    }
}
