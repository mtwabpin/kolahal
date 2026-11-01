package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** What turns one header item on in each stage: the full header, and the one-line header. */
internal class HeaderItem(val full: ConfigKey.Bool, val oneLine: ConfigKey.Bool)

internal val HEADER_ITEMS = listOf(
    HeaderItem(Keys.HEADER_CLOCK, Keys.COMPACT_CLOCK),
    HeaderItem(Keys.HEADER_DATE, Keys.COMPACT_DATE),
    HeaderItem(Keys.HEADER_DEVICE, Keys.COMPACT_DEVICE),
    HeaderItem(Keys.HEADER_UNLOCKS, Keys.COMPACT_UNLOCKS),
    HeaderItem(Keys.HEADER_BATTERY, Keys.COMPACT_BATTERY),
    HeaderItem(Keys.HEADER_NETWORK, Keys.COMPACT_NETWORK),
    HeaderItem(Keys.HEADER_ALARM, Keys.COMPACT_ALARM),
    HeaderItem(Keys.HEADER_MEDIA, Keys.COMPACT_MEDIA),
    HeaderItem(Keys.HEADER_TODO, Keys.COMPACT_TODO),
    HeaderItem(Keys.HEADER_STATUS, Keys.COMPACT_STATUS),
    HeaderItem(Keys.HEADER_SETTINGS, Keys.COMPACT_SETTINGS),
    HeaderItem(Keys.HEADER_PROFILES, Keys.COMPACT_PROFILES),
)

/** The settings the table shows, which Settings' list leaves to it. */
internal val HEADER_ITEM_KEYS: List<ConfigKey<*>> = HEADER_ITEMS.flatMap { listOf(it.full, it.oneLine) }

/** Wide enough for the longer heading, so both columns line up with their checkboxes. */
private val COLUMN = 64.dp

/** Each header item with a checkbox per stage, one tap each. */
@Composable
internal fun HeaderItemsTable(vm: TerminalViewModel, config: Config, version: State<Int>) {
    Column {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 4.dp),
        ) {
            for (stage in listOf("Full", "One line")) {
                Text(
                    stage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(COLUMN),
                )
            }
        }
        HEADER_ITEMS.forEachIndexed { index, item ->
            val title = TITLES[item.full.key] ?: item.full.key
            Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
                SegmentedListItem(
                    colors = segColors(),
                    shapes = segShapes(index, HEADER_ITEMS.size),
                    trailingContent = {
                        Row {
                            StageCheckbox(vm, config, item.full, version, "$title, full header")
                            StageCheckbox(vm, config, item.oneLine, version, "$title, one-line header")
                        }
                    },
                ) { Text(title) }
            }
        }
    }
}

@Composable
private fun StageCheckbox(vm: TerminalViewModel, config: Config, key: ConfigKey.Bool, version: State<Int>, description: String) {
    // Only this box recomposes when its own value changes.
    val on by remember(key) { derivedStateOf { version.value; config[key] } }
    Box(Modifier.width(COLUMN), contentAlignment = Alignment.Center) {
        Checkbox(
            checked = on,
            onCheckedChange = { vm.setConfig(key, if (it) "on" else "off") },
            modifier = Modifier.testTag(key.key).semantics { contentDescription = description },
        )
    }
}
