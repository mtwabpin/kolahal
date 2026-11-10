package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorMathTest {
    /** Plan B: the held row stays empty for 300 ms, then fills until the actions open at 800 ms. */
    @Test fun theHoldFillsAsItCounts() {
        assertEquals(0f, SelectorMath.holdFill(0), 0f)
        assertEquals(0f, SelectorMath.holdFill(300), 0f)
        assertEquals(0.5f, SelectorMath.holdFill(550), 0.001f)
        assertEquals(1f, SelectorMath.holdFill(800), 0f)
        assertEquals(1f, SelectorMath.holdFill(2_000), 0f)
    }

    /** A move clears the hold: the fill drains away rather than vanishing, and rises at once. */
    @Test fun aStoppedHoldDrains() {
        assertEquals(0.8f, SelectorMath.holdShown(0.2f, 0.8f, 16), 0f)
        assertEquals(0.8f - 60f / SelectorMath.DRAIN_MS, SelectorMath.holdShown(0.8f, 0f, 60), 0.001f)
        assertEquals(0f, SelectorMath.holdShown(0.8f, 0f, 500), 0f)
    }

    /** B2: the far side is the panel's last 30%, away from the strip. */
    @Test fun theFarSideIsAwayFromTheStrip() {
        // Strip on the right, panel at 0..252: the far side is left of 75.6.
        assertTrue(SelectorMath.farSide(60f, 0f, 252f, left = false))
        assertFalse(SelectorMath.farSide(120f, 0f, 252f, left = false))
        // Strip on the left, panel at 108..360: the far side is right of 284.4.
        assertTrue(SelectorMath.farSide(300f, 108f, 252f, left = true))
        assertFalse(SelectorMath.farSide(200f, 108f, 252f, left = true))
    }

    /** Plan A: with gesture navigation a touch starts only in the middle 200dp; otherwise anywhere on the strip. */
    @Test fun aTouchStartsInTheMiddleWhenTheEdgeSwipesBack() {
        assertEquals(75f to 200f, SelectorMath.startZone(350f, 200f, backGesture = true))
        assertEquals(0f to 350f, SelectorMath.startZone(350f, 200f, backGesture = false))
        assertEquals(0f to 180f, SelectorMath.startZone(180f, 200f, backGesture = true))
    }

    @Test fun letterOfSectionsByFirstLetter() {
        assertEquals('M', SelectorMath.letterOf("Maps"))
        assertEquals('M', SelectorMath.letterOf("maps"))
        assertEquals('E', SelectorMath.letterOf("Éclair"))
        assertEquals('#', SelectorMath.letterOf("1Password"))
        assertEquals('#', SelectorMath.letterOf("Телеграм"))
        assertEquals('#', SelectorMath.letterOf(""))
        assertEquals('W', SelectorMath.letterOf("  ‎WhatsApp"))
    }

    private data class A(val label: String, val pinned: Boolean = false, val profile: Int = 0)

    private fun build(items: List<A>) =
        SelectorMath.build(items, { it.label }, { it.label + it.profile }, { it.pinned }, { it.profile })

    @Test fun buildFavouritesFirstThenAlphabeticalSections() {
        val list = build(listOf(A("chrome", profile = 1), A("Calendar"), A("Chrome"), A("Camera", pinned = true), A("2048"), A("Maps")))
        assertEquals(
            listOf("Camera", "2048", "Calendar", "Camera", "Chrome", "chrome", "Maps"),
            list.map { it.item.label },
        )
        assertEquals(listOf('★', '#', 'C', 'C', 'C', 'C', 'M'), list.map { it.section })
        assertEquals(listOf(1, 1, 4, 1), SelectorMath.sectionSizes(list))
        assertEquals(listOf(true, true, true, false, false, false, true), list.map { it.sectionStart })
        assertEquals(listOf(0, 1, 2, 2, 2, 2, 3), list.map { it.sectionIndex })
        assertTrue(list[0].favourite && !list[3].favourite)
        // A favourite is listed twice, so keys must still be unique.
        assertEquals(list.size, list.map { it.key }.toSet().size)
        // Personal before work for the same label.
        assertEquals(listOf(0, 1), list.filter { it.item.label.equals("chrome", true) }.map { it.item.profile })
    }

    @Test fun buildWithoutFavouritesStartsAtFirstLetter() {
        val list = build(listOf(A("b"), A("a")))
        assertEquals(listOf('A', 'B'), list.map { it.section })
        assertEquals(listOf(0, 1), list.map { it.sectionIndex })
        assertTrue(build(emptyList()).isEmpty())
    }

    /** Plan F: with the strip at the bottom of the edge, the favourites come last, under the thumb. */
    @Test fun favouritesCanComeLast() {
        val list = SelectorMath.build(
            listOf(A("Maps"), A("Camera", pinned = true), A("2048")),
            { it.label }, { it.label }, { it.pinned }, { it.profile }, favouritesLast = true,
        )
        assertEquals(listOf("2048", "Camera", "Maps", "Camera"), list.map { it.item.label })
        assertEquals(listOf('#', 'C', 'M', '★'), list.map { it.section })
        assertEquals(listOf(0, 1, 2, 3), list.map { it.sectionIndex })
        assertTrue(list.last().favourite && list.last().sectionStart)
        assertEquals(list.size, list.map { it.key }.toSet().size)
    }

    /** Every letter gets the same stretch of the strip; the dead bands pin the first and the last. */
    @Test fun everyLetterGetsTheSameStretch() {
        val d = SelectorMath.DEAD
        fun t(u: Float) = d + u * (1 - 2 * d)
        assertEquals(0f, SelectorMath.alongLetters(0f, 4), 0f)
        assertEquals(0f, SelectorMath.alongLetters(d, 4), 0f)
        assertEquals(3.99f, SelectorMath.alongLetters(1f, 4), 0.001f)
        assertEquals(1f, SelectorMath.alongLetters(t(0.25f), 4), 1e-3f)
        assertEquals(2.5f, SelectorMath.alongLetters(t(0.625f), 4), 1e-3f)
        // Monotonic and continuous: small finger moves never jump.
        var prev = 0f
        for (i in 0..1000) {
            val a = SelectorMath.alongLetters(i / 1000f, 27)
            assertTrue(a >= prev)
            assertTrue(a - prev < 0.05f)
            prev = a
        }
        // The strip's ends are the first letter and the last.
        assertEquals(0, SelectorMath.letterAt(SelectorMath.alongLetters(0f, 27), -1, 27))
        assertEquals(26, SelectorMath.letterAt(SelectorMath.alongLetters(1f, 27), -1, 27))
        assertEquals(0f, SelectorMath.alongLetters(0.5f, 0), 0f)
    }

    /** Each letter goes in the middle of its stretch. */
    @Test fun lettersSitInTheMiddleOfTheirStretch() {
        val d = SelectorMath.DEAD
        val middles = SelectorMath.letterMiddles(4)
        assertEquals(4, middles.size)
        assertEquals(d + 0.125f * (1 - 2 * d), middles[0], 1e-5f)
        assertEquals(d + 0.375f * (1 - 2 * d), middles[1], 1e-5f)
        assertEquals(d + 0.875f * (1 - 2 * d), middles[3], 1e-5f)
        assertEquals(0, SelectorMath.letterMiddles(0).size)
    }

    /** Plan C: under the aim speed a slide aims; from two and a half times it, it glides; between, a blend. */
    @Test fun slowSlidesAimAndFastOnesGlide() {
        assertEquals(1f, SelectorMath.aimWeight(0f, 100f), 0f)
        assertEquals(1f, SelectorMath.aimWeight(100f, 100f), 0f)
        assertEquals(0.5f, SelectorMath.aimWeight(175f, 100f), 0.001f)
        assertEquals(0f, SelectorMath.aimWeight(250f, 100f), 0f)
        assertEquals(0f, SelectorMath.aimWeight(900f, 100f), 0f)
        // No aim speed: always a glide.
        assertEquals(0f, SelectorMath.aimWeight(10f, 0f), 0f)
    }

    /** Plan C: aiming moves an app per step, never faster than the glide; a glide goes where the strip maps it. */
    @Test fun aimingMovesAnAppPerStep() {
        // The glide would move 4 apps for this 20px slide; aiming at 10px an app moves 2.
        assertEquals(12f, SelectorMath.aim(10f, 30f, 34f, 20f, 10f, weight = 1f, count = 100), 0.001f)
        assertEquals(8f, SelectorMath.aim(10f, 34f, 30f, -20f, 10f, weight = 1f, count = 100), 0.001f)
        // Where the glide moves less than the step (a short list), aiming moves no more than it.
        assertEquals(10.5f, SelectorMath.aim(10f, 3f, 3.5f, 20f, 10f, weight = 1f, count = 100), 0.001f)
        // Gliding goes straight to the strip's place, wherever the aim had got to.
        assertEquals(34f, SelectorMath.aim(10f, 30f, 34f, 20f, 10f, weight = 0f, count = 100), 0.001f)
        // Half and half: half the aim's step, and half way back to the glide's place.
        assertEquals(10f + 0.5f * 2f + 0.5f * 24f, SelectorMath.aim(10f, 30f, 34f, 20f, 10f, weight = 0.5f, count = 100), 0.001f)
        // Never past the list's ends.
        assertEquals(0f, SelectorMath.aim(0.5f, 3f, 2f, -20f, 10f, weight = 1f, count = 100), 0.001f)
        assertEquals(99.99f, SelectorMath.aim(99.5f, 90f, 99f, 20f, 10f, weight = 1f, count = 100), 0.001f)
        // A step of 0 never aims.
        assertEquals(34f, SelectorMath.aim(10f, 30f, 34f, 20f, 0f, weight = 1f, count = 100), 0.001f)
    }

    /** Plan C: the measured speed follows the thumb over about 60 ms, so one jittery move doesn't flip it. */
    @Test fun theSpeedIsSmoothed() {
        assertEquals(50f, SelectorMath.smoothSpeed(0f, 200f, 15), 0.001f)
        assertEquals(200f, SelectorMath.smoothSpeed(0f, 200f, 60), 0.001f)
        assertEquals(200f, SelectorMath.smoothSpeed(0f, 200f, 500), 0.001f)
    }

    /** Plan D: each section's room goes by the square root of its size: four times the apps, twice the room. */
    @Test fun everyLetterGetsRoom() {
        val sections = SelectorMath.Sections(listOf(1, 4, 16))
        // √1 : √4 : √16 is 1 : 2 : 4, out of 7.
        assertEquals(1f / 7, sections.shares[0], 1e-5f)
        assertEquals(2f / 7, sections.shares[1], 1e-5f)
        assertEquals(4f / 7, sections.shares[2], 1e-5f)
        val d = SelectorMath.DEAD
        fun t(u: Float) = d + u * (1 - 2 * d)
        // The dead bands pin the ends; halfway across a section's room is halfway through its apps.
        assertEquals(0f, sections.along(0f), 0f)
        assertEquals(0f, sections.along(d), 0f)
        assertEquals(20.99f, sections.along(1f), 0.001f)
        assertEquals(0.5f, sections.along(t(0.5f / 7)), 1e-3f)
        assertEquals(1f + 2f, sections.along(t(2f / 7)), 1e-3f)
        assertEquals(5f + 8f, sections.along(t(5f / 7)), 1e-3f)
        // Monotonic and continuous: small finger moves never jump.
        var prev = 0f
        for (i in 0..1000) {
            val a = sections.along(i / 1000f)
            assertTrue(a >= prev)
            assertTrue(a - prev < 0.2f)
            prev = a
        }
        assertEquals(0f, SelectorMath.Sections(emptyList()).along(0.5f), 0f)
    }

    /** Each section's letter goes in the middle of its room. */
    @Test fun lettersSitInTheMiddleOfTheirRoom() {
        val d = SelectorMath.DEAD
        val middles = SelectorMath.Sections(listOf(1, 4, 16)).middles()
        assertEquals(3, middles.size)
        assertEquals(d + 0.5f / 7 * (1 - 2 * d), middles[0], 1e-5f)
        assertEquals(d + 2f / 7 * (1 - 2 * d), middles[1], 1e-5f)
        assertEquals(d + 5f / 7 * (1 - 2 * d), middles[2], 1e-5f)
    }

    @Test fun theStripCoversTheWholeList() {
        val rows = 100
        val one = SelectorMath.Sections(listOf(rows))
        // The strip's top is the first app, its bottom the last, its middle the middle one.
        assertEquals(0, SelectorMath.focusedIndex(one.along(0f), rows))
        assertEquals(rows - 1, SelectorMath.focusedIndex(one.along(1f), rows))
        assertEquals(50, SelectorMath.focusedIndex(one.along(0.5f), rows))
        // With sections too: the ends are the list's ends.
        val many = SelectorMath.Sections(listOf(5, 2, 30, 1, 62))
        assertEquals(0, SelectorMath.focusedIndex(many.along(0f), rows))
        assertEquals(rows - 1, SelectorMath.focusedIndex(many.along(1f), rows))
    }

    /** Labels nudged apart just enough: in order, the gap kept, inside the bounds; a crowd spreads around its middle. */
    @Test fun labelsAreNudgedApartAsLittleAsItTakes() {
        fun place(vararg w: Float, gap: Float = 5f, top: Float = 0f, bottom: Float = 100f) =
            SelectorMath.labelPositions(w, gap, top, bottom).toList()
        assertEquals(listOf(10f, 30f, 50f), place(10f, 30f, 50f))
        assertEquals(listOf(7f, 12f, 17f), place(10f, 12f, 14f))
        assertEquals(listOf(0f, 5f), place(1f, 2f))
        assertEquals(listOf(90f, 95f, 100f), place(98f, 99f, 100f))
        // Too many for the room: some are left out, the rest at least a gap apart.
        val crowded = place(0f, 10f, 40f, 45f, 100f, gap = 30f)
        assertEquals(0f, crowded[0], 0f)
        assertTrue(crowded[1].isNaN())
        assertEquals(40f, crowded[2], 0f)
        assertTrue(crowded[3].isNaN())
        assertEquals(100f, crowded[4], 0f)
        assertTrue(SelectorMath.labelPositions(FloatArray(0), 5f, 0f, 100f).isEmpty())
    }

    /** As Niagara does: the focused app is drawn level with the thumb, wherever on the screen it is. */
    @Test fun theFocusedAppIsLevelWithTheFinger() {
        val rows = 100
        val row = 40f
        val view = 800f
        val max = SelectorMath.maxScroll(rows, row, view)
        // Where a list position is drawn: its row, less the scroll, plus the panel's shift.
        fun drawnAt(at: Float, listY: Float) = SelectorMath.place(listY, max).let { (scroll, shift) -> at * row - scroll + shift }
        for ((fraction, finger) in listOf(0.5f to 600f, 0f to 600f, 1f to 100f, 0.02f to 750f)) {
            val at = (fraction * rows).coerceIn(0f, rows - 0.01f)
            val listY = SelectorMath.levelWith(at, finger, row)
            // Level with the finger, at the list's ends too (the panel moves the rest of the way).
            assertEquals("$fraction", finger, drawnAt(at, listY), 0.01f)
            // So picking starts on the same app.
            assertEquals(at, SelectorMath.contact(listY, finger, row), 0.01f)
        }
        // In the middle the list scrolls and the panel stays; at the top it can't, and the panel moves down.
        assertEquals(0f, SelectorMath.place(SelectorMath.levelWith(50f, 600f, row), max).second)
        assertEquals(600f, SelectorMath.place(SelectorMath.levelWith(0f, 600f, row), max).second)
        // A short list never scrolls: the panel does it all.
        assertEquals(0f, SelectorMath.place(SelectorMath.levelWith(5f, 100f, row), SelectorMath.maxScroll(10, row, view)).first)
        assertEquals(-1, SelectorMath.focusedIndex(Float.NaN, rows))
        assertEquals(-1, SelectorMath.focusedIndex(3f, 0))
        assertEquals(9, SelectorMath.focusedIndex(25f, 10))
        assertEquals(0, SelectorMath.focusedIndex(-2f, 10))
    }

    @Test fun edgeSpeedRampsAtTheEnds() {
        assertEquals(0f, SelectorMath.edgeSpeed(400f, 800f, 50f, 1f))
        assertEquals(-1f, SelectorMath.edgeSpeed(0f, 800f, 50f, 1f))
        assertEquals(-1f, SelectorMath.edgeSpeed(-30f, 800f, 50f, 1f))
        assertEquals(-0.5f, SelectorMath.edgeSpeed(25f, 800f, 50f, 1f), 1e-5f)
        assertEquals(1f, SelectorMath.edgeSpeed(900f, 800f, 50f, 1f))
        assertEquals(0.5f, SelectorMath.edgeSpeed(775f, 800f, 50f, 1f), 1e-5f)
        // A short panel keeps a still middle.
        assertEquals(0f, SelectorMath.edgeSpeed(50f, 100f, 50f, 1f))
    }

    /** One finger's path: (in the list, row, section) per move, felt as the selector does. */
    private fun feels(vararg moves: Triple<Boolean, Int, Int>): List<SelectorMath.Feel> {
        var wasSelecting = false
        var lastRow = -1
        var lastSection = -1
        return moves.map { (selecting, row, section) ->
            SelectorMath.feel(selecting, wasSelecting, row, lastRow, section, lastSection).also {
                wasSelecting = selecting
                lastRow = row
                lastSection = section
            }
        }
    }

    @Test fun lettersAppsAndTheSwitchEachHaveAFeel() {
        val edge = { row: Int, section: Int -> Triple(false, row, section) }
        val list = { row: Int, section: Int -> Triple(true, row, section) }
        assertEquals(
            listOf(
                SelectorMath.Feel.NONE, // touching down
                SelectorMath.Feel.NONE, // another app, the same letter
                SelectorMath.Feel.LETTER,
                SelectorMath.Feel.SWITCH, // into the list
                SelectorMath.Feel.APP,
                SelectorMath.Feel.NONE, // still on that app
                SelectorMath.Feel.APP, // a new letter in the list is still an app
                SelectorMath.Feel.SWITCH, // back to the letters
                SelectorMath.Feel.LETTER,
            ),
            feels(edge(0, 0), edge(3, 0), edge(5, 1), list(5, 1), list(6, 1), list(6, 1), list(7, 2), edge(9, 3), edge(12, 4)),
        )
    }

    private fun entries(vararg labels: String) =
        SelectorMath.build(labels.toList(), { it }, { it }, { false }, { 0 })

    /** Letters: where each letter's apps start in the list, then the end. */
    @Test fun eachLetterStartsWhereTheLastEnds() {
        val list = entries("Maps", "Alarm", "Meet", "Books", "Music", "Bank")
        assertEquals(listOf(0, 1, 3, 6), SelectorMath.sectionStarts(list).toList())
        assertEquals(listOf(0), SelectorMath.sectionStarts(entries()).toList())
    }

    /** Letters: the letter under the thumb, kept until the thumb is a quarter of a letter past its stretch. */
    @Test fun aLetterHoldsUntilTheThumbIsWellPastIt() {
        assertEquals(2, SelectorMath.letterAt(2.5f, -1, 5))
        assertEquals(2, SelectorMath.letterAt(3.2f, 2, 5))
        assertEquals(3, SelectorMath.letterAt(3.3f, 2, 5))
        assertEquals(2, SelectorMath.letterAt(1.8f, 2, 5))
        assertEquals(1, SelectorMath.letterAt(1.7f, 2, 5))
        // A jump of several letters goes straight there; the ends hold.
        assertEquals(4, SelectorMath.letterAt(4.99f, 0, 5))
        assertEquals(0, SelectorMath.letterAt(-1f, 3, 5))
        assertEquals(-1, SelectorMath.letterAt(1f, 0, 0))
    }

    /** Letters: the first app is level with the letter, the rest below, moved up only to stay on screen. */
    @Test fun aLettersAppsStartLevelWithIt() {
        // 40px rows, the list between 10 and 710.
        assertEquals(380f, SelectorMath.letterTop(400f, 3, 40f, 10f, 710f), 0f)
        // Near the bottom: up just far enough for all five.
        assertEquals(510f, SelectorMath.letterTop(650f, 5, 40f, 10f, 710f), 0f)
        // More than fit: from the top.
        assertEquals(10f, SelectorMath.letterTop(650f, 30, 40f, 10f, 710f), 0f)
        assertEquals(10f, SelectorMath.letterTop(5f, 1, 40f, 10f, 710f), 0f)
    }

    /** A tapped letter's list stays put: what moved the panel becomes room at the list's start or end. */
    @Test fun aListLeftOpenStaysWhereItWas() {
        // Moved down 300px at the list's start: 300px of room before the first app, not scrolled.
        assertEquals(Triple(300f, 0f, 0f), SelectorMath.keepInPlace(0f, 300f))
        // Moved up 120px at its end: room after the last app, scrolled 120px further.
        assertEquals(Triple(0f, 120f, 1_120f), SelectorMath.keepInPlace(1_000f, -120f))
        assertEquals(Triple(0f, 0f, 500f), SelectorMath.keepInPlace(500f, 0f))
    }

    /** Open, the list scrolls far enough for the first app to reach the strip's top and the last its bottom. */
    @Test fun anOpenListReachesTheStrip() {
        // Panel 0..1000, strip 400..700: 400 more room at the start (the scroll moves with it), 300 at the end.
        assertEquals(Triple(400f, 300f, 400f), SelectorMath.reachStrip(0f, 0f, 0f, 0f, 1000f, 400f, 300f))
        // Room keepInPlace gave already counts.
        assertEquals(Triple(400f, 300f, 250f), SelectorMath.reachStrip(250f, 0f, 100f, 0f, 1000f, 400f, 300f))
        assertEquals(Triple(400f, 500f, 400f), SelectorMath.reachStrip(0f, 500f, 0f, 0f, 1000f, 400f, 300f))
        // A strip at the panel's top and bottom needs no room.
        assertEquals(Triple(0f, 0f, 0f), SelectorMath.reachStrip(0f, 0f, 0f, 0f, 1000f, 0f, 1000f))
    }

    /** selector.filter: a letter's apps alone move level with it, but never off the screen. */
    @Test fun aLettersAppsStayOnScreen() {
        // A 190px list in a 700px space: it may move up to 510px from its place at the bottom.
        assertEquals(-200f, SelectorMath.keepOnScreen(-200f, 190f, 700f), 0f)
        assertEquals(-510f, SelectorMath.keepOnScreen(-900f, 190f, 700f), 0f)
        assertEquals(0f, SelectorMath.keepOnScreen(40f, 190f, 700f), 0f)
        // Taller than the space: it stays put and scrolls instead.
        assertEquals(0f, SelectorMath.keepOnScreen(-50f, 900f, 700f), 0f)
    }

    /** The wave: out at the bubble, easing back to the edge three letters away. */
    @Test fun theWaveEasesBackToTheEdge() {
        assertEquals(1f, SelectorMath.waveOut(0f), 0f)
        assertEquals(0.75f, SelectorMath.waveOut(1f), 0.001f)
        assertEquals(SelectorMath.waveOut(-1.5f), SelectorMath.waveOut(1.5f), 0f)
        assertEquals(0f, SelectorMath.waveOut(3f), 0f)
        assertEquals(0f, SelectorMath.waveOut(-7f), 0f)
    }

    /** The wave pushes the letters next to the bubble out to make room for it, keeps their order, and moves far ones by the same amount. */
    @Test fun theWaveMakesRoomForTheBubble() {
        val unit = 12f
        val spread = SelectorMath.waveSpread(unit, room = 30f)
        val ys = (0..10).map { SelectorMath.waveY(it * unit, 5 * unit, unit, spread) }
        assertEquals(5 * unit, ys[5], 0.001f)
        assertEquals(30f, ys[6] - ys[5], 0.01f)
        assertEquals(30f, ys[5] - ys[4], 0.01f)
        assertTrue("in order", ys.zipWithNext().all { (a, b) -> b > a })
        // Past the wave's reach, letters keep their spacing.
        assertEquals(unit, ys[10] - ys[9], 0.001f)
        // Letters already far enough apart need no spread.
        assertEquals(0f, SelectorMath.waveSpread(40f, room = 30f), 0f)
        assertEquals(0f, SelectorMath.waveSpread(0f, room = 30f), 0f)
    }

    /** The wave draws every letter there's room for, never the bubble's own, and none on top of another. */
    @Test fun theWaveDrawsOnlyLettersWithRoom() {
        val roomy = floatArrayOf(0f, 20f, 40f, 70f, 100f, 120f, 140f)
        val scales = FloatArray(roomy.size) { 1f }
        assertEquals(listOf(true, true, true, false, true, true, true), SelectorMath.waveShown(roomy, scales, 3, gap = 10f, clear = 26f).toList())
        // Crowded: kept letters are at least a letter apart, and clear of the bubble.
        val crowded = FloatArray(12) { it * 6f }
        val shown = SelectorMath.waveShown(crowded, FloatArray(12) { 1f }, 6, gap = 10f, clear = 26f)
        val kept = crowded.indices.filter { shown[it] }.map { crowded[it] }
        assertTrue(kept.isNotEmpty())
        assertTrue("apart: $kept", kept.zipWithNext().all { (a, b) -> b - a >= 10f })
        assertTrue("off the bubble: $kept", kept.all { kotlin.math.abs(it - crowded[6]) >= 26f })
        assertFalse(SelectorMath.waveShown(roomy, scales, -1, 10f, 26f).any { it })
    }

    /** Letters: along the edge no app is at the thumb, so only a new letter ticks; the list still ticks per app. */
    @Test fun lettersTickPerLetter() {
        assertEquals(SelectorMath.Feel.NONE, SelectorMath.feel(false, false, -1, -1, 3, -1))
        assertEquals(SelectorMath.Feel.NONE, SelectorMath.feel(false, false, -1, -1, 3, 3))
        assertEquals(SelectorMath.Feel.LETTER, SelectorMath.feel(false, false, -1, -1, 4, 3))
        assertEquals(SelectorMath.Feel.SWITCH, SelectorMath.feel(true, false, 7, -1, 4, 4))
        assertEquals(SelectorMath.Feel.APP, SelectorMath.feel(true, true, 8, 7, 4, 4))
    }

    /** Plan C: aiming along the edge ticks once per app, as picking does; a new letter still ticks as a letter. */
    @Test fun aimingTicksPerApp() {
        assertEquals(SelectorMath.Feel.APP, SelectorMath.feel(false, false, 4, 3, 1, 1, aiming = true))
        assertEquals(SelectorMath.Feel.NONE, SelectorMath.feel(false, false, 4, 3, 1, 1, aiming = false))
        assertEquals(SelectorMath.Feel.LETTER, SelectorMath.feel(false, false, 5, 4, 2, 1, aiming = true))
        assertEquals(SelectorMath.Feel.NONE, SelectorMath.feel(false, false, 4, 4, 1, 1, aiming = true))
    }
}
