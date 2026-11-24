/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.scripts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.CountChip
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.SearchField
import com.mtwabpin.kolahal.ui.rememberExpandedSheetState

/*
 * The controls above the script cards in the Scripts tab: search, Filters and Sort, the chips that
 * are on (and the sort, when it isn't Name), the note about scripts Kolahal updated, the banner about
 * copies that differ, and the count.
 */

/**
 * The search field, with Filters and Sort beside it.
 * - Filters opens the filter sheet. Its badge counts the chips that are on.
 * - Sort opens a menu: Name, Last run, Recently changed.
 */
@Composable
internal fun SearchRow(
    query: String,
    onQuery: (String) -> Unit,
    filters: Int,
    onFilters: () -> Unit,
    sort: ScriptSort,
    onSort: (ScriptSort) -> Unit
) {
    var sorting by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SearchField(
            query,
            onQuery,
            "Search scripts",
            Modifier
                .weight(1f)
                .testTag("script-search"),
            clear = "Clear the search"
        )
        Labeled("Filters") {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onFilters) {
                BadgedBox(badge = {
                    if (filters > 0) Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) { Text("$filters") }
                }) {
                    Icon(painterResource(R.drawable.ic_tune), contentDescription = "Filters")
                }
            }
        }
        Box {
            LabeledIconButton(R.drawable.ic_sort, "Sort", onClick = { sorting = true })
            DropdownMenuPopup(expanded = sorting, onDismissRequest = { sorting = false }) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                    // The theme's accent, as in IntervalSelector.
                    val colors = MenuDefaults.selectableItemColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    ScriptSort.entries.forEachIndexed { i, s ->
                        SelectableDropdownMenuItem(
                            selected = s == sort,
                            onClick = { sorting = false; onSort(s) },
                            text = { Text(s.label) },
                            shapes = MenuDefaults.itemShape(i, ScriptSort.entries.size),
                            colors = colors,
                        )
                    }
                }
            }
        }
    }
}

/** A chip's name under the search field. [version] names the Kolahal that updated scripts by itself. */
private fun chosenLabel(chip: ScriptFilter, version: String?): String = when (chip.group) {
    FilterGroup.USES -> chip.id.removePrefix("uses:").let { "Uses " + (USES_SHORT[it] ?: it) }
    FilterGroup.SELF_UPDATED -> if (version != null) "Updated in $version" else chip.label
    else -> chip.label
}

/**
 * The chips that are on, in the sheet's order. × on a chip turns it off; Clear turns all of them off.
 * - A sort other than Name comes first, as "Sorted by last run" with ×: × puts the list back by
 *   name ([onDefaultSort]). Clear leaves the sort as it is, and shows only with a filter on.
 */
@Composable
internal fun ChosenFilters(
    ids: Set<String>,
    version: String?,
    sort: ScriptSort,
    onDefaultSort: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("chosen-filters"),
    ) {
        if (sort != ScriptSort.NAME) {
            ChosenChip(
                sortedBy(sort),
                onDefaultSort,
                Modifier.testTag("chosen-sort")
            )
        }
        for (chip in ScriptFilters.ALL.filter { it.id in ids }) {
            ChosenChip(chosenLabel(chip, version), { onRemove(chip.id) })
        }
        if (ids.isNotEmpty()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onClear) { Text("Clear") }
        }
    }
}

/** "Sorted by last run": what the list is sorted by, in the count line or on its chip. */
private fun sortedBy(sort: ScriptSort) = "Sorted by ${sort.label.lowercase()}"

/** One chip under the search field, with ×. A tap anywhere on it calls [onRemove]. */
@Composable
private fun ChosenChip(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        // The Expressive shapes. These colors are the ones the chip had before it took shapes.
        shapes = InputChipDefaults.shapes(),
        modifier = modifier,
        colors = InputChipDefaults.inputChipColors(),
        trailingIcon = {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Remove",
                Modifier.size(InputChipDefaults.IconSize)
            )
        },
    )
}

/**
 * One line after an app update: "Kolahal 0.5.1 updated 3 scripts". These were library copies
 * nobody edited. Show filters the list to them; × closes the note.
 */
@Composable
internal fun SelfUpdateNote(version: String, count: Int, onShow: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("self-update-note"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_info),
                null,
                Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Kolahal $version updated $count ${if (count == 1) "script" else "scripts"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onShow) { Text("Show") }
            LabeledIconButton(R.drawable.ic_close, "Close the note", onClick = onClose)
        }
    }
}

/**
 * The banner when library or shared copies differ from where they came from: how many, with new
 * versions and edited ones counted. Review filters the list to them. Update all opens a list to tick.
 * × hides it until another copy differs.
 */
@Composable
internal fun CopiesBanner(
    newer: Int,
    edited: Int,
    canUpdate: Boolean,
    onReview: () -> Unit,
    onUpdateAll: () -> Unit,
    onHide: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val n = newer + edited
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("copies-banner")
    ) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_refresh), null, tint = colors.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (n == 1) "1 script differs from the library" else "$n scripts differ from the library",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        listOfNotNull(
                            newer.takeIf { it > 0 }
                                ?.let { if (it == 1) "1 new version" else "$it new versions" },
                            edited.takeIf { it > 0 }?.let { "$it edited" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                LabeledIconButton(R.drawable.ic_close, "Hide the banner", onClick = onHide)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp)
            ) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onReview) { Text("Review") }
                if (canUpdate) FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onUpdateAll
                ) { Text("Update all…") }
            }
        }
    }
}

/** "4 of 11 scripts" (or "11 scripts" when all show), and the sort. A null [sort] is left out: its chip shows it. */
@Composable
internal fun CountLine(shown: Int, total: Int, sort: ScriptSort?) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            if (shown == total) "$total ${if (total == 1) "script" else "scripts"}" else "$shown of $total scripts",
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (sort != null) Text(sortedBy(sort), style = style, color = color)
    }
}

/**
 * The filter sheet: the chips by group, each with the number of scripts it would show
 * ([ScriptFilters.counts]). A chip with 0 is grayed, but a tap still works. Show N scripts, a tap
 * outside or Back closes it, and the chips stay as chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    groups: List<Pair<FilterGroup, List<ScriptFilter>>>,
    counts: Map<String, Int>,
    chosen: Set<String>,
    shown: Int,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
        containerColor = colors.surfaceContainerLow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp)
        ) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onClear,
                enabled = chosen.isNotEmpty()
            ) { Text("Clear all") }
        }
        Text(
            "In a group: any of the chips. Across groups: all of them.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        FilterGroups(groups, counts, chosen, onToggle)
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        Button(
            shapes = ButtonDefaults.shapes(),
            onClick = onDismiss,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
                .fillMaxWidth(),
        ) { Text(if (shown == 1) "Show 1 script" else "Show $shown scripts") }
    }
}

/** The groups of the filter sheet. They scroll when they are taller than the screen. */
@Composable
private fun ColumnScope.FilterGroups(
    groups: List<Pair<FilterGroup, List<ScriptFilter>>>,
    counts: Map<String, Int>,
    chosen: Set<String>,
    onToggle: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((group, chips) in groups) {
            Text(
                group.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (chip in chips) {
                    // A chip at 0 is grayed, as nothing more would show. It still works: in a group it may add nothing yet keep the others.
                    CountChip(
                        chip.label,
                        counts[chip.id] ?: 0,
                        on = chip.id in chosen,
                        modifier = Modifier
                            .height(FilterChipDefaults.Height)
                            .testTag("filter:${chip.id}"),
                    ) { onToggle(chip.id) }
                }
            }
        }
    }
}
