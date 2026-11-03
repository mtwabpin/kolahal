package com.mtwabpin.kolahal.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a query matches an app, best first: what app search sorts by before how often each is used. */
class SearchTiersTest {
    private fun tier(label: String, q: String, pkg: String = "com.example.app", component: String = "com.example.app/.main"): Int? {
        val lower = Fuzzy.fold(label)
        return AppRepository.tierOf(lower, lower.split(' ', '-', '_', '.'), pkg, component, q)
    }

    @Test fun eachTier() {
        assertEquals(0, tier("Maps", "maps"))
        assertEquals(1, tier("Maps", "ma"))
        assertEquals(2, tier("Google Maps", "maps"))
        assertEquals(3, tier("Calendar", "ndar"))
        assertEquals(4, tier("Camera", "cmr"))
        assertNull(tier("Camera", "zz"))
    }

    /** Two apps of one name are told apart by their package or package/activity, typed whole. */
    @Test fun aPackageNamesOneAppExactly() {
        assertEquals(0, tier("Clock", "com.example.app"))
        assertEquals(0, tier("Clock", "com.example.app/.main"))
        // A part of it is no match of its own.
        assertNull(tier("Clock", "com.example"))
    }

    /**
     * Accents don't matter once the label and what's typed are both folded. Search folds both (what's
     * typed in `searchRanked`, before it gets here); this test folds them itself, as search does.
     */
    @Test fun accentsAside() {
        assertEquals(1, tier("Café Mocha", Fuzzy.fold("Cafe")))
        assertEquals(2, tier("Le Café", Fuzzy.fold("café")))
    }

    /** The better tier wins when several fit: a label that starts with the query beats one of its words. */
    @Test fun theBestTierWins() {
        assertEquals(1, tier("Maps Maps", "maps"))
        assertEquals(1, tier("Notes notes", "no"))
    }
}
