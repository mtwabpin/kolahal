package com.mtwabpin.kolahal.ui

import android.content.Context
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptEngine
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.ui.DocsExamples.KEEPS_SHOWN
import com.mtwabpin.kolahal.ui.reference.ReferenceExamples
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import kotlinx.coroutines.runBlocking

/**
 * The examples of the scripting guide (site/src/content/docs/scripting/api.mdx) that the website
 * shows as pictures. Each [code] is the guide's Lua code, as it is there: change both together.
 * The widget examples are the editor reference's ([ReferenceExamples]), and the picture example.
 * [before] is code run first that the guide leaves out, such as a stand-in for the network.
 */
object DocsExamples {
    class Widget(
        val name: String,
        val kind: WidgetKind,
        val size: WidgetSize,
        /** The script's @title: the card's title when the code gives none. */
        val title: String,
        val code: String,
        val before: String = "",
        val options: Map<String, String> = emptyMap(),
    )

    class Log(val name: String, val code: String, val before: String = "")

    /** The picture link of the image example. The gallery puts a picture there, so nothing is fetched. */
    const val PICTURE = "https://example.com/pictures/1432.jpg"

    /** The xkcd link of the log example's picture line. The gallery puts a drawing there. */
    const val COMIC = "https://imgs.xkcd.com/comics/exploits_of_a_mom.png"

    /**
     * What an example needs before its code, to run here: Do Not Disturb on, five glasses of water so
     * far, some reminders on, the porch light on.
     */
    val luaBefore = mapOf(
        "toggles" to "device = { dnd = function() return \"priority\" end, setDnd = function() end }\n",
        "water" to "store.set(os.date(\"%Y-%m-%d\"), \"5\")\n",
        "toggles-2" to "store.set(\"Morning\", \"on\")\n",
        "toggles-4" to "store.set(\"Morning\", \"on\"); store.set(\"Evening\", \"on\")\n",
        "toggles-icons" to "store.set(\"Porch\", \"on\")\n",
        "toggles-values" to "store.set(\"Heating\", \"on\")\n",
    )

    /** [luaBefore] for the JavaScript code. */
    val jsBefore = mapOf(
        "toggles" to "const device = { dnd: () => \"priority\", setDnd: () => {} };\n",
        "water" to "{ const d0 = new Date(); store.set(d0.getFullYear() + \"-\" + (d0.getMonth() + 1) + \"-\" + d0.getDate(), \"5\"); }\n",
        "toggles-2" to "store.set(\"Morning\", \"on\");\n",
        "toggles-4" to "store.set(\"Morning\", \"on\"); store.set(\"Evening\", \"on\");\n",
        "toggles-icons" to "store.set(\"Porch\", \"on\");\n",
        "toggles-values" to "store.set(\"Heating\", \"on\");\n",
    )

    /** The options an example's code reads. */
    val optionsOf = mapOf("water" to mapOf("goal" to "8"))

    /**
     * The widget examples: the editor reference's ([ReferenceExamples]), with their Lua code, and
     * the picture example, which the reference leaves out because drawing it fetches.
     */
    val widgets: List<Widget> = ReferenceExamples.widgets.map { ex ->
        Widget(
            ex.name,
            ex.kind,
            ex.size,
            ex.title,
            ex.lua,
            before = luaBefore[ex.name].orEmpty(),
            options = optionsOf[ex.name].orEmpty()
        )
    } + listOf(
        Widget(
            "image", WidgetKind.IMAGE, WidgetSize.S4X2, "Picture of the day",
            """
            local res = http.cached("https://example.com/today.json", 3600)
            local today = json.decode(res.body)
            widget.show{
              kind = "image",
              image = today.url,                 -- "https://example.com/pictures/1432.jpg"
              alt = today.description,           -- "Hills at sunrise"
              caption = today.title,
              tap = { url = today.page, label = "open the page" },
            }
            """,
            before = "http = { cached = function() return { ok = true, status = 200, body = [[" +
                    """{"url":"$PICTURE","description":"Hills at sunrise","title":"Sunrise over the ghats","page":"https://example.com/pictures/1432"}""" +
                    "]] } end }\n",
        ),
    )

    /** The rest of the guide's first log example. */
    private val LOG_REST = """
        log("xkcd #327", { image = "https://imgs.xkcd.com/comics/exploits_of_a_mom.png" })
        print("print() also logs", 42)   -- arguments joined with tabs
    """.trimIndent()

    /**
     * The log examples: the editor reference's ([ReferenceExamples]), with their Lua code. The first
     * one goes on as the guide's does, with a picture line and `print`, which the reference leaves out.
     */
    val logs: List<Log> = ReferenceExamples.logs.map { ex ->
        val code = ex.lua.trimIndent() + if (ex.name == "log") "\n" + LOG_REST else ""
        Log(ex.name, code)
    }

    /** Keeps what the code hands to `widget.show` in the script's store, as JSON, and passes it on. */
    private const val KEEPS_SHOWN =
        "do local show = widget.show; widget = { show = function(d) store.set('_shown', json.encode(d)); return show(d) end } end\n"

    /** [KEEPS_SHOWN] for JavaScript. */
    private const val JS_KEEPS_SHOWN =
        "const widget = { show: (d) => { store.set('_shown', JSON.stringify(d)); return globalThis.widget.show(d); } };\n"

    /**
     * Runs [ex]'s code as a script with its @widget line, and returns its card as `widget.show`
     * checks it. The widget isn't placed: nothing is stored but the script's own store, which is
     * emptied after.
     */
    fun spec(context: Context, ex: Widget): WidgetSpec = shown(
        context,
        ex.name,
        ex.kind,
        ex.size,
        ScriptLang.LUA,
        ex.before + ex.code.trimIndent(),
        ex.options
    )

    /**
     * Runs [code] in [lang] as a script with a `@widget` line of [kind] and [size], and returns the
     * card it handed `widget.show`, as `widget.show` checks it.
     */
    fun shown(
        context: Context,
        name: String,
        kind: WidgetKind,
        size: WidgetSize,
        lang: ScriptLang,
        code: String,
        options: Map<String, String> = emptyMap()
    ): WidgetSpec {
        val id = 910_000_000L + System.nanoTime() % 1_000_000
        val store = KvStore(context, ScriptRepository.scopeOf(id))
        try {
            val host = HostApi(
                context,
                "docs-$name",
                scriptId = id,
                notifyEnabled = false,
                options = options,
                widget = ScriptMeta.Widget(kind, size)
            )
            val keeps = if (lang == ScriptLang.LUA) KEEPS_SHOWN else JS_KEEPS_SHOWN
            runBlocking {
                ScriptEngine.forLang(lang)
                    .run(keeps + code, "docs-$name", host, 10_000, false, emptyList(), null)
            }
            host.flush()
            val json =
                checkNotNull(store.get("_shown")) { "$name ($lang): the code called no widget.show" }
            return WidgetParser.parse(
                Json.parse(json) as Map<*, *>,
                kind,
                text = { it },
                link = { it })
        } finally {
            store.all().keys.forEach { store.set(it, null) }
        }
    }
}
