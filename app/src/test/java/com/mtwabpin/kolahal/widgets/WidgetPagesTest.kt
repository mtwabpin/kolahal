package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widgets pages ([WidgetPages]): how they are kept, named, found, added, removed and restored, and placement on each. */
class WidgetPagesTest {
    private val two = WidgetPages.locationOf(2)
    private val three = WidgetPages.locationOf(3)

    private fun script(
        name: String,
        location: String = WidgetLocation.PANEL,
        span: Int = 6,
        height: Int = 88,
        column: Int = -1,
        top: Int = -1,
    ) = PlacedWidget(
        null,
        WidgetSource.script(name),
        0L,
        height,
        0,
        location = location,
        span = span,
        column = column,
        topDp = top
    )

    private fun named(all: List<PlacedWidget>, name: String) =
        all.single { it.source == WidgetSource.script(name) }

    // ---- kept -----------------------------------------------------------------------------

    /** Page 1 is `panel`, as every line of today; a later page has `panel:<id>`. An old Kolahal reads that as page 1. */
    @Test
    fun aWidgetsPageIsItsLocation() {
        assertEquals("panel:2", two)
        assertEquals(2, WidgetPages.idOf(two))
        assertNull(WidgetPages.idOf(WidgetLocation.PANEL))
        assertTrue(WidgetPages.isPage(WidgetLocation.PANEL))
        assertTrue(WidgetPages.isPage(three))
        assertFalse(WidgetPages.isPage("panel:1"))
        assertFalse(WidgetPages.isPage("panel:x"))
        assertFalse(WidgetPages.isPage(WidgetLocation.OLD_HOME))
        assertEquals(three, WidgetLocation.of("PANEL:3"))
        assertEquals(WidgetLocation.PANEL, WidgetLocation.of("panel:1"))
        assertEquals(WidgetLocation.PANEL, WidgetLocation.of("dock"))
        assertEquals(WidgetLocation.OLD_HOME, WidgetLocation.of("home"))
    }

    /** A widget's page is in its stored line and in a backup, and comes back; a line of today is page 1. */
    @Test
    fun theStoredLineAndTheBackupKeepThePage() {
        val all = listOf(script("a", column = 0, top = 0), script("b", two, column = 6, top = 96))
        val text = WidgetCodec.encode(all)
        assertTrue(text, text.lines().contains("-|script:b|0|88|0|panel:2|6|plain|6|96"))
        assertEquals(all, WidgetCodec.decode(text))
        assertEquals(all, WidgetCodec.fromExport(WidgetCodec.export(all)))
        assertEquals(WidgetLocation.PANEL, WidgetCodec.decode("-|script:c|0|88|0|panel|6|plain|0|0").single().location)
        // Start leaves a later page's widgets where they are: only the pre-0.3.1 home moves to page 1.
        assertEquals(all, WidgetCodec.start(text).widgets)
        val old = WidgetOrder.onePage(all + script("c", WidgetLocation.OLD_HOME))
        assertEquals(two, named(old, "b").location)
        assertEquals(WidgetLocation.PANEL, named(old, "c").location)
    }

    /** The pages are stored one line each, with their names; page 1 always comes first, and a line that names no page is skipped. */
    @Test
    fun thePagesRoundTrip() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL, "Daily"), WidgetPage(three), WidgetPage(two, "Work"))
        val text = WidgetPages.encode(pages)
        assertEquals("panel|Daily\npanel:3|\npanel:2|Work", text)
        assertEquals(pages, WidgetPages.decode(text))
        assertEquals(WidgetPages.ONE, WidgetPages.decode(null))
        assertEquals(WidgetPages.ONE, WidgetPages.decode("dock|x\nhome|y"))
        assertEquals(
            listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work")),
            WidgetPages.decode("panel:2|Work")
        )
    }

    /**
     * The pages there are: the stored ones, then a page only a widget names, at most five, each name
     * once. A widget on a page past them goes to page 1, under what is there.
     */
    @Test
    fun pagesComeFromTheStoreAndTheWidgets() {
        val stored = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work"), WidgetPage(three, "work"))
        val widgets = listOf(script("a", WidgetPages.locationOf(9)))
        val pages = WidgetPages.of(stored, widgets)
        assertEquals(listOf(WidgetLocation.PANEL, two, three, WidgetPages.locationOf(9)), pages.map { it.location })
        assertEquals(listOf(null, "Work", null, null), pages.map { it.name })
        val many = (2..8).map { WidgetPage(WidgetPages.locationOf(it)) }
        assertEquals(WidgetPages.MAX, WidgetPages.of(many, emptyList()).size)
        val all = listOf(
            script("a", column = 0, top = 0, height = 100),
            script("b", WidgetPages.locationOf(8), column = 0, top = 0),
        )
        val fixed = WidgetPages.homeless(all, WidgetPages.of(many, all))
        assertEquals(WidgetLocation.PANEL, named(fixed, "b").location)
        assertEquals(100 + WidgetLayout.GAP_DP, named(fixed, "b").topDp)
    }

    // ---- named ----------------------------------------------------------------------------

    /** Until it is named, a page is "Page 2"; a name may be taken away again. */
    @Test
    fun aPageIsCalledByItsNameOrItsPlace() {
        val pages = WidgetPages.added(WidgetPages.added(WidgetPages.ONE)!!)!!
        assertEquals(listOf("Page 1", "Page 2", "Page 3"), pages.indices.map { WidgetPages.title(pages, it) })
        val named = WidgetPages.renamed(pages, 1, "  Work  ")
        assertEquals("Work", WidgetPages.title(named, 1))
        assertEquals("Page 2", WidgetPages.title(WidgetPages.renamed(named, 1, ""), 1))
        assertEquals("a b", WidgetPages.clean("a|b\n"))
        assertEquals(WidgetPages.MAX_NAME, WidgetPages.clean("x".repeat(40)).length)
    }

    /** A name is short, not only digits, and no other page's, by its name or its number, in any case. */
    @Test
    fun whichNamesAPageMayHave() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL, "Daily"), WidgetPage(two), WidgetPage(three, "Travel"))
        assertNull(WidgetPages.nameError(pages, 1, "Work"))
        assertNull(WidgetPages.nameError(pages, 1, ""))
        assertNull(WidgetPages.nameError(pages, 2, "travel"))
        assertNull(WidgetPages.nameError(pages, 1, "Page 2"))
        assertNull(WidgetPages.nameError(pages, 1, "Room 101"))
        assertEquals("Another page is called that", WidgetPages.nameError(pages, 1, "DAILY"))
        assertEquals("Another page is called that", WidgetPages.nameError(pages, 2, "page 2"))
        assertEquals("A name can't be only digits", WidgetPages.nameError(pages, 1, "42"))
        assertEquals("Up to 16 characters", WidgetPages.nameError(pages, 1, "x".repeat(17)))
    }

    /** `widget page` finds a page by its place, its name in any case, or the start of one name. */
    @Test
    fun aPageIsFoundByPlaceOrName() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL, "Daily"), WidgetPage(two, "Work"), WidgetPage(three, "Workshop"))
        assertEquals(1, WidgetPages.find(pages, "2"))
        assertNull(WidgetPages.find(pages, "4"))
        assertNull(WidgetPages.find(pages, "0"))
        assertEquals(0, WidgetPages.find(pages, "daily"))
        assertEquals(0, WidgetPages.find(pages, "da"))
        assertEquals(1, WidgetPages.find(pages, "WORK"))
        assertEquals(2, WidgetPages.find(pages, "works"))
        assertNull(WidgetPages.find(pages, "wo"))
        assertNull(WidgetPages.find(pages, "travel"))
        assertNull(WidgetPages.find(pages, " "))
        assertEquals(1, WidgetPages.find(listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two)), "page 2"))
    }

    // ---- added and removed ----------------------------------------------------------------

    /** A new page gets an id one past the largest, so a removed page's id is never one a widget still names; five at most. */
    @Test
    fun pagesAreAddedUpToFiveAndRemovedButPageOne() {
        var pages = WidgetPages.ONE
        repeat(4) { pages = WidgetPages.added(pages)!! }
        assertEquals(listOf("panel", "panel:2", "panel:3", "panel:4", "panel:5"), pages.map { it.location })
        assertNull(WidgetPages.added(pages))
        val fewer = WidgetPages.removed(pages, 1)
        assertEquals(listOf("panel", "panel:3", "panel:4", "panel:5"), fewer.map { it.location })
        assertEquals("panel:6", WidgetPages.added(fewer)!!.last().location)
        assertEquals(pages, WidgetPages.removed(pages, 0))
        assertEquals(pages, WidgetPages.removed(pages, 9))
    }

    /** A removed page's widgets go to page 1, under what is there, as they sat among themselves. */
    @Test
    fun aRemovedPagesWidgetsGoUnderPageOnes() {
        val all = listOf(
            script("a", column = 0, top = 0, height = 120),
            script("b", two, span = 6, column = 6, top = 40),
            script("c", two, span = 12, column = 0, top = 136),
        )
        val merged = WidgetPlacement.mergeInto(all, two)
        val under = 120 + WidgetLayout.GAP_DP
        assertEquals(WidgetLocation.PANEL to (6 to under), named(merged, "b").let { it.location to (it.column to it.topDp) })
        assertEquals(WidgetLocation.PANEL to (0 to under + 96), named(merged, "c").let { it.location to (it.column to it.topDp) })
        assertEquals(listOf("a", "b", "c"), WidgetOrder.inLocation(merged, WidgetLocation.PANEL).map { it.source!!.removePrefix("script:") })
        // Onto an empty page 1 they start at its top.
        assertEquals(0, named(WidgetPlacement.mergeInto(all.drop(1), two), "b").topDp)
    }

    /** An app's widget waiting to be added again ([PlacedWidget.pending]): its key holds its page and its order. */
    private fun waiting(location: String, order: Int = 0) =
        PlacedWidget(null, "com.example/.Clock", 0L, 96, order, location = location, span = 12, column = 0, topDp = 0)

    /**
     * Removing a page that has cards: its cards land under page 1's, the pages after it move up a
     * place, and page 1 or a page that isn't there can't be removed.
     */
    @Test
    fun aPageWithCardsIsRemoved() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work"), WidgetPage(three))
        val all = listOf(
            script("a", column = 0, top = 0, height = 120),
            script("b", two, span = 12, column = 0, top = 0),
            script("c", three, column = 0, top = 0),
        )
        val (left, widgets) = WidgetPages.withoutPage(pages, all, two)!!
        assertEquals(listOf(WidgetLocation.PANEL, three), left.map { it.location })
        assertEquals("Page 2", WidgetPages.title(left, 1))
        assertEquals(WidgetLocation.PANEL to 120 + WidgetLayout.GAP_DP, named(widgets, "b").let { it.location to it.topDp })
        assertEquals(three, named(widgets, "c").location)
        assertNull(WidgetPages.withoutPage(pages, all, WidgetLocation.PANEL))
        assertNull(WidgetPages.withoutPage(pages, all, WidgetPages.locationOf(4)))
    }

    /**
     * Two widgets of one app wait to be added again, each the first on its page. When one's page is
     * removed, or it is moved to the other's page, the two stay two: each keeps a key of its own, so
     * Add again and Dismiss act on one of them.
     */
    @Test
    fun twoWaitingWidgetsOfOneAppStayTwo() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two))
        val all = listOf(waiting(WidgetLocation.PANEL), waiting(two))
        assertEquals(2, all.map { it.key }.distinct().size)
        val (_, merged) = WidgetPages.withoutPage(pages, all, two)!!
        assertEquals(listOf(WidgetLocation.PANEL, WidgetLocation.PANEL), merged.map { it.location })
        assertEquals(2, merged.map { it.key }.distinct().size)
        // It keeps its place for when it is added again.
        assertEquals(0 to 0, merged[1].let { it.column to it.topDp })
        val moved = WidgetPlacement.moveToPage(all, all[1].key, WidgetLocation.PANEL)
        assertEquals(2, moved.map { it.key }.distinct().size)
        // Only those waiting on the page it goes to count.
        assertEquals(listOf(0, 1), WidgetPlacement.mergeInto(all + waiting(three, 1), two).filter { it.location == WidgetLocation.PANEL }.map { it.order })
    }

    /**
     * A backup with pages onto a phone that has page 1 only: its pages are added, with their names,
     * and each widget goes on its page, where it was.
     */
    @Test
    fun aBackupsWidgetsComeOntoItsPages() {
        val backupPages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(three, "Work"))
        val backup = listOf(
            script("a", column = 0, top = 0),
            script("b", three, column = 6, top = 96),
            waiting(three),
        )
        val (pages, all) = WidgetPages.imported(WidgetPages.ONE, emptyList(), backupPages, backup)
        assertEquals(listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work")), pages)
        assertEquals(WidgetLocation.PANEL, named(all, "a").location)
        assertEquals(two to (6 to 96), named(all, "b").let { it.location to (it.column to it.topDp) })
        assertEquals(two, all.single { it.pending }.location)
    }

    /**
     * Onto a phone with five pages, a backup's pages join them by place and none is added. Its
     * widgets keep their places where those are free and go to the first free one where they aren't.
     */
    @Test
    fun aBackupOntoFivePagesJoinsThemByPlace() {
        var five = WidgetPages.ONE
        repeat(4) { five = WidgetPages.added(five)!! }
        val here = listOf(script("x", two, span = 12, column = 0, top = 0))
        val backupPages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(WidgetPages.locationOf(7), "Travel"))
        val backup = listOf(
            script("a", WidgetPages.locationOf(7), span = 12, column = 0, top = 0),
            script("b", column = 0, top = 0),
        )
        val (pages, all) = WidgetPages.imported(five, here, backupPages, backup)
        assertEquals(five.map { it.location }, pages.map { it.location })
        assertEquals("Travel", WidgetPages.title(pages, 1))
        assertEquals(two to 88 + WidgetLayout.GAP_DP, named(all, "a").let { it.location to it.topDp })
        assertEquals(two to 0, named(all, "x").let { it.location to it.topDp })
        assertEquals(WidgetLocation.PANEL, named(all, "b").location)
    }

    /**
     * A backup made with two pages, imported after page 2 was removed: page 2's widgets are on page
     * 1 now, and none of them comes again. Page 2 comes back all the same, empty: a backup brings
     * every page it has.
     */
    @Test
    fun anImportBringsEveryPageOfTheBackup() {
        val backupPages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two))
        val backup = listOf(script("a", column = 0, top = 0), script("b", two, column = 0, top = 0))
        val here = listOf(script("a", column = 0, top = 0), script("b", column = 6, top = 0))
        val (pages, all) = WidgetPages.imported(WidgetPages.ONE, here, backupPages, backup)
        assertEquals(listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two)), pages)
        assertEquals(WidgetPlacement.renumber(here), all)
        // A page that was here stays, with or without widgets.
        val mine = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(three, "Empty"))
        assertEquals(mine, WidgetPages.imported(mine, here, backupPages, backup).first)
    }

    // ---- empty pages stay -----------------------------------------------------------------

    /**
     * A page the person added stays empty: after its last widget moves away, through a restart (the
     * stored form, read with widgets that name no such page), and when Arrange widgets closes, since
     * nothing in it removes a page but Remove page.
     */
    @Test
    fun anEmptyPageStays() {
        val pages = WidgetPages.added(WidgetPages.added(WidgetPages.ONE)!!)!!
        val widgets = listOf(script("a", two, column = 0, top = 0))
        // Its last widget moves to page 1: the pages don't change.
        val moved = WidgetPlacement.moveToPage(widgets, named(widgets, "a").key, WidgetLocation.PANEL)
        assertTrue(moved.none { it.location == two })
        // A restart reads the stored pages again, with widgets on page 1 only.
        assertEquals(pages, WidgetPages.of(WidgetPages.decode(WidgetPages.encode(pages)), moved))
        assertEquals(moved, WidgetPages.homeless(moved, pages))
        // Closing Arrange widgets runs no clean-up of pages.
        val closing = listOf("ui/widgets/WidgetsScreen.kt", "widgets/WidgetHost.kt").joinToString("\n") {
            sourceFile("src/main/java/com/mtwabpin/kolahal/$it").readText()
        }
        assertFalse(closing.contains("removeEmptyPages"))
        assertFalse(closing.contains("keepsEmptyPages"))
    }

    /** A backup carries empty pages, and an import brings them, onto page 1 alone or onto pages that are here. */
    @Test
    fun aBackupKeepsEmptyPages() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work"), WidgetPage(three))
        val back = WidgetPages.fromExport(WidgetPages.export(pages))
        assertEquals(pages, back)
        val widgets = listOf(script("a", column = 0, top = 0))
        assertEquals(pages, WidgetPages.imported(WidgetPages.ONE, emptyList(), back, widgets).first)
        assertEquals(pages, WidgetPages.imported(pages, widgets, back, widgets).first)
        // An older backup lists no pages: the pages here stay as they are, the empty ones too.
        assertEquals(pages, WidgetPages.imported(pages, widgets, WidgetPages.fromExport(emptyList()), widgets).first)
    }

    // ---- placed per page ------------------------------------------------------------------

    /** Each page is laid out on its own: a new widget goes into the first free place of its page, whatever the others hold. */
    @Test
    fun aNewWidgetGoesOnItsPage() {
        val all = listOf(script("a", span = 12, column = 0, top = 0))
        val added = WidgetPlacement.add(all, script("b", two, span = 12))
        assertEquals(two to (0 to 0), named(added, "b").let { it.location to (it.column to it.topDp) })
        val more = WidgetPlacement.add(added, script("c", two, span = 12))
        assertEquals(88 + WidgetLayout.GAP_DP, named(more, "c").topDp)
        // Widgets on two pages never meet, and each page is numbered from 0.
        assertFalse(WidgetPlacement.overlap(named(more, "a"), named(more, "b")))
        assertEquals(listOf(0, 0, 1), listOf("a", "b", "c").map { named(more, it).order })
        assertEquals(88, WidgetPlacement.pageHeightDp(more, WidgetLocation.PANEL))
        assertEquals(88 * 2 + WidgetLayout.GAP_DP, WidgetPlacement.pageHeightDp(more, two))
    }

    /** A widget moved to another page lands in its first free place from the top, at its size; on its own page nothing changes. */
    @Test
    fun aWidgetMovesToAnotherPage() {
        val all = listOf(
            script("a", span = 6, column = 6, top = 200),
            script("b", two, span = 6, column = 0, top = 0),
        )
        val moved = WidgetPlacement.moveToPage(all, named(all, "a").key, two)
        assertEquals(two, named(moved, "a").location)
        assertEquals(6 to 0, named(moved, "a").let { it.column to it.topDp })
        assertEquals(6, named(moved, "a").span)
        assertEquals(all, WidgetPlacement.moveToPage(all, named(all, "b").key, two))
        assertEquals(all, WidgetPlacement.moveToPage(all, named(all, "b").key, "dock"))
    }

    /** Close gaps and a move's lines keep to one page. */
    @Test
    fun closeGapsAndLinesKeepToOnePage() {
        val all = listOf(
            script("a", span = 12, column = 0, top = 300),
            script("b", two, span = 12, column = 0, top = 400),
        )
        val closed = WidgetPlacement.closeGaps(all, two)
        assertEquals(300, named(closed, "a").topDp)
        assertEquals(0, named(closed, "b").topDp)
        assertEquals(setOf(400, 400 + 88 + WidgetLayout.GAP_DP), WidgetPlacement.lines(all + script("c", two, column = 0, top = 0), "s:script:c"))
    }

    // ---- backups --------------------------------------------------------------------------

    /** A backup carries the pages and their names; one from before pages has none, and all is page 1. */
    @Test
    fun aBackupCarriesThePages() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL, "Daily"), WidgetPage(three, "Work"))
        assertEquals(pages, WidgetPages.fromExport(WidgetPages.export(pages)))
        assertEquals(mapOf("page" to "panel:3", "name" to "Work"), WidgetPages.export(pages)[1])
        assertEquals(mapOf<String, Any?>("page" to "panel"), WidgetPages.export(WidgetPages.ONE)[0])
        assertEquals(WidgetPages.ONE, WidgetPages.fromExport(emptyList()))
    }

    /**
     * A backup's pages join the pages here by place: a name comes with one where the page here has
     * none and no other page has it; a page beyond these is added while there is room.
     */
    @Test
    fun aBackupsPagesJoinTheseByPlace() {
        val here = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two, "Work"))
        val backup = listOf(
            WidgetPage(WidgetLocation.PANEL, "Daily"),
            WidgetPage(three, "Travel"),
            WidgetPage(WidgetPages.locationOf(7), "Work"),
        )
        val (pages, into) = WidgetPages.merge(here, backup)
        assertEquals(listOf("Daily", "Work", "Page 3"), pages.indices.map { WidgetPages.title(pages, it) })
        assertEquals(WidgetLocation.PANEL, into[WidgetLocation.PANEL])
        assertEquals(two, into[three])
        assertEquals(three, into[WidgetPages.locationOf(7)])
        // With five pages here, a backup's sixth has no room: its widgets go to page 1.
        var full = WidgetPages.ONE
        repeat(4) { full = WidgetPages.added(full)!! }
        val (same, to) = WidgetPages.merge(full, full + WidgetPage(WidgetPages.locationOf(9)))
        assertEquals(full, same)
        assertNull(to[WidgetPages.locationOf(9)])
    }

    /** `widget ls` takes the pages that have widgets, in their order, each with its widgets in page order. */
    @Test
    fun widgetLsGoesPageByPage() {
        val pages = listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage(two), WidgetPage(three))
        val all = WidgetPlacement.renumber(
            listOf(
                script("c", three, column = 0, top = 0),
                script("a", column = 0, top = 96),
                script("b", column = 0, top = 0),
            )
        )
        val grouped = WidgetPages.byPage(pages, all)
        assertEquals(listOf(0, 2), grouped.map { it.first })
        assertEquals(listOf("b", "a"), grouped[0].second.map { it.source!!.removePrefix("script:") })
    }
}
