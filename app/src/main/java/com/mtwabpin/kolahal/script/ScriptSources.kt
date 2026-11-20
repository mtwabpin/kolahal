package com.mtwabpin.kolahal.script

import java.net.URI

/**
 * The repositories that Get more scripts reads ("sources"). kolahal-scripts is always the first.
 * The user can add others that publish the same index.json on GitHub Pages or any https address.
 * Plain Kotlin with no Android types, so the JVM tests check it (ScriptSourcesTest).
 */
object ScriptSources {
    /**
     * One source.
     * - [name]: what Kolahal calls it, as `owner/name` for GitHub Pages, otherwise the host and path.
     * - [index]: the address of its index.json. Script paths in it are read from [base].
     */
    data class Source(val name: String, val index: String) {
        val base: String get() = index.removeSuffix(INDEX)
        val builtIn: Boolean get() = index == DEFAULT.index
    }

    private const val INDEX = "index.json"

    /** kolahal-scripts, where every script is reviewed before it is added. */
    val DEFAULT = Source("kolahal-scripts", "https://mtwabpin.github.io/kolahal-scripts/index.json")

    /** What [parse] made of the text typed: a source, or why it isn't one. */
    sealed interface Parsed {
        data class Ok(val source: Source) : Parsed
        data class Bad(val why: String) : Parsed
    }

    // GitHub's rules for user and repository names.
    private val REPO = Regex("^([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100})$")
    private val GITHUB_LINK = Regex("^(?:https://)?(?:www\\.)?github\\.com/([^/\\s]+/[^/\\s]+?)(?:\\.git)?/?$", RegexOption.IGNORE_CASE)

    /**
     * The source that [input] names:
     * - `owner/name` reads `https://owner.github.io/name/index.json`, and `owner/owner.github.io`
     *   reads `https://owner.github.io/index.json`. A github.com link to the repository works too.
     * - An https link to an index.json reads that file. A link that ends in `/` gets index.json added.
     * Only https. Nothing else about the address is checked.
     */
    fun parse(input: String): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed.Bad("Enter owner/name or a link to an index.json")
        val repo = GITHUB_LINK.find(text)?.groupValues?.get(1) ?: text.takeUnless { "://" in it }?.trimEnd('/')
        if (repo != null) {
            val m = REPO.find(repo) ?: return Parsed.Bad("Enter owner/name or a link to an index.json")
            val owner = m.groupValues[1].lowercase()
            val name = m.groupValues[2].removeSuffix(".git")
            if (name == "." || name == "..") return Parsed.Bad("Enter owner/name or a link to an index.json")
            val host = "$owner.github.io"
            val index = if (name.equals(host, ignoreCase = true)) "https://$host/$INDEX" else "https://$host/$name/$INDEX"
            return Parsed.Ok(Source(nameOf(index), index))
        }
        if (!text.startsWith("https://", ignoreCase = true)) return Parsed.Bad("Only https:// links")
        val uri = runCatching { URI(text) }.getOrNull()
        val authority = uri?.rawAuthority?.takeIf { it.isNotBlank() && uri.host != null } ?: return Parsed.Bad("That isn't a link")
        if (uri.rawQuery != null || uri.rawFragment != null) return Parsed.Bad("The link must end in $INDEX")
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val full = when {
            path.endsWith("/") -> path + INDEX
            path.endsWith("/$INDEX") -> path
            else -> return Parsed.Bad("The link must end in $INDEX")
        }
        val index = "https://${authority.lowercase()}$full"
        return Parsed.Ok(Source(nameOf(index), index))
    }

    /**
     * The name of the source at [index]: `owner/name` for a GitHub Pages address
     * (`owner/owner.github.io` for the site at its root), otherwise the host and the path without index.json.
     */
    fun nameOf(index: String): String {
        if (index == DEFAULT.index) return DEFAULT.name
        val rest = index.removePrefix("https://").removeSuffix(INDEX).trimEnd('/')
        val host = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        if (host.endsWith(".github.io") && '/' !in path) {
            val owner = host.removeSuffix(".github.io")
            return "$owner/${path.ifEmpty { host }}"
        }
        return rest
    }

    /** Every source, in order: kolahal-scripts, then the addresses in [added]. Each one once. */
    fun list(added: List<String>): List<Source> =
        (listOf(DEFAULT) + added.map { Source(nameOf(it), it) }).distinctBy { it.name }

    /** [added] with [source] at the end. The same list when it is there already, or is kolahal-scripts. */
    fun adding(added: List<String>, source: Source): List<String> =
        if (list(added).any { it.name == source.name }) added else added + source.index

    /** [added] without the source named [name] (or with that address). kolahal-scripts can't be removed. */
    fun removing(added: List<String>, name: String): List<String> =
        added.filterNot { it == name || nameOf(it) == name }

    /** The source that [name] means: its name or its address, or the text that added it (`owner/name`). */
    fun find(sources: List<Source>, name: String): Source? =
        sources.firstOrNull { it.name == name || it.index == name }
            ?: (parse(name) as? Parsed.Ok)?.source?.let { s -> sources.firstOrNull { it.name == s.name } }

    /**
     * The entry that `script get [ref]` installs, from [entries] in source order.
     * - A plain name: the first source that has it.
     * - `source/name`: that source's (the source as [find] reads it).
     */
    fun pick(entries: List<ScriptCatalog.Entry>, ref: String, sources: List<Source>): ScriptCatalog.Entry? {
        val name = ref.substringAfterLast('/')
        if ('/' !in ref) return entries.firstOrNull { it.name == name }
        val source = find(sources, ref.substringBeforeLast('/')) ?: return null
        return entries.firstOrNull { it.name == name && it.source == source.name }
    }

    /** "1 script", "3 scripts". */
    fun scriptCount(n: Int): String = if (n == 1) "1 script" else "$n scripts"

    /** "1 source", "2 sources". */
    fun sourceCount(n: Int): String = if (n == 1) "1 source" else "$n sources"

    /** What `script get` calls [e] by: its name, or `source/name` when another source comes first with that name. */
    fun ref(entries: List<ScriptCatalog.Entry>, e: ScriptCatalog.Entry): String =
        if (entries.first { it.name == e.name }.source == e.source) e.name else "${e.source}/${e.name}"

    /**
     * [added] with the sources of a backup's [saved] list that it doesn't have yet. An address that
     * isn't a source (not https, not an index.json) goes into [skipped].
     */
    fun fromBackup(added: List<String>, saved: List<*>, skipped: MutableList<String> = ArrayList()): List<String> =
        saved.fold(added) { list, item ->
            when (val p = parse(item?.toString().orEmpty())) {
                is Parsed.Ok -> adding(list, p.source)
                is Parsed.Bad -> list.also { skipped += "script source $item" }
            }
        }
}
