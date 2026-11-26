package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The system prompt, and reading a rich answer. The prompt's stable part comes first (the format, the
 * design guidance, the example, the catalog's schema), so the services cache it; the user's own
 * instructions come last. Nothing from the phone is in it.
 */
object Prompts {
    /** The system prompt: rich ([catalogId], [schema]: the catalog the app draws with) or plain, then [custom]. */
    fun system(rich: Boolean, catalogId: String, schema: String, custom: String): String {
        val base = if (rich) rich(catalogId, schema) else PLAIN
        val own = custom.trim()
        return if (own.isEmpty()) base else "$base\n\nThe user's own instructions, for every answer:\n$own"
    }

    private val PLAIN = """
You are the assistant on a page of a phone's home screen app. Answer in plain text: short paragraphs, and simple Markdown where it helps (**bold**, *italic*, `code`, lists with "- " or "1. ", headings with "#"). No tables, no pictures, no links to open: the app shows links as text.
Keep answers short enough to read on a phone; say more only when asked.
You can't see the phone or do anything on it: the conversation is all you get from it.
""".trim()

    private fun rich(catalogId: String, schema: String) = """
You draw your answers on a phone with the A2UI protocol, version 0.9. Answer only with a JSON array of A2UI server-to-client messages: no prose, no code fences.

Messages: each is an object with "version": "v0.9" and exactly one of these keys:
- "createSurface": {"surfaceId", "catalogId": "$catalogId", "sendDataModel": true}
- "updateComponents": {"surfaceId", "components": [...]}: a flat list; each component has a unique "id", "component" (its type from the catalog) and its properties. Children are named by id ("child": "id", "children": ["id", ...]). Exactly one component has the id "root".
- "updateDataModel": {"surfaceId", "path": "/a/b", "value": ...}: sets the data model at a JSON Pointer.
- "deleteSurface": {"surfaceId"}

The conversation is a thread: each answer that shows something new creates a new surface, "s1" for the first, then "s2", "s3" and so on. To change something already shown (after the user taps one of its buttons, or asks to change it), send updateComponents or updateDataModel to that surface's id: it changes in place, where it is in the thread.

Rules:
- Use only the components and properties of the catalog below.
- A dynamic value is a literal, or {"path": "/json/pointer"} bound to the data model. Input components (TextField, CheckBox, ChoicePicker, Slider, DateTimeInput) bind their "value" to a path.
- A Button's "action" is {"event": {"name": "...", "context": {"key": {"path": "/..."}}}}. Never use "functionCall" actions or openUrl: the app ignores them. Images, video and audio are never loaded: don't use them.
- When the user acts, you get {"action": {"name", "surfaceId", "sourceComponentId", "timestamp", "context"}, "metadata": {"a2uiClientDataModel": ...}}. Answer by updating that surface.
- When you get {"error": ...}, your last answer was rejected: send a corrected one.
- You can't see the phone or do anything on it: the conversation is all you get from it.

Design: the screen is a phone's, about 360 dp wide. Make it calm, dense and easy to scan.
- Hierarchy: one title per surface (Text variant "h2"), section headings "h4", body text "body", small notes and labels "caption". Never skip straight to large text for ordinary content.
- Group related things in a Card: one card per topic, not one per line. Inside a card, a Column with the parts in order.
- Rows for things that belong side by side: a label and its value, an icon and its text, two or three small stats. Never more than three columns across: the phone's width doesn't hold more. Give each item of a row of stats "weight": 1 so they share the width.
- Numbers that matter are large ("h3") with a "caption" under them saying what they are.
- Short labels: a few words, no full sentences in headings. Put details in body text under them.
- Icons only from the catalog's own names, beside a label to help scanning; not as decoration everywhere.
- Dividers sparingly, between groups that a card doesn't already separate.
- Nothing interactive (TextField, Button, CheckBox, Slider…) unless the user asked for something to fill in or choose. A plain question gets a plain, well-laid-out answer.
- For a short or conversational answer, one card with a few lines of text is right: not every answer needs a dashboard.

A good dense screen (a week's running summary):
${example(catalogId)}

The catalog (JSON Schema):
$schema
""".trim()

    /** The example in the rich prompt: a title, a row of stat cards, a card of rows, no input. */
    internal fun example(catalogId: String) = EXAMPLE.replace("CATALOG", catalogId)

    private val EXAMPLE = """
[
{"version":"v0.9","createSurface":{"surfaceId":"s1","catalogId":"CATALOG","sendDataModel":true}},
{"version":"v0.9","updateComponents":{"surfaceId":"s1","components":[
{"id":"root","component":"Column","children":["title","stats","runs_card"]},
{"id":"title","component":"Text","text":"This week's running","variant":"h2"},
{"id":"stats","component":"Row","children":["s_dist","s_time","s_pace"]},
{"id":"s_dist","component":"Card","child":"s_dist_col","weight":1},
{"id":"s_dist_col","component":"Column","children":["s_dist_v","s_dist_l"]},
{"id":"s_dist_v","component":"Text","text":"24.6 km","variant":"h3"},
{"id":"s_dist_l","component":"Text","text":"Distance","variant":"caption"},
{"id":"s_time","component":"Card","child":"s_time_col","weight":1},
{"id":"s_time_col","component":"Column","children":["s_time_v","s_time_l"]},
{"id":"s_time_v","component":"Text","text":"2 h 18 m","variant":"h3"},
{"id":"s_time_l","component":"Text","text":"Time","variant":"caption"},
{"id":"s_pace","component":"Card","child":"s_pace_col","weight":1},
{"id":"s_pace_col","component":"Column","children":["s_pace_v","s_pace_l"]},
{"id":"s_pace_v","component":"Text","text":"5:36","variant":"h3"},
{"id":"s_pace_l","component":"Text","text":"Pace /km","variant":"caption"},
{"id":"runs_card","component":"Card","child":"runs"},
{"id":"runs","component":"Column","children":["runs_h","run1","div","run2"]},
{"id":"runs_h","component":"Text","text":"Runs","variant":"h4"},
{"id":"run1","component":"Row","children":["run1_i","run1_t","run1_v"],"align":"center"},
{"id":"run1_i","component":"Icon","name":"event"},
{"id":"run1_t","component":"Text","text":"Tuesday, easy","weight":1},
{"id":"run1_v","component":"Text","text":"8.2 km","variant":"caption"},
{"id":"div","component":"Divider"},
{"id":"run2","component":"Row","children":["run2_i","run2_t","run2_v"],"align":"center"},
{"id":"run2_i","component":"Icon","name":"star"},
{"id":"run2_t","component":"Text","text":"Saturday, long run","weight":1},
{"id":"run2_v","component":"Text","text":"16.4 km","variant":"caption"}
]}}
]
""".trim()

    /**
     * The A2UI messages in [answer], each as its JSON text. The model may wrap them in a code fence or
     * words: the JSON is taken from the first `[` or `{` to its last match. An object with "messages"
     * gives those, and a single message is a list of one. Throws [IllegalArgumentException] when there
     * is no JSON there.
     */
    fun messages(answer: String): List<String> {
        val json = jsonPart(answer) ?: throw IllegalArgumentException("There's no JSON in the answer")
        val value = try {
            JSONTokener(json).nextValue()
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException(e.message)
        }
        val list = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("messages") ?: JSONArray().put(value)
            else -> throw IllegalArgumentException("The answer isn't a list of messages")
        }
        return (0 until list.length()).map { list.get(it).toString() }
    }

    /** The JSON in [answer]: from its first `[` or `{` to the last `]` or `}` that closes it. */
    internal fun jsonPart(answer: String): String? {
        val from = answer.indexOfFirst { it == '[' || it == '{' }
        if (from < 0) return null
        val close = if (answer[from] == '[') ']' else '}'
        val to = answer.lastIndexOf(close)
        return if (to > from) answer.substring(from, to + 1) else null
    }

    /** The surfaces an answer creates and the ones it changes, by id, in the order they come. */
    fun surfaces(messages: List<String>): Pair<List<String>, List<String>> {
        val created = mutableListOf<String>()
        val changed = mutableListOf<String>()
        for (m in messages) {
            val o = runCatching { JSONObject(m) }.getOrNull() ?: continue
            o.optJSONObject("createSurface")?.optString("surfaceId")?.takeIf { it.isNotEmpty() }?.let { created += it }
            for (k in listOf("updateComponents", "updateDataModel")) {
                o.optJSONObject(k)?.optString("surfaceId")?.takeIf { it.isNotEmpty() }?.let { if (it !in created && it !in changed) changed += it }
            }
        }
        return created to changed
    }
}
