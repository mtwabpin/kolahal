/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mtwabpin.kolahal.commands.Access
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.UsageValues
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.usage.UsageAccess

/**
 * The words for an option that can't work because Android hasn't given Kolahal an access ([Accesses]), and which
 * accesses the values a shader can follow need. Pure (AccessNeedsTest).
 *
 * Such an option isn't shown as if it worked: in its place is one line that says what it needs, and a button that
 * asks for it ([AccessNeeded]). What is stored stays, and `config` still changes it. Settings' list of them is
 * `NEEDS_ACCESS`, beside the settings that only apply with another one.
 */
internal object AccessNeeds {
    /**
     * Android's name for [access] inside a sentence: "usage access", "notification access". A name with capitals
     * after its first word keeps them: "Do Not Disturb access".
     */
    fun name(access: Access): String =
        if (access.title.split(' ').drop(1).any { it.firstOrNull()?.isUpperCase() == true }) access.title
        else access.title.lowercase()

    /** The line in place of an option that needs [access]: "Needs usage access". */
    fun line(access: Access): String = "Needs ${name(access)}"

    /** The button under it: "Allow usage access". */
    fun button(access: Access): String = "Allow ${name(access)}"

    /**
     * The access ([Accesses] word) that a shader's parameter needs to follow the value [id] ([com.mtwabpin.kolahal.shader.ShaderValues]):
     * usage access for the phone's use ([UsageValues]), as screen time isn't known without it and unlocks are then
     * counted only while Kolahal runs. Null for the rest.
     */
    fun ofValue(id: String): String? = if (UsageValues.has(id)) Accesses.USAGE else null

    /**
     * What the value [id] says in the Follow menu and on its button while the access it needs is [missing], in place
     * of "no value yet" or its value: "needs usage access". Null when it needs none, or has it.
     */
    fun valueNote(id: String, missing: Set<String>): String? =
        ofValue(id)?.takeIf { it in missing }?.let(Accesses::find)?.let { line(it).replaceFirstChar(Char::lowercase) }

    /** The accesses that what [params] follow need, where a shader's [followed] values are these ([ShaderParams.follow]). */
    fun ofParams(params: List<ShaderParam>, followed: Map<String, String>): Set<String> =
        params.mapNotNullTo(LinkedHashSet<String>()) { p -> ShaderParams.follow(followed, p)?.let { ofValue(it) } }
}

/**
 * Which of [words] ([Accesses]) Android doesn't give Kolahal. Read again each time the screen comes back, as from
 * Android's page where an access is turned on.
 */
@Composable
internal fun rememberMissing(words: Set<String>): Set<String> {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember(lifecycle, words) {
        words.filterTo(LinkedHashSet<String>()) { w -> Accesses.find(w)?.let { !SetupChecks.given(context, it) } ?: false }
    }
}

/**
 * The line and the button in place of an option that needs the access [word] ([Accesses]): "Needs usage access",
 * then **Allow usage access**, which calls [onAsk].
 */
@Composable
internal fun AccessNeeded(word: String, onAsk: (Access) -> Unit, modifier: Modifier = Modifier) {
    val access = Accesses.find(word) ?: return
    Column(modifier.testTag("needs-access $word")) {
        Text(
            AccessNeeds.line(access),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        FilledTonalButton(onClick = { onAsk(access) }, shapes = ButtonDefaults.shapes()) { Text(AccessNeeds.button(access)) }
    }
}

/** Opens Android's page for usage access, as `allow usage` does: for a screen without the terminal at hand. */
internal fun openUsageAccess(context: Context) {
    runCatching { context.startActivity(UsageAccess.intent()) }
}
