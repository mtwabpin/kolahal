/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize

/**
 * The script API as data. The editor uses it for completion, inline docs and the reference page.
 * Keep it in sync with the bindings in [LuaEngine] and [JsEngine]. ApiDocsTest checks the names.
 */
data class ApiDoc(
    /** Full dotted name, e.g. `http.get`. */
    val name: String,
    val signature: String,
    val doc: String,
    val langs: Set<ScriptLang> = BOTH,
    val kind: Kind = Kind.FUNCTION,
) {
    enum class Kind { FUNCTION, NAMESPACE, VALUE, KEYWORD }

    /** The namespace part (`http` for `http.get`), or null for globals. */
    val qualifier: String? get() = name.substringBeforeLast('.', "").ifEmpty { null }

    /** The last segment (`get` for `http.get`). */
    val member: String get() = name.substringAfterLast('.')

    private companion object {
        val BOTH = setOf(ScriptLang.LUA, ScriptLang.JS)
    }
}

object ApiDocs {
    private val LUA = setOf(ScriptLang.LUA)
    private val JS = setOf(ScriptLang.JS)
    private val NS = ApiDoc.Kind.NAMESPACE
    private val VALUE = ApiDoc.Kind.VALUE

    /**
     * What `widget.show` does, the same words for Lua and JavaScript. The editor shows the start
     * on one line, so what the call does and returns comes first. Then comes `kind`, which a script
     * may leave out: the parser ([com.mtwabpin.kolahal.widgets.WidgetParser.parse]) then takes the
     * kind of the `@widget` line. The fields of each kind come last.
     */
    private val WIDGET_SHOW =
        "Fill this script's widget (it needs an @widget line); true when you placed it, false when it was only checked. " +
                "kind is the card to draw; without it, the kind on the @widget line. " +
                WidgetKind.forScripts.joinToString(" ") { "${it.id}: ${widgetFields(it)}." } + " " +
                "Every kind: title, icon, color, tap ({ args } runs this script again, { url } opens a link; label is what a screen reader says, " +
                "page = \"log\" turns home to the log after the run), staleAfter, refresh (seconds), note (a few words on a large card's last line). " +
                "An unknown field is an error"

    /** The fields of a [kind] widget, as the reference lists them. Only the kinds a script can use are asked for. */
    private fun widgetFields(kind: WidgetKind): String = when (kind) {
        WidgetKind.STAT -> "value, label, unit, delta, trend (up, down or flat), more, progress, moving"
        WidgetKind.TEXT -> "text, caption"
        WidgetKind.LIST -> "items (each { title, detail, trailing, icon, color, tap, done, range }; a range is { low, high, value }, drawn as a bar), empty, " +
                "lead ({ value, unit, label }, a big line over the rows)"

        WidgetKind.PROGRESS -> "value, max, text, label, style (ring or bar), moving, or items for bars (each { label, value, max, text, color })"
        WidgetKind.CHART -> "points, style (line, bars or dots), min, max, value, label, labels, summary"
        WidgetKind.COUNTDOWN -> "to, from (times in seconds), label, style (days or clock), reached"
        WidgetKind.BUTTONS -> "items (1 to 8, each { label, icon, color, tap })"
        WidgetKind.TOGGLES -> "items (1 to 6, each { label, icon, on, tap }; the tap is { args }, and the run shows the new state)"
        WidgetKind.CLOCK -> "zones (each { zone, label }; a zone is a name such as Asia/Kolkata), seconds"
        WidgetKind.AGENDA -> "events (each { title, starts, ends (times in seconds), allDay, where, color, tap }), days, empty"
        WidgetKind.IMAGE -> "image (an https link to a picture), alt (what it shows, in words), caption, fit (crop or fit)"
        WidgetKind.MEDIA -> ""
    }

    /** What `tty.screen` does, the same words for Lua and JavaScript: the editor shows the start on one line. */
    private val TTY_SCREEN =
        "Open a full-screen screen over home once the script has run, only when you start it. " +
                "Give it start(g) or frame(g, t, dt), or both. frame runs fps times a second " +
                "(0 to ${ScreenSession.MAX_FPS}, ${ScreenSession.DEFAULT_FPS} by default; with 0, only start, key and tap draw); " +
                "t and dt are milliseconds. key(k) gets each key (\"a\", \"up\", \"down\", \"left\", \"right\", \"enter\", \"space\", \"escape\", " +
                "\"tab\", \"delete\"), tap(col, row) each tap. resize(g) runs when the size changes, stop() when it closes. " +
                "A function that returns false closes the screen; without key a key closes it, without tap a tap does, and Back always does. " +
                "g.cols and g.rows are the size in cells, counted from 0. Draw with g.put(col, row, text, style?), g.fill(col, row, width, height, char?, style?), " +
                "g.clear(style?), g.box(col, row, width, height, style?) and g.bar(col, row, width, value, style?); style is a color role or " +
                "{ color, bg, bold, faint, italic, underline, strike, inverse }. Each call has ${ScreenSession.CALL_MS / 1000} s; " +
                "the screen stays for as long as you leave it open. A script with @uses screen is a command, with no timer, event or widget (needs @uses screen)"

    val all: List<ApiDoc> = listOf(
        ApiDoc(
            "log",
            "log(title, body?, ctx?) -> line",
            "Append a line to the terminal: the title bold, the body muted under it; ctx.url makes it a tappable link, ctx.copy is what Copy text copies, ctx.image shows an image under it, ctx.actions puts up to 3 buttons under it that run this script again, ctx.gap = true puts a small space above it to start a new item of a list, ctx.mono = \"1\" keeps it in the terminal font. line.set(title, body?, ctx?) changes it in place while the script runs"
        ),
        ApiDoc(
            "logs",
            "logs(titles)",
            "Append one title line per item: logs({ \"a\", \"b\" }) or logs(\"a\", \"b\")",
            LUA
        ),
        ApiDoc(
            "logs",
            "logs(titles)",
            "Append one title line per item: logs([\"a\", \"b\"]) or logs(\"a\", \"b\")",
            JS
        ),
        ApiDoc("md", "md", "Markdown lines: log, escape", kind = NS),
        ApiDoc(
            "md.log",
            "md.log(title, body?, ctx?) -> line",
            "Like log, reading Markdown: **bold**, *italic*, ~~strike~~, ++underline++, `code`, [text](https://…) links; lists (- item, 1. item) in the body"
        ),
        ApiDoc(
            "md.escape",
            "md.escape(text) -> string",
            "Text with Markdown marks escaped, to show web text literally in md.log"
        ),
        ApiDoc(
            "fmt",
            "fmt",
            "Styles for any log line: bold, italic, underline, strike, code, color, tag, link, list, table, bar, bars, ranges, stats, plain",
            kind = NS
        ),
        ApiDoc(
            "fmt.bold",
            "fmt.bold(text) -> string",
            "Text shown bold in the log; join it with other text"
        ),
        ApiDoc("fmt.italic", "fmt.italic(text) -> string", "Text shown in italics in the log"),
        ApiDoc(
            "fmt.underline",
            "fmt.underline(text) -> string",
            "Text shown underlined in the log"
        ),
        ApiDoc("fmt.strike", "fmt.strike(text) -> string", "Text shown struck through in the log"),
        ApiDoc(
            "fmt.code",
            "fmt.code(text) -> string",
            "Text shown as code (a shaded chip) in the log"
        ),
        ApiDoc(
            "fmt.color",
            "fmt.color(role, text) -> string",
            "Text in one of the theme's colors: accent, link, info, muted, text, good, warn, bad"
        ),
        ApiDoc(
            "fmt.list",
            "fmt.list(items, numbered?) -> string",
            "A list, one item per line, for a body: bullets, or 1., 2. … with numbered"
        ),
        ApiDoc(
            "fmt.table",
            "fmt.table(rows, {header?, align?}) -> string",
            "A table for a body: rows of cells; header names the columns; align is one letter a column, l or r"
        ),
        ApiDoc(
            "fmt.tag",
            "fmt.tag(role?, text) -> string",
            "A short label on a shaded chip in one of the theme's colors (accent without a role): LIVE, NEW, v1.2"
        ),
        ApiDoc(
            "fmt.link",
            "fmt.link(text, url) -> string",
            "Text that opens url when tapped; a line can have several. Web links only, like ctx.url"
        ),
        ApiDoc(
            "fmt.bar",
            "fmt.bar(value, {max?, color?}) -> string",
            "A bar as wide as the line, for a body: value out of max (1 by default), in a color role"
        ),
        ApiDoc(
            "fmt.bars",
            "fmt.bars(rows, {max?, color?, unit?}) -> string",
            "A bar for each row { label, value, text? }, all on one scale, for a body"
        ),
        ApiDoc(
            "fmt.ranges",
            "fmt.ranges(rows, {min?, max?, color?, unit?}) -> string",
            "A bar from low to high for each row { label, low, high, lowText?, highText? }, all on one scale, for a body: a forecast's days"
        ),
        ApiDoc(
            "fmt.stats",
            "fmt.stats(items) -> string",
            "A line of big numbers for a body, each item { value, label }: the first big, the rest smaller, each label under its number. Up to 5"
        ),
        ApiDoc(
            "fmt.plain",
            "fmt.plain(text) -> string",
            "The text without its styles, for anything other than the log"
        ),
        ApiDoc(
            "notify",
            "notify(title, body, ctx?) -> bool",
            "Post a notification if enabled for the script; ctx.url opens on tap"
        ),
        ApiDoc(
            "status",
            "status(text, opts?)",
            "This script's status line, in its card and status, and at the top of home once you pin the script; opts.progress (0 to 1) adds a bar there, opts.color (a role: good, warn, bad…) is the bar's color, opts.refresh (seconds, 10 at the fastest) runs it again that often while it shows; status(nil) or status(null) clears it"
        ),
        // Every `@uses` item is named, from the list itself: a new item can't be left out here.
        ApiDoc(
            "allowed",
            "allowed(what) -> bool",
            "Whether this script may use `what`: internet, header, widget (true once you placed its widget), ${
                ScriptMeta.USES.joinToString(", ")
            } or an @on event"
        ),
        ApiDoc(
            "widget",
            "widget",
            "This script's widget on the widgets page: show, clear",
            kind = NS
        ),
        ApiDoc("widget.show", "widget.show{ kind, title, … } -> bool", WIDGET_SHOW, LUA),
        ApiDoc("widget.show", "widget.show({ kind, title, … }) -> bool", WIDGET_SHOW, JS),
        ApiDoc(
            "widget.clear",
            "widget.clear()",
            "Remove the widget's content; its card then has nothing to show"
        ),
        ApiDoc("shader", "shader", "Values that shaders' parameters can follow: give", kind = NS),
        ApiDoc(
            "shader.give",
            "shader.give(name, value)",
            "Give a value under a name from @gives: a number from 0 to 1, or a word; nil/null takes it away. A shader's parameter follows it when you pick it there"
        ),
        ApiDoc("tty", "tty", "A full-screen screen of its own over home, drawn on the character grid: screen", kind = NS),
        ApiDoc("tty.screen", "tty.screen{ fps, title, start, frame, key, tap, resize, stop }", TTY_SCREEN, LUA),
        ApiDoc("tty.screen", "tty.screen({ fps, title, start, frame, key, tap, resize, stop })", TTY_SCREEN, JS),
        ApiDoc("sleep", "sleep(ms)", "Pause up to 5000 ms", LUA),
        ApiDoc("sleep", "await sleep(ms)", "Pause up to 5000 ms (async)", JS),
        ApiDoc("print", "print(...)", "Log the values joined by tabs", LUA),
        ApiDoc("args", "args", "Arguments when run as a command (list of strings)", kind = VALUE),
        ApiDoc("argline", "argline", "Arguments joined by spaces", kind = VALUE),
        ApiDoc(
            "event",
            "event",
            "What started this run, an @on event, an @at time, a button under its line or a tap on its widget (action), a pinned status line's refresh or the widgets page asking for new content (widget) (event.name, time, label, widget, instance, app, title, text, level…); nil/null otherwise",
            kind = VALUE
        ),
        ApiDoc(
            "options",
            "options",
            "Its @option values by name, as text: your value if set, otherwise the default (options.city)",
            kind = VALUE
        ),

        ApiDoc("http", "http", "HTTP client: get, cached, request", kind = NS),
        ApiDoc(
            "http.get",
            "http.get(url, headers?) -> { status, ok, body, headers }",
            "HTTP GET",
            LUA
        ),
        ApiDoc(
            "http.get",
            "await http.get(url, headers?) -> { status, ok, body, headers }",
            "HTTP GET (async)",
            JS
        ),
        ApiDoc(
            "http.cached",
            "http.cached(url, seconds?=600) -> response",
            "GET, reusing this script's stored response while fresh; adds `cached`",
            LUA
        ),
        ApiDoc(
            "http.cached",
            "await http.cached(url, seconds?=600) -> response",
            "GET, reusing this script's stored response while fresh; adds `cached`",
            JS
        ),
        ApiDoc(
            "http.request",
            "http.request{ url, method, body, contentType, headers } -> response",
            "Any method; a table body is sent as JSON",
            LUA
        ),
        ApiDoc(
            "http.request",
            "await http.request({ url, method, body, contentType, headers }) -> response",
            "Any method; an object body is sent as JSON",
            JS
        ),

        ApiDoc("rss", "rss", "Feeds: fetch, fresh, parse", kind = NS),
        ApiDoc(
            "rss.fetch",
            "rss.fetch(url) -> { title, link, items }",
            "Download and parse an RSS/Atom feed",
            LUA
        ),
        ApiDoc(
            "rss.fetch",
            "await rss.fetch(url) -> { title, link, items }",
            "Download and parse an RSS/Atom feed",
            JS
        ),
        ApiDoc(
            "rss.fresh",
            "rss.fresh(url) -> { title, link, items }",
            "Only items not returned before; marked seen if the run succeeds",
            LUA
        ),
        ApiDoc(
            "rss.fresh",
            "await rss.fresh(url) -> { title, link, items }",
            "Only items not returned before; marked seen if the run succeeds",
            JS
        ),
        ApiDoc(
            "rss.parse",
            "rss.parse(xml) -> { title, link, items = [{ title, link, id, date, summary }] }",
            "Parse feed XML"
        ),

        ApiDoc("store", "store", "Persistent per-script strings: get, set", kind = NS),
        ApiDoc("store.get", "store.get(key) -> string?", "Read a stored string"),
        ApiDoc("store.set", "store.set(key, value)", "Store a string; nil/null deletes it"),
        ApiDoc("secrets", "secrets", "API keys and tokens you set for this script: get", kind = NS),
        ApiDoc(
            "secrets.get",
            "secrets.get(name) -> string?",
            "A placeholder for the value you set for an @secret name, filled in only in requests to its site; nil/null until you set it"
        ),

        ApiDoc("clipboard", "clipboard", "Clipboard: get, set", kind = NS),
        ApiDoc(
            "clipboard.get",
            "clipboard.get() -> string?",
            "Clipboard text; usually nil/null in timers (needs @uses clipboard)"
        ),
        ApiDoc(
            "clipboard.set",
            "clipboard.set(text)",
            "Copy text to the clipboard (needs @uses clipboard)"
        ),

        ApiDoc(
            "device",
            "device",
            "Device info: battery, network, unlocks; Do Not Disturb, the work profile",
            kind = NS
        ),
        ApiDoc(
            "device.battery",
            "device.battery() -> { level, charging }",
            "Battery percent and charging state"
        ),
        ApiDoc(
            "device.network",
            "device.network() -> string",
            "\"wifi\", \"cell\", \"eth\", \"vpn\", \"net\" or \"offline\""
        ),
        ApiDoc(
            "device.unlocks",
            "device.unlocks(days?) -> number | list",
            "Today's unlock count; with days (1 to 30), a list of the counts of the last days instead, the oldest first and today's last"
        ),
        ApiDoc(
            "device.dnd",
            "device.dnd() -> string?",
            "Do Not Disturb: \"off\", \"priority\", \"alarms\" or \"none\" (needs @uses dnd)"
        ),
        ApiDoc(
            "device.setDnd",
            "device.setDnd(mode) -> bool",
            "Set Do Not Disturb to \"off\", \"on\" (or \"priority\"), \"alarms\" or \"none\"; false without Do Not Disturb access (needs @uses dnd)"
        ),
        ApiDoc(
            "device.work",
            "device.work() -> string?",
            "The work profile: \"on\" or \"paused\"; nil/null without one (needs @uses work)"
        ),
        ApiDoc(
            "device.setWork",
            "device.setWork(on) -> bool",
            "Resume (true or \"on\") or pause (false or \"off\") the work profile; false if Android refuses (needs @uses work)"
        ),

        ApiDoc("apps", "apps", "Installed apps: list", kind = NS),
        ApiDoc(
            "apps.list",
            "apps.list() -> [{ label, pkg, profile }]",
            "Launchable apps across profiles (needs @uses apps)"
        ),

        ApiDoc("notifications", "notifications", "Recent notifications: recent", kind = NS),
        ApiDoc(
            "notifications.recent",
            "notifications.recent(n?=20) -> [{ pkg, app, title, text, time }]",
            "Newest first, up to 50 (needs @uses notifications and notification access)"
        ),

        ApiDoc("todos", "todos", "Your to-do list: list, setDone", kind = NS),
        ApiDoc(
            "todos.list",
            "todos.list() -> [{ id, text, done }]",
            "Your todos, the open ones first, as the todo command lists them; up to 200 (needs @uses todos)"
        ),
        ApiDoc(
            "todos.setDone",
            "todos.setDone(id, done) -> bool",
            "Mark the todo with this id done (true) or open again (false); true when it changed; at most 20 per run (needs @uses todos)"
        ),
        ApiDoc("notes", "notes", "Your notes: list", kind = NS),
        ApiDoc(
            "notes.list",
            "notes.list(n?=10) -> [{ id, text, created }]",
            "Your notes, newest first, up to 50; created is a time in seconds (needs @uses notes)"
        ),
        ApiDoc("reminders", "reminders", "Your reminders: list", kind = NS),
        ApiDoc(
            "reminders.list",
            "reminders.list() -> [{ id, text, at, repeats }]",
            "The reminders that are set, soonest first; at is a time in seconds; repeats says how one repeats, and is nil/null for one that doesn't (needs @uses reminders)"
        ),
        ApiDoc("calendar", "calendar", "The phone's calendar: events", kind = NS),
        ApiDoc(
            "calendar.events",
            "calendar.events(days?=3) -> [{ id, title, starts, ends, allDay }]",
            "The events of the next days (1 to 30), up to 100, with times in seconds as an agenda widget takes them; empty without the calendar permission (needs @uses calendar)"
        ),
        ApiDoc("feeds", "feeds", "The feeds you follow: unread", kind = NS),
        ApiDoc(
            "feeds.unread",
            "feeds.unread(n?=10) -> [{ id, feed, title, link, date, summary }]",
            "The unread items of your feeds, newest first, up to 50, as Kolahal fetched them: no request is made, and nothing is marked read (needs @uses feeds)"
        ),

        ApiDoc("usage", "usage", "Your screen time, data use and unused apps", kind = NS),
        ApiDoc(
            "usage.today",
            "usage.today(n?=10) -> { total, apps = [{ app, pkg, minutes }] }",
            "Today's screen time in minutes since midnight, and the n apps used most, biggest first, up to 30 (needs @uses usage)"
        ),
        ApiDoc(
            "usage.days",
            "usage.days(n?=7) -> [minutes]",
            "The screen time of each of the last n days (1 to 30), oldest first, only the days Android has data for (needs @uses usage)"
        ),
        ApiDoc(
            "usage.data",
            "usage.data(since?) -> { mobile, wifi }",
            "The bytes of mobile and Wi-Fi data used since a time in seconds (by default, midnight); \"month\" gives your data plan's month instead, adding since, plan, pace and daysLeft; mobile is nil/null on Android 9 (needs @uses usage)"
        ),
        ApiDoc(
            "usage.unused",
            "usage.unused(days?=30) -> [{ app, pkg, days, bytes }]",
            "The apps not opened for days days or more (7 to 365), up to 50; days is nil when no use is recorded (needs @uses usage)"
        ),
        ApiDoc(
            "usage.summary",
            "usage.summary(period, app?) -> { perDay, rows = [{ from, to, minutes, perDay, unlocks, current }], top }",
            "Screen time over Android's weeks, months or years (\"week\", \"month\", \"year\"), all apps or one package, oldest first, as usage-summary shows it (needs @uses usage)"
        ),

        ApiDoc("launcher", "launcher", "Drive the launcher: run", kind = NS),
        ApiDoc(
            "launcher.run",
            "launcher.run(command) -> bool",
            "Run a terminal command (needs @uses commands); only when started from the prompt, at most 10 per run"
        ),

        ApiDoc("json", "json", "JSON: encode, decode", LUA, NS),
        ApiDoc(
            "json.encode",
            "json.encode(value) -> string",
            "Serialize a table/value to JSON",
            LUA
        ),
        ApiDoc("json.decode", "json.decode(text) -> value", "Parse JSON into tables", LUA),

        ApiDoc("console", "console", "console.log / console.error", JS, NS),
        ApiDoc("console.log", "console.log(...values)", "Log the values joined by spaces", JS),
        ApiDoc("console.error", "console.error(...values)", "Log the values as an error line", JS),
    )

    val luaKeywords = listOf(
        "and",
        "break",
        "do",
        "else",
        "elseif",
        "end",
        "false",
        "for",
        "function",
        "goto",
        "if",
        "in",
        "local",
        "nil",
        "not",
        "or",
        "repeat",
        "return",
        "then",
        "true",
        "until",
        "while",
    )

    val jsKeywords = listOf(
        "async",
        "await",
        "break",
        "case",
        "catch",
        "const",
        "continue",
        "default",
        "do",
        "else",
        "false",
        "finally",
        "for",
        "function",
        "if",
        "in",
        "let",
        "new",
        "null",
        "of",
        "return",
        "switch",
        "this",
        "throw",
        "true",
        "try",
        "typeof",
        "undefined",
        "var",
        "while",
    )

    fun keywords(lang: ScriptLang) = if (lang == ScriptLang.LUA) luaKeywords else jsKeywords

    private val byLang: Map<ScriptLang, List<ApiDoc>> = ScriptLang.entries.associateWith { lang ->
        all.filter { lang in it.langs } +
                keywords(lang).map { ApiDoc(it, it, "keyword", setOf(lang), ApiDoc.Kind.KEYWORD) }
    }

    private val byName: Map<ScriptLang, Map<String, ApiDoc>> =
        byLang.mapValues { (_, docs) ->
            docs.filter { it.kind != ApiDoc.Kind.KEYWORD }.associateBy { it.name }
        }

    /** API entries and keywords available in [lang]. */
    fun forLang(lang: ScriptLang): List<ApiDoc> = byLang.getValue(lang)

    /** The API entry called [name] in [lang]. Keywords are not included. */
    fun find(name: String, lang: ScriptLang): ApiDoc? = byName.getValue(lang)[name]

    fun isNamespace(name: String, lang: ScriptLang) =
        find(name, lang)?.kind == ApiDoc.Kind.NAMESPACE

    /** A comment line at the top of a script ([ScriptMeta]). [args] is the text after its name, or empty if there is none. */
    data class Directive(val name: String, val args: String, val doc: String)

    /** Every `@` line, for the editor's reference. Each line says what the script is, how it runs or what it may use. */
    val directives: List<Directive> = listOf(
        Directive("command", "", "Run it by its name from the prompt"),
        Directive("desc", "<text>", "One-line description, in help, suggestions and Scripts"),
        Directive("usage", "[<args>]", "Its arguments, like a usage line in help: [<city>]"),
        Directive(
            "widget", "<kind> [<size>]",
            "It has a widget for the widgets page, filled with widget.show. Kinds: " + WidgetKind.forScripts.joinToString(
                " "
            ) { it.id } +
                    ". Sizes: " + WidgetSize.entries.joinToString(" ") { it.id },
        ),
        Directive(
            "on",
            "<event>, …",
            "Run on these events: " + ScriptMeta.EVENTS.joinToString(" ")
        ),
        Directive(
            "uses",
            "<what>, …",
            "What it may use, if you allow it: " + ScriptMeta.USES.joinToString(" ")
        ),
        Directive(
            "option",
            "<name> <default> <description>",
            "A setting you change without editing the code, read as options.<name>; a default of a|b|c offers a choice"
        ),
        Directive(
            "secret",
            "<NAME> <site> <description>",
            "A key you set in its sheet, never in the code; read with secrets.get, sent only to that site"
        ),
        Directive("gives", "<name>, …", "The values it gives with shader.give, for shaders' parameters to follow"),
        Directive("every", "<15m|1h|1d>", "The timer it suggests, set when it is added"),
        Directive("at", "<HH:mm>, …", "The daily times it suggests"),
        Directive("notify", "", "Suggests allowing notifications"),
        Directive("title", "<text>", "Display name, shown in Scripts"),
        Directive("author", "<name>", "Who wrote it"),
        Directive("version", "<x.y>", "Raise it when sharing an update"),
        Directive("tags", "<word>, …", "Search keywords"),
        Directive("requires", "<x.y.z>", "Minimum Kolahal version"),
    )
}
