package com.mtwabpin.kolahal.data

/**
 * How a page's search field finds its rows: a row is found when it has every word typed.
 * The Commands page, the Scripts tab, the script library and Get more scripts all search this way.
 * - Spaces, tabs and other blank characters split what is typed into words.
 * - A word can be a part of a longer word: `wea` finds `weather`.
 * - Upper and lower case are the same.
 */
object SearchWords {
    private val BLANKS = Regex("\\s+")

    /** The words of [query]. A blank query has none. */
    fun of(query: String): List<String> = query.split(BLANKS).filter { it.isNotEmpty() }

    /**
     * Whether each of [words] is in one of [texts]. Each word can be in another text: a row's name,
     * its title, its description. With no words, it is true: a blank search finds every row.
     */
    fun allIn(words: List<String>, texts: List<String>): Boolean =
        words.all { word -> texts.any { it.contains(word, ignoreCase = true) } }
}
