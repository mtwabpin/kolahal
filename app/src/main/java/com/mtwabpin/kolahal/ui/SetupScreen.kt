package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * Setup checklist (`setup`, or Settings → Setup checklist). Each unchecked item opens
 * the right system page; the list refreshes when the user comes back.
 */
@Composable
fun SetupScreen(vm: TerminalViewModel) {
    val checks by vm.setupChecks.collectAsStateWithLifecycle()
    BackHandler { vm.closeScreen() }

    GuiTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val done = checks.count { it.ok || it.fix == null }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("Setup") },
                    subtitle = { Text(if (checks.isEmpty()) "Checking…" else "$done of ${checks.size} done") },
                    navigationIcon = { BackButton(vm::closeScreen) },
                    scrollBehavior = scroll,
                )
            },
            bottomBar = {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = vm::closeScreen) { Text(if (checks.isNotEmpty() && done == checks.size) "Done" else "Continue to terminal") }
                }
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(checks, key = { _, c -> c.title }) { i, c ->
                    val fixable = !c.ok && c.fix != null
                    SegmentedListItem(
                        onClick = { if (fixable) vm.fixSetup(c) },
                        enabled = true,
                        shapes = segShapes(i, checks.size),
                        colors = segColors(),
                        leadingContent = { StatusDot(c.ok, c.fix == null) },
                        supportingContent = { Text(c.detail) },
                        trailingContent = if (fixable) {
                            { FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { vm.fixSetup(c) }) { Text("Fix") } }
                        } else null,
                    ) { Text(c.title) }
                }
            }
        }
    }
}

@Composable
internal fun StatusDot(ok: Boolean, informational: Boolean) {
    val color = when {
        ok -> MaterialTheme.colorScheme.primary
        informational -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.18f), CircleShape),
    ) {
        if (ok) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = "done", tint = color, modifier = Modifier.size(20.dp))
        } else {
            Text(if (informational) "–" else "!", color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}
