package com.mtwabpin.kolahal.widgets

import android.os.Build
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A widget's view is made once and used again when home is built again, until something it shows from changes. */
@RunWith(AndroidJUnit4::class)
class KeptWidgetViewsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val t = TestWidgets()
    private val widgets = t.host

    @After fun tearDown() {
        compose.runOnUiThread { widgets.releaseViews(compose.activity) }
        t.close()
    }

    /** A clock placed on the page, and its details as screens read them. */
    private fun clock(): Int {
        val clock = t.clocks().first()
        t.allowBinding(clock)
        return t.place(clock).id!!
    }

    private fun view(id: Int) = compose.runOnUiThread<android.appwidget.AppWidgetHostView> { widgets.view(compose.activity, id, widgets.cachedInfo(id)!!) }

    /** Built again in the same activity, home gets the same view back, out of the holder it was left in. */
    @Test fun theSameViewComesBack() {
        val id = clock()
        val first = view(id)
        compose.runOnUiThread { FrameLayout(compose.activity).addView(first) }
        val again = view(id)
        assertSame(first, again)
        assertNull("still in the old holder", again.parent)
    }

    /**
     * Let go as their activity is destroyed (`releaseViews`, which MainActivity's end calls), its
     * views aren't used again: the next one asked for is new. Same activity here; a view kept for
     * another one isn't tried.
     */
    @Test fun releasedViewsAreMadeAgain() {
        val id = clock()
        val first = view(id)
        compose.runOnUiThread { widgets.releaseViews(compose.activity) }
        assertNotSame(first, view(id))
    }

    /** The check at each start keeps details that didn't change, so the kept view is still used (Android 12 and later). */
    @Test fun theCheckAtStartKeepsUnchangedDetails() {
        assumeTrue("the install can't be told before Android 12", Build.VERSION.SDK_INT >= 31)
        val id = clock()
        val before = widgets.cachedInfo(id)
        assertNotNull(before)
        val first = view(id)
        widgets.prune()
        assertSame(before, widgets.cachedInfo(id))
        assertSame(first, view(id))
    }
}
