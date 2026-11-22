/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.data.sha256Hex
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptSources

/** One shader in a source's list (Settings → Shaders → Get more shaders, and `shader get`). */
data class SharedShader(
    override val name: String,
    val path: String,
    val title: String,
    val desc: String,
    val author: String,
    val version: String,
    val license: String,
    val tags: List<String>,
    /** It moves by itself (`@animated`). */
    val animated: Boolean,
    /** The oldest Kolahal it works with. */
    val requires: String?,
    val sha256: String,
    override val source: String = ScriptCatalog.SOURCE,
    /** It follows how the phone leans (it declares `tilt`). An older list doesn't say. */
    val tilt: Boolean = false,
) : ScriptSources.Listed

/**
 * The shaders in the sources' lists: the optional `shaders` of an index.json, beside its `scripts`.
 * - [parse] keeps only entries that can be installed as they are written.
 * - [state] says where one stands on this phone, from the user's file and what was recorded when a
 *   shader of its name was installed ([Origin]).
 *
 * Plain Kotlin, so the JVM tests check it (SharedShadersTest). [ScriptCatalog] reads the lists, and
 * [ShaderStore] installs.
 */
object SharedShaders {
    private val SHA = Regex("^[0-9a-f]{64}$")

    /**
     * The shaders of an index's `shaders` value ([list]), which came from [source]. Null, or anything
     * that isn't a list, is no shaders. An entry is left out when its name can't be a shader's, its file
     * isn't `shaders/<name>/<name>.agsl` (never a path that leaves `shaders/`), or it has no SHA-256.
     * One entry per name: the screen uses the name as the list key.
     */
    fun parse(list: Any?, source: String): List<SharedShader> =
        (list as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { m ->
            fun str(key: String) = m[key]?.toString()?.takeIf { it.isNotBlank() }
            val name = str("name")?.takeIf(ShaderCatalog::valid) ?: return@mapNotNull null
            val path = str("path")?.takeIf { it == pathOf(name) } ?: return@mapNotNull null
            val sha = str("sha256")?.lowercase()?.takeIf(SHA::matches) ?: return@mapNotNull null
            SharedShader(
                name = name,
                path = path,
                title = str("title") ?: name,
                desc = str("desc").orEmpty(),
                author = str("author").orEmpty(),
                version = str("version").orEmpty(),
                license = str("license").orEmpty(),
                tags = (m["tags"] as? List<*>).orEmpty().map { it.toString() },
                animated = m["animated"] == true,
                requires = str("requires"),
                sha256 = sha,
                source = source,
                tilt = m["tilt"] == true,
            )
        }.distinctBy { it.name }

    /** Where the file of shader [name] is in its source. */
    fun pathOf(name: String): String = "shaders/$name/$name.${ShaderCatalog.EXT}"

    /**
     * What is recorded of a shader installed from a list: the list's [sha256], the hash of the
     * [code] as it was saved, and the [source].
     */
    data class Origin(val sha256: String, val code: String, val source: String) {
        /** Whether [text] is still what was installed. */
        fun isCode(text: String): Boolean = hashOf(text) == code

        /** The record as it is stored. [read] gives it back. */
        fun text(): String =
            Json.stringify(mapOf("sha256" to sha256, "code" to code, "source" to source))

        companion object {
            /** The record of [e], installed with the code [text]. */
            fun of(e: SharedShader, text: String) = Origin(e.sha256, hashOf(text), e.source)

            /** A stored record; null when [raw] isn't one. */
            fun read(raw: String?): Origin? {
                val m = raw?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
                    ?: return null
                return Origin(
                    m["sha256"]?.toString() ?: return null,
                    m["code"]?.toString() ?: return null,
                    m["source"]?.toString() ?: return null,
                )
            }

            private fun hashOf(text: String) = sha256Hex(text.toByteArray())
        }
    }

    /**
     * Where [e] stands, for the version [kolahal]:
     * - [mine]: the code of the user's file of its name, null when there is none;
     * - [origin]: what was recorded when a shader of its name was installed from a list.
     *
     * The name of a shader that comes with Kolahal is taken: a file of it would take its place.
     */
    fun state(
        e: SharedShader,
        mine: String?,
        origin: Origin?,
        kolahal: String
    ): ScriptCatalog.State = when {
        needsNewer(e, kolahal) -> ScriptCatalog.State.NEEDS_NEWER
        mine == null && e.name in ShaderCatalog.BUNDLED -> ScriptCatalog.State.NAME_TAKEN
        mine == null -> ScriptCatalog.State.NOT_INSTALLED
        origin == null || origin.source != e.source -> ScriptCatalog.State.NAME_TAKEN
        !origin.isCode(mine) -> ScriptCatalog.State.MODIFIED
        origin.sha256 != e.sha256 -> ScriptCatalog.State.UPDATE_AVAILABLE
        else -> ScriptCatalog.State.INSTALLED
    }

    /**
     * The user's shader of code [mine], compared with what was installed ([origin]) and with [latest],
     * its entry in the list (null when the list isn't kept, or no longer has it). A version that needs
     * a newer Kolahal doesn't count as newer, nor one of another source.
     */
    fun compare(
        mine: String,
        origin: Origin,
        latest: SharedShader?,
        kolahal: String
    ) = ScriptCatalog.Copy(
        edited = !origin.isCode(mine),
        newer = latest != null && latest.source == origin.source &&
                latest.sha256 != origin.sha256 && !needsNewer(latest, kolahal),
        source = origin.source,
    )

    private fun needsNewer(e: SharedShader, kolahal: String) =
        e.requires != null && ScriptMeta.isNewer(e.requires, kolahal)

    /**
     * The shaders of [entries] that the search finds: each word of [query] is in the name, the title,
     * the description, the author or a tag ([SearchWords]). A blank [query] finds them all.
     */
    fun found(entries: List<SharedShader>, query: String): List<SharedShader> {
        val words = SearchWords.of(query)
        return entries.filter { e ->
            SearchWords.allIn(words, listOf(e.name, e.title, e.desc, e.author) + e.tags)
        }
    }
}
