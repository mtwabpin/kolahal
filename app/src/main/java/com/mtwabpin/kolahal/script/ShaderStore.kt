/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.Stores

/**
 * Installs the shaders shared in the sources of Get more scripts ([ScriptCatalog] reads their lists),
 * and remembers where each came from. Used by Settings → Shaders, and by `shader get` and
 * `shader update`.
 * - A shader is installed only when the downloaded file matches its list and can be used
 *   ([ShaderMeta.problems]). It is then one of the user's own files ([Shaders]).
 * - Installing doesn't turn it on.
 * - One the user edited is never replaced without asking.
 */
class ShaderStore(
    private val context: Context,
    private val catalog: ScriptCatalog = ScriptCatalog(context)
) {
    private val prefs by lazy { Stores.SHADER_CATALOG.open(context) }

    /** Every source's shaders from the last download, in source order. Nothing is downloaded. */
    fun cached(): List<SharedShader> = catalog.cachedShaders()

    /** Downloads [e]'s file and checks it against the list. */
    fun download(e: SharedShader): String =
        catalog.download(e, e.path, e.sha256, ShaderMeta.MAX_BYTES.toLong(), "shader")

    /** Where [e] stands on this phone. Reads a file: not on the main thread. */
    fun state(e: SharedShader): ScriptCatalog.State = SharedShaders.state(
        e,
        Shaders.own(context, e.name),
        origin(e.name),
        BuildConfig.VERSION_NAME
    )

    /** Why [e]'s name is taken ([ScriptCatalog.State.NAME_TAKEN]). */
    fun taken(e: SharedShader): String {
        val from = installedFrom(e.name)
        return when {
            from != null && from != e.source -> "You installed ${e.name} from $from"
            e.name !in Shaders.userNames -> "${e.name} comes with Kolahal"
            else -> "You have your own shader named ${e.name}"
        }
    }

    /**
     * Installs [e] with [code] from [download]: a new file of the user's, or a new version of the one
     * installed from the same source. [replace] lets it overwrite a copy the user edited.
     */
    fun install(e: SharedShader, code: String, replace: Boolean = false) {
        val requires = ShaderMeta.parse(code).requires
        if (requires != null && ScriptMeta.isNewer(requires, BuildConfig.VERSION_NAME)) {
            throw IllegalStateException("${e.name} needs Kolahal $requires")
        }
        ShaderMeta.problems(code).firstOrNull()
            ?.let { throw IllegalStateException("${e.name} can't be used: $it") }
        when (state(e)) {
            ScriptCatalog.State.NEEDS_NEWER -> throw IllegalStateException("${e.name} needs Kolahal ${e.requires}")
            ScriptCatalog.State.NAME_TAKEN -> throw IllegalStateException(taken(e))
            ScriptCatalog.State.MODIFIED ->
                if (!replace) throw IllegalStateException("you edited ${e.name}; replace it to install this version")

            else -> Unit
        }
        // Recorded first: saving the file makes the screens read it again, with where it is from.
        val before = recorded(e.name)
        record(e.name, SharedShaders.Origin.of(e, code).text())
        try {
            Shaders.save(context, e.name, code)
        } catch (failed: Exception) {
            record(e.name, before)
            throw failed
        }
    }

    /**
     * The user's shader [name] with code [mine], compared with what was installed and with the newest
     * version in [latest], the sources' lists. Null when it wasn't installed from a list.
     */
    fun copy(name: String, mine: String, latest: List<SharedShader>): ScriptCatalog.Copy? {
        val origin = origin(name) ?: return null
        return SharedShaders.compare(mine, origin, entry(name, latest), BuildConfig.VERSION_NAME)
    }

    /** The entry in [latest] of the shader that the user's [name] was installed from. Null when there is none. */
    fun entry(name: String, latest: List<SharedShader>): SharedShader? {
        val from = installedFrom(name) ?: return null
        return latest.firstOrNull { it.name == name && it.source == from }
    }

    /** The source that shader [name] was installed from. Null when it wasn't installed from a list. */
    fun installedFrom(name: String): String? = origin(name)?.source

    /** What was recorded when [name] was installed, as stored. Undo puts it back with [record]. */
    fun recorded(name: String): String? = prefs.getString(key(name), null)

    /** Sets what [recorded] returned. Null forgets it. */
    fun record(name: String, raw: String?) = prefs.edit {
        if (raw == null) remove(key(name)) else putString(key(name), raw)
    }

    /** Forgets that [name] was installed from a list: its file is gone. */
    fun forget(name: String) = record(name, null)

    /** What was installed under [name], as long as the user has a file of that name. */
    private fun origin(name: String): SharedShaders.Origin? =
        SharedShaders.Origin.read(recorded(name))?.takeIf { name in Shaders.userNames }

    private fun key(name: String) = "installed:$name"
}
