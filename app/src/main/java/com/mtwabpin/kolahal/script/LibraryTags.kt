/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.SearchWords

/**
 * What a library script is, and how it runs. The library page has a filter chip for each tag, in
 * this order.
 * - No script names its tags: each one is worked out from what the script already declares
 *   ([LibraryTags.of]). So a new library script gets its tags by itself.
 * - They are not the words of a script's `@tags` line. Those are free words for Get more scripts.
 */
enum class LibraryTag(val label: String) {
    /** A script to start your own from. Its file name starts with `template-`. */
    TEMPLATE("Template"),

    /** It has a widget for the widgets page: a `@widget` line. */
    WIDGET("Widget"),

    /** You run it by typing its name: a `@command` line. */
    COMMAND("Command"),

    /** It suggests a timer: an `@every` or an `@at` line. */
    TIMER("Timer"),

    /** It runs when something happens on the phone: an `@on` line. */
    EVENT("Event"),

    /** It uses the internet. Once installed, it has the Internet switch ([ScriptAccess.INTERNET]). */
    INTERNET("Internet"),

    /** It never uses the internet: it has no Internet switch. Every script is this or [INTERNET]. */
    OFFLINE("Offline");

    /** Its name in saved state and in tests: `widget`. */
    val id: String = name.lowercase()
}

/** The tags of the library's scripts, and what the library page's search and chips keep. Plain functions, checked by LibraryTagsTest. */
object LibraryTags {
    /** How the file name of a template starts. */
    private const val TEMPLATE_PREFIX = "template-"

    /**
     * The tags of the script named [name] (its file name without `.lua` or `.js`) with [code], in
     * the order of [LibraryTag].
     * - The directives are read as everywhere else ([ScriptMeta]): a `@widget` line of a kind Kolahal
     *   doesn't draw is no widget, and an `@on` line needs an event Kolahal knows.
     * - Internet is what [ScriptAccess] reads from the code ([ScriptMeta.usesInternet]), so the chip
     *   and the script's Internet switch always agree.
     */
    fun of(name: String, code: String): Set<LibraryTag> {
        val internet = ScriptMeta.usesInternet(code)
        return LibraryTag.entries.filterTo(LinkedHashSet()) { tag ->
            when (tag) {
                LibraryTag.TEMPLATE -> name.startsWith(TEMPLATE_PREFIX)
                LibraryTag.WIDGET -> ScriptMeta.widget(code) != null
                LibraryTag.COMMAND -> ScriptMeta.isCommand(code)
                LibraryTag.TIMER -> ScriptMeta.every(code) != null || ScriptMeta.at(code)
                    .isNotEmpty()

                LibraryTag.EVENT -> ScriptMeta.events(code).isNotEmpty()
                LibraryTag.INTERNET -> internet
                LibraryTag.OFFLINE -> !internet
            }
        }
    }

    /** The tags with the [ids] given, in the order of [LibraryTag]. An id that no tag has is left out. */
    fun known(ids: Collection<String>): Set<LibraryTag> =
        LibraryTag.entries.filterTo(LinkedHashSet()) { it.id in ids }

    /**
     * Whether the search finds [entry]: its name, its title or its description has each of [words]
     * ([SearchWords]). With no words, every script is found.
     */
    fun found(entry: ScriptLibrary.Entry, words: List<String>): Boolean =
        SearchWords.allIn(words, listOf(entry.name, entry.title, entry.description))

    /**
     * Whether [entry] passes the chips that are on ([chosen]): it has the tag of one of them. Each
     * chip adds its scripts, as the chips of one group do in the Scripts tab's filters. With no chip
     * on, every script passes.
     */
    fun passes(entry: ScriptLibrary.Entry, chosen: Set<LibraryTag>): Boolean =
        chosen.isEmpty() || entry.tags.any { it in chosen }

    /** Whether the page lists [entry]: the search finds it, and it passes the chips. */
    fun shows(entry: ScriptLibrary.Entry, words: List<String>, chosen: Set<LibraryTag>): Boolean =
        found(entry, words) && passes(entry, chosen)

    /**
     * The chips and the number on each: how many of the scripts the search found have the tag.
     * - A tag that no script of [entries] has gets no chip. That doesn't depend on the search, so
     *   the chips stay in place while you type.
     * - The number is what the chip keeps on its own. The other chips don't change it.
     */
    fun counts(entries: List<ScriptLibrary.Entry>, words: List<String>): Map<LibraryTag, Int> {
        val found = entries.filter { found(it, words) }
        return LibraryTag.entries.filter { tag -> entries.any { tag in it.tags } }
            .associateWith { tag -> found.count { tag in it.tags } }
    }
}
