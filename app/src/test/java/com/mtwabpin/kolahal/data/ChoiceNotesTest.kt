package com.mtwabpin.kolahal.data

import org.junit.Assert.assertTrue
import org.junit.Test

/** What a choice's values say beside them in the prompt's suggestions ([ConfigKey.Choice.notes]). */
class ChoiceNotesTest {
    /** A note names one of its setting's values, so a value renamed later can't leave a note behind. */
    @Test
    fun everyNoteIsForOneOfTheValues() {
        for (key in Keys.ALL.filterIsInstance<ConfigKey.Choice>()) for (value in key.notes.keys) {
            assertTrue("${key.key}: $value is not one of ${key.choices}", value in key.choices)
        }
    }

    /** A note is a few words: one short line beside the value, without a full stop. */
    @Test
    fun notesAreShort() {
        for (key in Keys.ALL.filterIsInstance<ConfigKey.Choice>()) for ((value, note) in key.notes) {
            assertTrue("${key.key} $value: '$note' is long", note.length <= 70)
            assertTrue("${key.key} $value: '$note' ends with a full stop", !note.endsWith("."))
        }
    }

    /** Every choice setting whose values don't explain themselves says what they do. */
    @Test
    fun theStyleAndTheSelectorSayWhatTheyDo() {
        for (key in listOf(Keys.HOME_STYLE, Keys.SUGGESTIONS_STYLE, Keys.SELECTOR_SIDE, Keys.SHOW_TIMESTAMPS, Keys.LAUNCH_INSTANT)) {
            assertTrue("${key.key} has a note for each value", key.choices.all { it in key.notes })
        }
    }
}
