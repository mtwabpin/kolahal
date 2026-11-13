package com.mtwabpin.kolahal.script

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.mtwabpin.kolahal.data.StyledText

/**
 * QuickJS through quickjs-kt. Network helpers are `async`, so scripts can `await` them (and use
 * `Promise.all` for parallel fetches). Only the bindings below are exposed. QuickJS has no file,
 * process or network access of its own.
 */
class JsEngine : ScriptEngine {

    override suspend fun run(
        code: String,
        chunkName: String,
        host: HostApi,
        timeoutMs: Long,
        repl: Boolean,
        args: List<String>,
        event: Map<*, *>?,
    ): String? {
        val js = QuickJs.create(Dispatchers.IO)
        host.startClock(timeoutMs)
        // QuickJS stops a run at its timeout only at points where the bytecode checks. Some built-ins
        // never check (Array.prototype.includes over 2^53 slots). Promise jobs that queue one another
        // stop at the timeout since quickjs-kt 1.0.17; before, only the watchdog stopped them. The
        // watchdog has the last word.
        val watchdog = RunWatchdog(host, timeoutMs, nativeLimitMb = null)
        try {
            js.memoryLimit = MEMORY_LIMIT
            js.evaluationTimeoutMillis = timeoutMs
            bindHost(js, host)
            // Scripts may `return` (see below). The value comes back through a binding, because evaluate
            // does not unwrap returned promises.
            var result: Any? = null
            js.function("__kolahal_done") { args -> result = args.getOrNull(0) }
            js.evaluate<Any?>(
                "globalThis.args = ${Json.stringify(args)}; globalThis.argline = ${Json.stringify(args.joinToString(" "))};" +
                    " globalThis.event = ${Json.stringify(event)}; globalThis.options = ${Json.stringify(host.options)};",
                filename = "args.js",
            )
            js.evaluate<Any?>(GUARD, filename = "guard.js")
            // A REPL line that ends with log(…) has its line's handle as its value, which reads as {}: it prints nothing.
            if (repl) return js.evaluate<Any?>(code, filename = "$chunkName.js")?.takeUnless { it is Map<*, *> && it.isEmpty() && host.effects > 0 }?.let(::render)
            // Wrapped so that scripts may `return`. Top-level await makes evaluate() wait until the script completes.
            js.evaluate<Any?>("__kolahal_done(__kolahal_out(await (async () => {\n$code\n})()));", filename = "$chunkName.js")
            return result?.let(::render)
        } finally {
            // Finish first. Closing cancels the jobs still waiting, but a request that one of them made
            // (a call not awaited) answers later on its own thread, and must change nothing then.
            host.finish()
            // Closing waits for the engine. A run stuck in a job loop never gets past it, so the
            // watchdog, still running, stops it.
            watchdog.use { js.close() }
        }
    }

    private fun bindHost(js: QuickJs, host: HostApi) {
        /** A call that waits, in one of [httpSlots]. What it throws is masked, as the script may see it. */
        suspend fun <T> io(block: () -> T): T = httpSlots.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    block()
                } catch (e: Exception) {
                    throw host.masked(e)
                }
            }
        }

        // log and md.log return the line's number; guard.js turns it into the line's handle (line.set).
        js.function("log") { args ->
            val (title, body, ctx) = logArgs(args)
            host.log(title, body, ctx).takeIf { it > 0 }
        }
        js.function("__kolahal_set") { args ->
            val number = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val (title, body, ctx) = logArgs(args.drop(2).toTypedArray())
            host.setLine(number, markdown = args.getOrNull(1) == true, title, body, ctx)
        }
        js.function("logs") { args ->
            // logs(["a", "b"]) or logs("a", "b"): one title line for each.
            val list = args.singleOrNull() as? List<*> ?: args.toList()
            host.logs(list.map(::render))
        }
        js.function("status") { args -> host.status(args.str(0), args.getOrNull(1) as? Map<*, *>) }
        js.function("allowed") { args -> host.allowed(args.str(0).orEmpty()) }
        js.function("notify") { args ->
            host.notify(args.str(0) ?: "", args.str(1) ?: "", args.getOrNull(2) as? Map<*, *>)
        }
        js.define("console") {
            function("log") { args -> host.log(args.joinToString(" ") { render(it) }); Unit }
            function("error") { args -> host.logError(args.joinToString(" ") { render(it) }) }
        }
        js.define("http") {
            asyncFunction("get") { args ->
                io { toJs(host.httpGet(args.req(), args.getOrNull(1) as? Map<*, *>)) }
            }
            asyncFunction("cached") { args ->
                io { toJs(host.httpCached(args.req(), (args.getOrNull(1) as? Number)?.toInt() ?: 600)) }
            }
            asyncFunction("request") { args ->
                val opts = args.getOrNull(0) as? Map<*, *>
                    ?: throw IllegalArgumentException("http.request expects an options object")
                io { toJs(host.httpRequest(opts)) }
            }
        }
        js.define("clipboard") {
            function("get") { host.clipboardGet() }
            function("set") { args -> host.clipboardSet(args.req()) }
        }
        js.define("device") {
            function("battery") { toJs(host.battery()) }
            function("network") { host.network() }
            function("unlocks") { host.unlocksToday() }
            function("dnd") { host.dnd() }
            function("setDnd") { args -> host.setDnd(args.req()) }
            function("work") { host.work() }
            function("setWork") { args -> host.setWork(host.onOff(args.getOrNull(0), "device.setWork")) }
        }
        js.define("apps") { function("list") { toJs(host.appsList()) } }
        js.define("notifications") { function("recent") { args -> toJs(host.notificationsRecent((args.getOrNull(0) as? Number)?.toInt() ?: 20)) } }
        js.define("launcher") { function("run") { args -> host.runCommand(args.req()) } }
        js.asyncFunction("sleep") { args -> host.sleepAsync((args.getOrNull(0) as? Number)?.toLong() ?: 0L) }
        js.define("rss") {
            asyncFunction("fetch") { args -> io { toJs(host.rssFetch(args.req())) } }
            asyncFunction("fresh") { args -> io { toJs(host.rssFresh(args.req())) } }
            function("parse") { args -> toJs(host.rssParse(args.req())) }
        }
        js.define("store") {
            function("get") { args -> host.storeGet(args.req()) }
            function("set") { args -> host.storeSet(args.req(), args.str(1)) }
        }
        js.define("md") {
            function("log") { args -> logArgs(args).let { (title, body, ctx) -> host.logMarkdown(title, body, ctx).takeIf { it > 0 } } }
            function("escape") { args -> Markdown.escape(args.str(0).orEmpty()) }
        }
        js.define("fmt") {
            function("bold") { args -> StyledText.wrap(StyledText.Style.BOLD, args.str(0) ?: "undefined") }
            function("italic") { args -> StyledText.wrap(StyledText.Style.ITALIC, args.str(0) ?: "undefined") }
            function("underline") { args -> StyledText.wrap(StyledText.Style.UNDERLINE, args.str(0) ?: "undefined") }
            function("strike") { args -> StyledText.wrap(StyledText.Style.STRIKE, args.str(0) ?: "undefined") }
            function("code") { args -> StyledText.wrap(StyledText.Style.CODE, args.str(0) ?: "undefined") }
            function("color") { args ->
                val role = args.str(0)
                val colour = role?.let(StyledText.Colour::named)
                    ?: throw IllegalArgumentException("fmt.color: no colour '$role'; use ${StyledText.Colour.entries.joinToString { it.role }}")
                StyledText.colour(colour, args.str(1) ?: "undefined")
            }
            function("list") { args ->
                val items = args.getOrNull(0) as? List<*> ?: throw IllegalArgumentException("fmt.list expects an array of items")
                StyledText.list(items.map(::render), numbered = args.getOrNull(1) == true)
            }
            function("table") { args ->
                val bad = "fmt.table expects an array of rows, each an array of cells"
                val rows = (args.getOrNull(0) as? List<*>)?.map { r -> (r as? List<*>)?.map { c -> if (c == null) "" else render(c) } ?: throw IllegalArgumentException(bad) }
                    ?: throw IllegalArgumentException(bad)
                val opts = args.getOrNull(1) as? Map<*, *>
                StyledText.table(rows, header = opts?.get("header") == true, align = opts?.get("align")?.toString().orEmpty())
            }
            val fmt = Fmt(::render)
            function("tag") { args -> fmt.tag(args.toList()) }
            function("link") { args -> fmt.link(args.toList()) }
            function("bar") { args -> fmt.bar(args.toList()) }
            function("bars") { args -> fmt.bars(args.toList()) }
            function("ranges") { args -> fmt.ranges(args.toList()) }
            function("stats") { args -> fmt.stats(args.toList()) }
            function("plain") { args -> StyledText.plain(args.str(0).orEmpty()) }
        }
        js.define("secrets") {
            function("get") { args -> host.secretGet(args.req()) }
        }
    }

    /** Network calls in flight for this run. With `Promise.all` over hundreds of requests, each waits its turn. */
    private val httpSlots = Semaphore(4)


    /** `(title, body?, ctx?)`, as log and md.log take them. If the second argument is an object, it is the ctx: log(title, ctx). */
    private fun logArgs(args: Array<Any?>): Triple<String, String?, Map<*, *>?> {
        val second = args.getOrNull(1)
        return if (second is Map<*, *>) Triple(args.str(0) ?: "undefined", null, second)
        else Triple(args.str(0) ?: "undefined", second?.let(::render), args.getOrNull(2) as? Map<*, *>)
    }

    private fun Array<Any?>.str(i: Int): String? = getOrNull(i)?.let { it as? String ?: render(it) }

    private fun Array<Any?>.req(): String =
        str(0) ?: throw IllegalArgumentException("argument 1 is required")

    /** Kotlin maps must be wrapped as [JsObject] to arrive as plain JS objects (not `Map`). */
    private fun toJs(v: Any?): Any? = when (v) {
        is JsObject -> v
        is Map<*, *> -> v.entries.associate { (k, value) -> k.toString() to toJs(value) }.toJsObject()
        is Collection<*> -> v.map(::toJs)
        else -> v
    }

    private fun render(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is Map<*, *>, is Collection<*> -> Json.stringify(v)
        is Double -> if (v % 1.0 == 0.0 && v in -9.0e15..9.0e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private companion object {
        const val MEMORY_LIMIT = 64L * 1024 * 1024

        /**
         * Runs before the script. Every host function takes a plain copy of its arguments. The copy is
         * made here in JavaScript, where QuickJS watches the stack and the clock. The copy:
         * - is at most 64 levels deep. The native conversion has no depth check, so a value nested
         *   10^5 deep would overflow the thread's stack and crash the :script process.
         * - has no string over 4 MB, and no array or object over 100,000 entries. Copies on the Kotlin
         *   side don't count toward the memory cap.
         * - holds no getter or proxy that could change between the check and the conversion.
         * Calls that wait (requests, sleep) are capped at 64 in flight. Everything the guard uses is
         * taken now, so a script that later replaces Array.prototype or Function.prototype.apply
         * can't switch the guard off.
         */
        val GUARD = """
            (() => {
              const MAX_DEPTH = 64, MAX_STRING = 4 * 1024 * 1024, MAX_ENTRIES = 100000, MAX_WAITING = 64;
              const keys = Object.keys, names = Object.getOwnPropertyNames, describe = Object.getOwnPropertyDescriptor;
              const define = Object.defineProperty, isArray = Array.isArray;
              const apply = Reflect.apply, RangeErr = RangeError;
              const put = (o, k, value) => define(o, k, { value, writable: true, enumerable: true, configurable: true });
              const copy = (v, depth) => {
                if (typeof v === "string") {
                  if (v.length > MAX_STRING) throw new RangeErr("a string over 4 MB");
                  return v;
                }
                if (v === null || typeof v !== "object") return v;
                if (depth > MAX_DEPTH) throw new RangeErr("a value nested over 64 levels deep, or one that holds itself");
                if (isArray(v)) {
                  const n = v.length;
                  if (n > MAX_ENTRIES) throw new RangeErr("an array over 100,000 items");
                  const out = [];
                  for (let i = 0; i < n; i++) put(out, i, copy(v[i], depth + 1));
                  return out;
                }
                const ks = keys(v);
                if (ks.length > MAX_ENTRIES) throw new RangeErr("an object over 100,000 keys");
                const out = {};
                for (let i = 0; i < ks.length; i++) put(out, ks[i], copy(v[ks[i]], depth + 1));
                return out;
              };
              const copyAll = (a) => {
                const out = [];
                for (let i = 0; i < a.length; i++) put(out, i, copy(a[i], 1));
                return out;
              };
              let waiting = 0;
              const guard = (f, waits) => {
                if (typeof f !== "function") return f;
                if (!waits) return (...a) => apply(f, undefined, copyAll(a));
                return async (...a) => {
                  const args = copyAll(a);
                  if (waiting >= MAX_WAITING) throw new RangeErr("more than 64 requests or sleeps at once");
                  waiting++;
                  try { return await apply(f, undefined, args); } finally { waiting--; }
                };
              };
              // Bindings come read-only (and a namespace's not enumerable): each is defined over, keeping
              // its flags, and checked, so a guard that didn't take fails loudly instead of quietly.
              const install = (o, k, waits) => {
                const d = describe(o, k);
                if (!d || typeof d.value !== "function") return;
                const wrapped = guard(d.value, waits);
                define(o, k, { value: wrapped, writable: d.writable, enumerable: d.enumerable, configurable: d.configurable });
                if (o[k] !== wrapped) throw new Error("guard.js: could not wrap " + k);
              };
              const g = globalThis;
              for (const name of ["log", "logs", "status", "allowed", "notify", "__kolahal_done", "__kolahal_set"]) install(g, name, false);
              install(g, "sleep", true);
              const spaces = ["console", "http", "clipboard", "device", "apps", "notifications", "launcher", "rss", "store", "md", "fmt", "secrets"];
              for (const ns of spaces) {
                const o = g[ns];
                const ks = names(o);
                for (let i = 0; i < ks.length; i++) install(o, ks[i], ns === "http" || (ns === "rss" && ks[i] !== "parse"));
              }
              // log and md.log return the line they logged, as a handle: line.set(title, body?, ctx?)
              // changes it in place. A handle has no properties of its own (set is on its prototype,
              // its line in a WeakMap), so a REPL line `log("x")` reads it as {} and prints nothing,
              // and a script's result that is a handle isn't logged.
              const setLine = g.__kolahal_set, lines = new WeakMap(), freeze = Object.freeze, create = Object.create, TypeErr = TypeError;
              const proto = freeze({
                set(...a) {
                  const line = lines.get(this);
                  if (!line) throw new TypeErr("set changes a line that log returned: line.set(title, body)");
                  setLine(line[0], line[1], ...a);
                  return this;
                },
              });
              const handle = (n, md) => {
                if (typeof n !== "number") return undefined;
                const h = freeze(create(proto));
                lines.set(h, [n, md]);
                return h;
              };
              const wrap = (o, k, md) => {
                const d = describe(o, k), raw = d.value;
                define(o, k, { value: (...a) => handle(raw(...a), md), writable: d.writable, enumerable: d.enumerable, configurable: d.configurable });
              };
              wrap(g, "log", false);
              wrap(g.md, "log", true);
              define(g, "__kolahal_out", { value: (v) => (v !== null && typeof v === "object" && lines.has(v) ? undefined : v) });
            })();
        """.trimIndent()
    }
}
