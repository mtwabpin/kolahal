/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Lab
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.GroupHeader

/** What Settings' Lab page says above its switch. */
internal const val LAB_INTRO =
    "These features aren't finished. They may change, or go, in a later version. Turning Lab off hides them all."

/** The line at the top of Settings' Lab page ([LAB_CATEGORY]), above its switch. */
internal fun LazyListScope.labIntro() {
    item(key = "lab:about") {
        Text(
            LAB_INTRO,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
    }
}

/** The icon of a Lab feature's row. */
private fun icon(feature: Lab.Feature): Int = when (feature) {
    Lab.Feature.PICTURE -> R.drawable.ic_image
}

/**
 * Under Lab's switch, a row for each Lab feature that is on ([Lab.features]): what it is and where to find it. A tap
 * goes there ([onOpen]).
 */
internal fun LazyListScope.labFeatures(on: Boolean, onOpen: (Lab.Feature) -> Unit) {
    val features = Lab.features(on, Build.VERSION.SDK_INT)
    if (features.isEmpty()) return
    item(key = "h:lab-features") { GroupHeader("Features") }
    item(key = "lab-features") {
        ActionGroup(features.map { f -> Action(icon(f), f.title, f.subtitle, chevron = true) { onOpen(f) } })
        Spacer(Modifier.height(8.dp))
    }
}
