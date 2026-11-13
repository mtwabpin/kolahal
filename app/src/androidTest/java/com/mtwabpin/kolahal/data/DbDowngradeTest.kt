package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database from a newer Kolahal (a transfer from a phone with a newer version) opens, with its
 * data. Android's default refuses it at every open, which would crash home at every start.
 */
@RunWith(AndroidJUnit4::class)
class DbDowngradeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "downgrade-test.db"

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test fun aNewerDatabaseOpensWithItsData() {
        context.deleteDatabase(name)
        val first = Db(context, name)
        val current = first.writableDatabase.version
        first.writableDatabase.apply {
            execSQL("INSERT INTO kv(scope, k, v) VALUES ('downgrade', 'kept', 'yes')")
            // What a later version would leave: a higher number, and a column this one doesn't know.
            execSQL("ALTER TABLE kv ADD COLUMN later TEXT")
            version = current + 5
        }
        first.close()

        val older = Db(context, name)
        val db = older.writableDatabase
        assertEquals(current, db.version)
        db.rawQuery("SELECT v FROM kv WHERE scope = 'downgrade' AND k = 'kept'", null).use { c ->
            c.moveToFirst()
            assertEquals("yes", c.getString(0))
        }
        // The next update runs the steps above this version again; they must be safe to run twice.
        older.upgrade(db, current - 1)
        older.close()
    }
}
