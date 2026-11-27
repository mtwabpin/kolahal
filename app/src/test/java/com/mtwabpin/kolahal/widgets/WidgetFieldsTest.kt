package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fields of each kind are declared once ([WidgetFields]). Here the stored form ([WidgetJson]) is
 * held to that declaration: descriptions that between them give every declared field of a kind, at
 * every level, are stored with each field and read back as the parser made them. A field the stored
 * form misses fails here, not on a phone as a card that lost it.
 */
class WidgetFieldsTest {
    private fun parse(kind: WidgetKind, raw: Map<String, Any?>) = WidgetParser.parse(raw, kind, { it }, { it })

    private val base = mapOf(
        "title" to "Pune",
        "icon" to "☀",
        "color" to "warn",
        "shape" to "sun",
        "tap" to mapOf("args" to "now", "label" to "Refresh", "page" to "log"),
        "staleAfter" to 7200,
        "refresh" to 600,
        "note" to "Done today",
        "footer" to "City sensors",
    )

    /** For each kind, descriptions that between them give each of its fields. */
    private val samples: Map<WidgetKind, List<Map<String, Any?>>> = mapOf(
        WidgetKind.STAT to listOf(
            base + mapOf(
                "kind" to "stat", "value" to "27°", "label" to "Sunny", "unit" to "C", "delta" to "+2", "trend" to "up",
                "more" to listOf(mapOf("value" to "62%", "label" to "humidity")),
                "progress" to 1.4, "moving" to true, "points" to listOf(1, 2, 3), "scene" to "pond",
                "target" to "8,000", "badge" to "live",
            ),
            mapOf("tap" to mapOf("url" to "https://example.com/a"), "value" to 1),
        ),
        WidgetKind.TEXT to listOf(base + mapOf("text" to "Carry an umbrella.", "caption" to "Today")),
        WidgetKind.LIST to listOf(
            base + mapOf(
                "items" to listOf(
                    mapOf(
                        "title" to "Story", "detail" to "by pg", "trailing" to "412", "icon" to "Y", "color" to "good",
                        "tap" to mapOf("url" to "https://example.com/1"), "done" to true,
                    ),
                    mapOf("title" to "BTC", "range" to mapOf("low" to 94_100.5, "high" to 97_900, "value" to 97_420)),
                ),
                "empty" to "Nothing new",
                "lead" to mapOf("value" to "42", "unit" to "min", "label" to "Next"),
            ),
        ),
        WidgetKind.PROGRESS to listOf(
            base + mapOf(
                "value" to 72, "max" to 100, "text" to "72%", "label" to "Goal", "style" to "rings", "moving" to true,
                "items" to listOf(
                    mapOf(
                        "label" to "Water", "value" to 4, "max" to 8, "text" to "4 of 8", "color" to "info",
                        "unit" to "glasses", "moving" to true,
                    ),
                ),
            ),
            mapOf(
                "value" to 40, "max" to 80, "min" to 10, "unit" to "°C", "style" to "gauge",
                "zones" to listOf(mapOf("from" to 10, "color" to "good"), mapOf("from" to 60, "color" to "bad")),
            ),
            mapOf(
                "stages" to listOf("Ordered", "Shipped", "Delivered"), "stage" to 2, "value" to 0.5,
                "details" to listOf("Mon 09:12", "Tue 18:40"), "badge" to "2",
            ),
        ),
        WidgetKind.CHART to listOf(
            base + mapOf(
                "points" to listOf(41, 58, 37), "style" to "bars", "min" to 0, "max" to 80, "value" to "46",
                "label" to "unlocks today", "labels" to listOf("2 days ago", "today"), "summary" to "Unlocks",
            ),
        ),
        WidgetKind.COUNTDOWN to listOf(
            base + mapOf(
                "to" to 1_797_000_000L, "from" to 1_790_000_000L, "label" to "Goa trip", "style" to "days",
                "reached" to "Today!",
            ),
        ),
        WidgetKind.BUTTONS to listOf(
            base + mapOf(
                "items" to listOf(
                    mapOf("label" to "On", "icon" to "☀", "color" to "good", "tap" to mapOf("args" to "on"), "value" to "21°"),
                ),
            ),
        ),
        WidgetKind.TOGGLES to listOf(
            base + mapOf(
                "items" to listOf(mapOf("label" to "Mute", "icon" to "🌙", "on" to false, "tap" to mapOf("args" to "mute"), "value" to "40%")),
            ),
        ),
        WidgetKind.CLOCK to listOf(
            base + mapOf("zones" to listOf(mapOf("zone" to "Europe/Lisbon", "label" to "Lisbon")), "seconds" to true),
        ),
        WidgetKind.AGENDA to listOf(
            base + mapOf(
                "events" to listOf(
                    mapOf(
                        "title" to "Final", "starts" to 1_800_000_000L, "ends" to 1_800_007_200L, "allDay" to true,
                        "where" to "Pune", "color" to "good", "tap" to mapOf("args" to "open"), "id" to 7,
                    ),
                ),
                "days" to 7,
                "empty" to "Nothing planned",
            ),
        ),
        WidgetKind.IMAGE to listOf(
            base + mapOf(
                "image" to "https://example.com/a.png", "alt" to "A picture", "caption" to "Monday", "fit" to "fit",
            ),
        ),
        WidgetKind.VERSUS to listOf(
            base + mapOf(
                "sides" to listOf(
                    mapOf(
                        "name" to "Nilsen", "mark" to "N", "color" to "accent", "value" to 72, "text" to "1:12",
                        "detail" to "3rd · 41 pts",
                    ),
                    mapOf("name" to "Carter"),
                ),
                "score" to "1–0", "turn" to 1, "moving" to true, "caption" to "17. b3", "badge" to "LIVE",
            ),
        ),
        WidgetKind.SKY to listOf(
            base + mapOf(
                "rises" to 1_800_000_000L, "sets" to 1_800_040_000L, "phase" to 0.78, "waxing" to true,
                "phases" to listOf(mapOf("phase" to 1, "waxing" to true, "at" to 1_800_300_000L)),
            ),
        ),
    )

    /** The keys [raw] gives at each level, by the path to it: "" for the description, "items" for its rows, "items.range". */
    private fun keys(raw: Map<*, *>, fields: List<WidgetField>, path: String = "", out: MutableMap<String, MutableSet<String>> = HashMap()): Map<String, Set<String>> {
        out.getOrPut(path) { HashSet() } += raw.keys.map { it.toString() }
        for (f in fields) if (f.inner.isNotEmpty()) {
            val v = raw[f.name] ?: continue
            val inner = if (f.many) (v as List<*>).map { it as Map<*, *> } else listOf(v as Map<*, *>)
            val at = if (path.isEmpty()) f.name else "$path.${f.name}"
            for (m in inner) keys(m, f.inner, at, out)
        }
        return out
    }

    /** The declared fields at each level, by the same paths as [keys]. */
    private fun declared(fields: List<WidgetField>, path: String = "", out: MutableMap<String, Set<String>> = HashMap()): Map<String, Set<String>> {
        out[path] = fields.map { it.name }.toSet()
        for (f in fields) if (f.inner.isNotEmpty()) declared(f.inner, if (path.isEmpty()) f.name else "$path.${f.name}", out)
        return out
    }

    /** The samples give every field of every kind at every level, and nothing else. */
    @Test
    fun theSamplesGiveEveryDeclaredField() {
        assertEquals(WidgetKind.forScripts.toSet(), samples.keys)
        // The common fields and a tap's: between all the samples.
        val common = HashMap<String, MutableSet<String>>()
        for ((kind, list) in samples) {
            val own = WidgetFields.ownOf(kind)
            val commonNames = WidgetFields.namesOf(WidgetFields.commonOf(kind)).toSet()
            val given = HashMap<String, MutableSet<String>>()
            for (raw in list) keys(raw, WidgetFields.commonOf(kind) + own).forEach { (path, k) ->
                when (path) {
                    "" -> {
                        given.getOrPut(path) { HashSet() } += k - commonNames
                        common.getOrPut(path) { HashSet() } += k intersect commonNames
                    }
                    "tap" -> common.getOrPut(path) { HashSet() } += k
                    else -> given.getOrPut(path) { HashSet() } += k
                }
            }
            assertEquals(kind.id, declared(own), given)
        }
        assertEquals(declared(WidgetFields.COMMON), common)
    }

    /** Each sample is stored with every field it gives, at every level, and read back as the parser made it. */
    @Test
    fun theStoredFormKeepsEveryField() {
        for ((kind, list) in samples) for (raw in list) {
            val spec = parse(kind, raw)
            val text = WidgetJson.encode(spec)
            assertEquals("${kind.id}: read back", spec, WidgetJson.decode(text))
            val stored = Json.parse(text) as Map<*, *>
            val fields = WidgetFields.commonOf(kind) + WidgetFields.ownOf(kind)
            val kept = keys(stored, fields)
            for ((path, names) in keys(raw, fields)) {
                // An event's id is taken and not kept.
                val missing = names - kept[path].orEmpty() - "id"
                assertTrue("${kind.id}: $path: not stored: $missing", missing.isEmpty())
            }
        }
    }
}
