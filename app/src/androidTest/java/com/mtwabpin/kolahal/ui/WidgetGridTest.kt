package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The widgets page puts each widget at its column and top. Columns run from the start: right to left in such a language. */
@RunWith(AndroidJUnit4::class)
class WidgetGridTest {
    @get:Rule val compose = createComposeRule()

    private fun left(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value
    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value

    @Test fun columnsRunFromTheStart() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.width(300.dp)) {
                    // Two half-width widgets side by side, no gap.
                    WidgetGrid(0.dp) {
                        Box(Modifier.gridPlace(0, 6, 0).height(40.dp).testTag("first"))
                        Box(Modifier.gridPlace(6, 6, 0).height(40.dp).testTag("second"))
                    }
                }
            }
        }
        assertEquals(0f, left("first"), 0.5f)
        assertEquals(150f, left("second"), 0.5f)
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        assertEquals(150f, left("first"), 0.5f)
        assertEquals(0f, left("second"), 0.5f)
    }

    /**
     * A widget sits where it was put, with space around it, and the grid is as tall as its lowest
     * widget. One without a place goes under the others.
     */
    @Test fun eachWidgetSitsWhereItWasPut() {
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                WidgetGrid(0.dp, Modifier.testTag("grid")) {
                    Box(Modifier.gridPlace(3, 6, 40).height(40.dp).testTag("middle"))
                    Box(Modifier.gridPlace(9, 3, 0).height(120.dp).testTag("tall"))
                    Box(Modifier.gridPlace(-1, 12, -1).height(20.dp).testTag("unplaced"))
                }
            }
        }
        assertEquals(75f, left("middle"), 0.5f)
        assertEquals(40f, top("middle"), 0.5f)
        assertEquals(225f, left("tall"), 0.5f)
        assertEquals(0f, top("tall"), 0.5f)
        assertEquals(0f, left("unplaced"), 0.5f)
        assertEquals(120f, top("unplaced"), 0.5f)
        assertEquals(140f, compose.onNodeWithTag("grid").getUnclippedBoundsInRoot().height.value, 0.5f)
    }
}
