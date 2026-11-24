package com.mtwabpin.kolahal.ui

import android.view.View
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sheet or a dialog is a window of its own, which shows Android's bars unless it hides them too. It
 * hides the bars home hides (`screen.hide-bars`, [DialogBars]), and leaves them alone when home hides none.
 */
@RunWith(AndroidJUnit4::class)
class HiddenBarsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val status = WindowInsetsCompat.Type.statusBars()
    private val nav = WindowInsetsCompat.Type.navigationBars()

    /** A view in the sheet's or dialog's window, once it is shown. */
    private var inWindow: View? = null

    /** Whether the bars of [type] show in the window of [inWindow]; null before it is laid out. */
    private fun shown(type: Int): Boolean? =
        inWindow?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(type) }

    @androidx.compose.runtime.Composable
    private fun Content() {
        val view = LocalView.current
        SideEffect { inWindow = view }
        Text("In the window")
    }

    /** `screen.hide-bars status`: a GUI sheet (a script's, a shader's details) has no status bar. */
    @Test
    fun aSheetHidesTheStatusBarAsHomeDoes() {
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalHiddenBars provides { HiddenBars.STATUS }) {
                    GuiSheet(onDismiss = {}) { Content() }
                }
            }
        }
        compose.waitUntil(5_000) { shown(status) == false }
        assertEquals(true, shown(nav))
    }

    /** `screen.hide-bars both`: the long-press sheet on a line has neither bar. */
    @Test
    fun theActionSheetHidesBothBarsAsHomeDoes() {
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalHiddenBars provides { HiddenBars.BOTH }) {
                    TermSheet(onDismiss = {}) { Content() }
                }
            }
        }
        compose.waitUntil(5_000) { shown(status) == false && shown(nav) == false }
    }

    /** A dialog (as the TTY style's sheets and Material's dialogs are) hides them too. */
    @Test
    fun aDialogHidesTheStatusBarAsHomeDoes() {
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalHiddenBars provides { HiddenBars.STATUS }) {
                    Dialog(onDismissRequest = {}) {
                        DialogBars()
                        Content()
                    }
                }
            }
        }
        compose.waitUntil(5_000) { shown(status) == false }
    }

    /** `screen.hide-bars none`: the sheet's window keeps its bars. */
    @Test
    fun withNoBarHiddenASheetKeepsTheBars() {
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalHiddenBars provides { HiddenBars.NONE }) {
                    GuiSheet(onDismiss = {}) { Content() }
                }
            }
        }
        compose.waitUntil(5_000) { shown(status) != null }
        compose.waitForIdle()
        assertEquals(true, shown(status))
    }
}
