/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import com.mtwabpin.kolahal.commands.BuildFeature

/**
 * The words for a feature that this build gets through Kolahal Extras, when Extras can't give it now ([ExtrasStatus]):
 * the setup checklist's row, `allow`, and the commands that need it. Pure.
 */
object ExtrasWords {
    /** What's missing, in one line: "Needs Kolahal Extras", "Kolahal Extras needs contacts access". */
    fun problem(status: ExtrasStatus, feature: BuildFeature): String = when (status) {
        ExtrasStatus.MISSING -> "Needs Kolahal Extras"
        ExtrasStatus.UNTRUSTED -> "Kolahal Extras is signed with another key"
        ExtrasStatus.SILENT -> "Kolahal Extras doesn't answer"
        ExtrasStatus.TOO_OLD -> "Needs a newer Kolahal Extras"
        ExtrasStatus.NOT_ALLOWED -> when (feature) {
            BuildFeature.LOCK_SERVICE -> "Kolahal Extras needs its screen lock turned on"
            BuildFeature.CONTACTS -> "Kolahal Extras needs contacts access"
            BuildFeature.CALL_LOG -> "Kolahal Extras needs call log access"
            BuildFeature.SHADER_OVER_APPS -> "Needs Kolahal Extras"
        }

        ExtrasStatus.READY -> "Through Kolahal Extras"
    }

    /** The hint under it, on a line in the log whose tap runs `allow <word>`. */
    fun hint(status: ExtrasStatus): String = when {
        status == ExtrasStatus.UNTRUSTED -> "Install both apps from the same place\nTap to read about Kolahal Extras"
        status.opensExtras -> "Tap to open Kolahal Extras"
        else -> "Tap to read about Kolahal Extras"
    }

    /** What `allow <word>` says once it opened the way on ([ExtrasLink.intent]). */
    fun opened(status: ExtrasStatus): String =
        if (status.opensExtras) "Opened Kolahal Extras" else "Opened Kolahal Extras' page in the guide"
}
