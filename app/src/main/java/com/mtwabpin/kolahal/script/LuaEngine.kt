package com.mtwabpin.kolahal.script

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import party.iroiro.luajava.JFunction
import party.iroiro.luajava.Lua
import party.iroiro.luajava.lua54.Lua54
import com.mtwabpin.kolahal.data.StyledText

/**
 * Native Lua 5.4 through LuaJava. Each run gets a fresh, sandboxed state with no `java` bridge,
 * `io`, `package`/`require`, `debug`, or process-level `os` functions.
 * An instruction-count hook enforces the timeout and coroutine cancellation.
 */
class LuaEngine : ScriptEngine {

    override suspend fun run(
        code: String,
        chunkName: String,
        host: HostApi,
        timeoutMs: Long,
        repl: Boolean,
        args: List<String>,
        event: Map<*, *>?,
    ): String? = withContext(Dispatchers.IO) {
        val ctx = coroutineContext
        val deadline = System.currentTimeMillis() + timeoutMs
        val lua = Lua54()
        host.startClock(timeoutMs)
        // The hook cannot interrupt long-running C functions (e.g. pathological string.find patterns), and
        // withContext cannot cancel blocking JNI. As a last resort, the watchdog kills the isolated :script process.
        // The hook checks memory every thousand instructions. But a few huge allocations outrun any count
        // (`s = s .. s` in a loop doubles a string every few instructions). So the watchdog also stops a
        // run whose native memory grows past NATIVE_LIMIT_MB. It checks often enough to overshoot by about
        // one doubling. LuaJava gives no control over Lua's allocator itself.
        val watchdog = RunWatchdog(host, timeoutMs, NATIVE_LIMIT_MB)
        try {
            lua.openLibraries()
            lua.push(guarded("script stopped") { l ->
                if (!ctx.isActive || System.currentTimeMillis() > deadline) {
                    l.push("script timed out after ${timeoutMs / 1000}s")
                    -1
                } else 0
            })
            lua.setGlobal("__kolahal_check")
            bindHost(lua, host)
            lua.push(code)
            lua.setGlobal("__kolahal_src")
            lua.push(chunkName)
            lua.setGlobal("__kolahal_name")
            lua.push(args)
            lua.setGlobal("args")
            lua.push(args.joinToString(" "))
            lua.setGlobal("argline")
            lua.pushValue(event)
            lua.setGlobal("event")
            lua.pushValue(host.options)
            lua.setGlobal("options")
            lua.run(PRELUDE)

            lua.getGlobal("__kolahal_main")
            lua.push(repl)
            lua.pCall(1, 1)
            lua.toString(-1)
        } finally {
            // Close while the watchdog still runs. lua_close runs pending __gc finalizers, where the
            // instruction hook is off, so only the watchdog can stop a looping finalizer. Finalizers
            // can't call the host, because LuaJava releases the state before closing it.
            watchdog.use { lua.close() }
        }
    }

    /**
     * [fn] as a Lua function that never throws. An exception that left it would reach the script as a
     * Java object (LuaJava keeps it in `__jthrowable__`), with Java's reflection behind it. So an error
     * becomes a Lua error: the text [message] gives, or [fallback] when even that fails.
     */
    private fun guarded(fallback: String, message: (Throwable) -> String = { fallback }, fn: (Lua) -> Int) = JFunction { l ->
        try {
            fn(l)
        } catch (e: Throwable) {
            val text = try {
                message(e)
            } catch (_: Throwable) {
                fallback
            }
            try {
                l.push(text)
            } catch (_: Throwable) {
                // No room for the message: the error carries whatever is on top, never a Java object.
            }
            -1
        }
    }

    private fun bindHost(lua: Lua, host: HostApi) {
        /** Turns Kotlin exceptions into Lua errors instead of letting them cross JNI. They are masked, as the script may see them. */
        fun safe(fn: (Lua) -> Int) = guarded("host error", { e -> host.redact(e.message ?: e.javaClass.simpleName) }, fn)

        fun Lua.global(name: String, fn: (Lua) -> Int) {
            push(safe(fn))
            setGlobal(name)
        }

        fun Lua.table(name: String, vararg fns: Pair<String, (Lua) -> Int>) {
            newTable()
            for ((key, fn) in fns) {
                push(safe(fn))
                setField(-2, key)
            }
            setGlobal(name)
        }

        // log and md.log return the line's number; the prelude turns it into the line's handle (line.set).
        lua.global("log") { l ->
            val (title, body, ctx) = l.logArgs()
            l.pushLine(host.log(title, body, ctx))
            1
        }
        lua.global("__kolahal_set") { l ->
            val number = if (l.isNumber(1)) l.toInteger(1).toInt() else 0
            val (title, body, ctx) = l.logArgs(from = 3)
            host.setLine(number, markdown = l.toBoolean(2), title, body, ctx)
            0
        }
        lua.global("logs") { l ->
            // logs({ "a", "b" }) or logs("a", "b"): one title line for each.
            val titles = if (l.isTable(1)) {
                // An empty table counts as an empty list, because it has no keys to tell.
                when (val t = Json.normalizeLua(l.toMap(1))) {
                    is List<*> -> t.map { it.toString() }
                    is Map<*, *> -> if (t.isEmpty()) emptyList() else throw IllegalArgumentException("logs expects a list of titles")
                    else -> throw IllegalArgumentException("logs expects a list of titles")
                }
            } else (1..l.top).map { l.str(it) ?: "nil" }
            host.logs(titles)
            0
        }
        lua.global("status") { l ->
            host.status(l.str(1), l.mapArg(2))
            0
        }
        lua.global("allowed") { l ->
            l.push(host.allowed(l.str(1).orEmpty()))
            1
        }
        lua.global("notify") { l ->
            l.push(host.notify(l.str(1) ?: "", l.str(2) ?: "", l.mapArg(3)))
            1
        }
        lua.table(
            "http",
            "get" to { l: Lua -> l.pushValue(host.httpGet(l.checkString(), l.mapArg(2))); 1 },
            // No lifetime, or nil, means the documented 600 s. (Reading nil as a number would give 0: never cached.)
            "cached" to { l: Lua -> l.pushValue(host.httpCached(l.checkString(), if (l.isNumber(2)) l.toInteger(2).toInt() else 600)); 1 },
            "request" to { l: Lua ->
                l.pushValue(host.httpRequest(l.mapArg(1) ?: throw IllegalArgumentException("http.request expects a table")))
                1
            },
        )
        lua.table(
            "clipboard",
            "get" to { l: Lua -> l.pushValue(host.clipboardGet()); 1 },
            "set" to { l: Lua -> host.clipboardSet(l.checkString()); 0 },
        )
        lua.table(
            "device",
            "battery" to { l: Lua -> l.pushValue(host.battery()); 1 },
            "network" to { l: Lua -> l.push(host.network()); 1 },
            "unlocks" to { l: Lua -> l.push(host.unlocksToday().toLong()); 1 },
            "dnd" to { l: Lua -> l.pushValue(host.dnd()); 1 },
            "setDnd" to { l: Lua -> l.push(host.setDnd(l.checkString())); 1 },
            "work" to { l: Lua -> l.pushValue(host.work()); 1 },
            "setWork" to { l: Lua ->
                val on = when (l.type(1)) {
                    Lua.LuaType.BOOLEAN -> l.toBoolean(1)
                    Lua.LuaType.STRING -> l.toString(1)
                    else -> null
                }
                l.push(host.setWork(host.onOff(on, "device.setWork")))
                1
            },
        )
        lua.table("apps", "list" to { l: Lua -> l.pushValue(host.appsList()); 1 })
        lua.table("notifications", "recent" to { l: Lua -> l.pushValue(host.notificationsRecent(if (l.isNumber(1)) l.toInteger(1).toInt() else 20)); 1 })
        lua.table("launcher", "run" to { l: Lua -> l.push(host.runCommand(l.checkString())); 1 })
        lua.global("sleep") { l ->
            host.sleep(l.toInteger(1))
            0
        }
        lua.table(
            "rss",
            "fetch" to { l: Lua -> l.pushValue(host.rssFetch(l.checkString())); 1 },
            "fresh" to { l: Lua -> l.pushValue(host.rssFresh(l.checkString())); 1 },
            "parse" to { l: Lua -> l.pushValue(host.rssParse(l.checkString())); 1 },
        )
        lua.table(
            "store",
            "get" to { l: Lua -> l.pushValue(host.storeGet(l.checkString())); 1 },
            // A boolean or a table is stored as text (true, JSON), not dropped as nil.
            "set" to { l: Lua -> host.storeSet(l.checkString(), l.str(2)); 0 },
        )
        lua.table(
            "md",
            "log" to { l: Lua -> l.logArgs().let { (title, body, ctx) -> l.pushLine(host.logMarkdown(title, body, ctx)) }; 1 },
            "escape" to { l: Lua -> l.pushValue(Markdown.escape(l.str(1) ?: "")); 1 },
        )
        val fmt = Fmt(::cellText)
        lua.table(
            "fmt",
            "bold" to { l: Lua -> l.styled(StyledText.Style.BOLD); 1 },
            "italic" to { l: Lua -> l.styled(StyledText.Style.ITALIC); 1 },
            "underline" to { l: Lua -> l.styled(StyledText.Style.UNDERLINE); 1 },
            "strike" to { l: Lua -> l.styled(StyledText.Style.STRIKE); 1 },
            "code" to { l: Lua -> l.styled(StyledText.Style.CODE); 1 },
            "color" to { l: Lua -> l.pushValue(StyledText.colour(colourNamed(l.toString(1)), l.str(2) ?: "nil")); 1 },
            "list" to { l: Lua ->
                val items = (if (!l.isTable(1)) null else when (val v = Json.normalizeLua(l.toMap(1))) {
                    is List<*> -> v.map { it.toString() }
                    is Map<*, *> -> if (v.isEmpty()) emptyList() else null
                    else -> null
                }) ?: throw IllegalArgumentException("fmt.list expects a list of items")
                l.pushValue(StyledText.list(items, numbered = l.top >= 2 && l.toBoolean(2)))
                1
            },
            "table" to { l: Lua ->
                val bad = "fmt.table expects a list of rows, each a list of cells"
                val rows = (if (!l.isTable(1)) null else when (val v = Json.normalizeLua(l.toMap(1))) {
                    is List<*> -> v
                    is Map<*, *> -> if (v.isEmpty()) emptyList<Any?>() else null
                    else -> null
                })?.map { r ->
                    when (r) {
                        is List<*> -> r.map(::cellText)
                        is Map<*, *> -> if (r.isEmpty()) emptyList() else throw IllegalArgumentException(bad)
                        else -> throw IllegalArgumentException(bad)
                    }
                } ?: throw IllegalArgumentException(bad)
                val opts = if (l.top >= 2 && l.isTable(2)) Json.normalizeLua(l.toMap(2)) as? Map<*, *> else null
                l.pushValue(StyledText.table(rows, header = opts?.get("header") == true, align = opts?.get("align")?.toString().orEmpty()))
                1
            },
            "tag" to { l: Lua -> l.pushValue(fmt.tag(l.args())); 1 },
            "link" to { l: Lua -> l.pushValue(fmt.link(l.args())); 1 },
            "bar" to { l: Lua -> l.pushValue(fmt.bar(l.args())); 1 },
            "bars" to { l: Lua -> l.pushValue(fmt.bars(l.args())); 1 },
            "ranges" to { l: Lua -> l.pushValue(fmt.ranges(l.args())); 1 },
            "stats" to { l: Lua -> l.pushValue(fmt.stats(l.args())); 1 },
            "plain" to { l: Lua -> l.pushValue(StyledText.plain(l.str(1) ?: "")); 1 },
        )
        lua.table(
            "secrets",
            "get" to { l: Lua -> l.pushValue(host.secretGet(l.checkString())); 1 },
        )
        lua.table(
            "json",
            "decode" to { l: Lua -> l.pushValue(Json.decode(l.checkString())); 1 },
            "encode" to { l: Lua -> l.push(Json.stringify(Json.normalizeLua(l.toObject(1)))); 1 },
        )
    }

    /**
     * Argument [index] as text, as `tostring` shows it. Null for nil or none.
     * - A boolean becomes true/false and a table becomes JSON (lua_tostring gives nil for both).
     * - A number is written as Lua writes it.
     */
    /** A table cell from Lua: text as it is, a whole number without ".0", nothing as an empty cell. */
    private fun cellText(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v % 1.0 == 0.0 && v in -9.0e15..9.0e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun Lua.str(index: Int): String? = when {
        top < index || isNil(index) -> null
        isBoolean(index) -> toBoolean(index).toString()
        isTable(index) -> Json.stringify(Json.normalizeLua(toMap(index)))
        else -> toString(index)
    }

    private fun Lua.checkString(): String =
        toString(1) ?: throw IllegalArgumentException("argument #1 must be a string")

    /** The role for `fmt.color(role, text)`, or an error that lists the existing roles. */
    private fun colourNamed(role: String?): StyledText.Colour = role?.let(StyledText.Colour::named)
        ?: throw IllegalArgumentException("fmt.color: no colour '$role'; use ${StyledText.Colour.entries.joinToString { it.role }}")

    /** `fmt.<style>(text)`: the text with [style] applied. */
    private fun Lua.styled(style: StyledText.Style) {
        pushValue(StyledText.wrap(style, str(1) ?: "nil"))
    }

    /**
     * `(title, body?, ctx?)` from argument [from] on, as log, md.log and line.set take them. If the
     * second is a table, it is the ctx: log(title, ctx).
     */
    private fun Lua.logArgs(from: Int = 1): Triple<String, String?, Map<*, *>?> {
        val tableSecond = top >= from + 1 && isTable(from + 1)
        val body = if (top >= from + 1 && !tableSecond) str(from + 1) else null
        return Triple(str(from) ?: "nil", body, mapArg(if (tableSecond) from + 1 else from + 2))
    }

    /** The line's number that log returned, for the prelude to make a handle of; nil when nothing was logged. */
    private fun Lua.pushLine(number: Int) = if (number > 0) push(number.toLong()) else pushNil()

    /** Every argument as a Kotlin value (nil, a number, text, true/false, or a table as a list or a map), for [Fmt]. */
    private fun Lua.args(): List<Any?> = (1..top).map { i ->
        when (type(i)) {
            Lua.LuaType.NUMBER -> toNumber(i)
            Lua.LuaType.STRING -> toString(i)
            Lua.LuaType.BOOLEAN -> toBoolean(i)
            Lua.LuaType.TABLE -> Json.normalizeLua(toMap(i))
            else -> null
        }
    }

    private fun Lua.mapArg(index: Int): Map<*, *>? =
        if (top >= index && isTable(index)) Json.normalizeLua(toMap(index)) as? Map<*, *> else null

    private fun Lua.pushValue(value: Any?) {
        if (value == null) pushNil() else push(value, Lua.Conversion.FULL)
    }


    private companion object {
        /** Native memory a run may add before the watchdog stops it: twice the Lua cap (64 MB). */
        const val NATIVE_LIMIT_MB = 128L

        val PRELUDE = """
            local check = __kolahal_check
            local sethook = debug.sethook
            local gc = collectgarbage
            -- Everything the guards below call, kept now: the script can replace the globals and the
            -- string and table libraries, which would otherwise switch the guards off.
            local error, type, tostring, select = error, type, tostring, select
            local pack, unpack, concat = table.pack, table.unpack, table.concat
            local sfind, slower = string.find, string.lower
            -- Memory every thousand instructions; the clock and cancellation, a call into Kotlin, every
            -- ten thousand.
            local HOOK_EVERY = 1000
            local CHECK_EVERY = 10
            local MEMORY_LIMIT_KB = 64 * 1024
            local hooks = 0
            local function hook()
              hooks = hooks + 1
              if hooks % CHECK_EVERY == 0 then check() end
              if gc("count") > MEMORY_LIMIT_KB then error("Memory limit exceeded (64 MB)", 0) end
            end
            sethook(hook, "", HOOK_EVERY)

            -- Timeouts and the memory cap must not be swallowed by pcall / xpcall / resume.
            local function fatal(e)
              -- Lowercased: the hook raises "Memory limit exceeded (64 MB)".
              local s = type(e) == "string" and slower(e)
              return s and (sfind(s, "script timed out", 1, true) or sfind(s, "memory limit exceeded", 1, true)) ~= nil
            end
            local function rethrow(res)
              if not res[1] and fatal(res[2]) then error(res[2], 0) end
              return unpack(res, 1, res.n)
            end
            local rawpcall, rawxpcall = pcall, xpcall
            pcall = function(f, ...) return rethrow(pack(rawpcall(f, ...))) end
            xpcall = function(f, handler, ...)
              return rethrow(pack(rawxpcall(f, function(e)
                if fatal(e) then return e end
                return handler(e)
              end, ...)))
            end

            -- Coroutines get their own hook so they cannot dodge the timeout.
            local cocreate, coresume = coroutine.create, coroutine.resume
            local function hooked(f)
              local co = cocreate(f)
              sethook(co, hook, "", HOOK_EVERY)
              return co
            end
            coroutine.create = hooked
            coroutine.resume = function(co, ...) return rethrow(pack(coresume(co, ...))) end
            coroutine.wrap = function(f)
              local co = hooked(f)
              return function(...)
                local res = pack(coresume(co, ...))
                if not res[1] then error(res[2], fatal(res[2]) and 0 or 2) end
                return unpack(res, 2, res.n)
              end
            end

            -- string.rep can ask for gigabytes in one call, before the hook gets to check memory.
            local rawrep, tointeger = string.rep, math.tointeger
            local REP_LIMIT = 16 * 1024 * 1024
            string.rep = function(s, n, sep)
              local each, count = #tostring(s) + (sep ~= nil and #tostring(sep) or 0), tointeger(n) or 0
              -- Divided, not multiplied: a huge count would wrap the product around to a small number.
              if count > 0 and each > 0 and each > REP_LIMIT // count then error("string.rep: result too large (over 16 MB)", 2) end
              return rawrep(s, n, sep)
            end

            -- Sandbox
            -- A Java object that ever reached the script (none should) can do nothing: its metatables
            -- keep only what frees it (__gc), and can't be read. The host functions are C closures
            -- and don't use them.
            do
              local registry = debug.getregistry()
              for _, kind in ipairs({ "__jobject__", "__jclass__", "__jarray__" }) do
                local mt = registry[kind]
                if type(mt) == "table" then
                  for k in pairs(mt) do if k ~= "__gc" then mt[k] = nil end end
                  mt.__metatable = false
                end
              end
            end
            local realload = load
            java, debug, io, package, require, dofile, loadfile = nil, nil, nil, nil, nil, nil, nil
            -- LuaJava keeps a thrown Java exception (a reflective object) here; none should get this far.
            __jthrowable__ = nil
            os = { time = os.time, date = os.date, clock = os.clock, difftime = os.difftime }
            -- Text chunks only. Passing env explicitly (even nil) would replace _ENV, so forward it only if given.
            load = function(chunk, name, _, ...)
              if select("#", ...) > 0 then return realload(chunk, name, "t", ...) end
              return realload(chunk, name, "t")
            end

            -- log and md.log return the line they logged, as a handle: line.set(title, body?, ctx?) or
            -- line:set(…) changes it in place. A script's result that is a handle isn't logged.
            local rawlog, rawmdlog, setline = log, md.log, __kolahal_set
            __kolahal_set = nil
            local handles = setmetatable({}, { __mode = "k" })
            local function handle(n, ismd)
              if n == nil then return nil end
              local h = {}
              h.set = function(a, ...)
                if a == h then setline(n, ismd, ...) else setline(n, ismd, a, ...) end
                return h
              end
              handles[h] = true
              return h
            end
            log = function(...) return handle(rawlog(...), false) end
            md.log = function(...) return handle(rawmdlog(...), true) end

            print = function(...)
              local t = table.pack(...)
              for i = 1, t.n do t[i] = tostring(t[i]) end
              log(table.concat(t, "\t"))
            end

            local src, name = __kolahal_src, __kolahal_name
            __kolahal_check, __kolahal_src, __kolahal_name = nil, nil, nil

            local function render(v)
              if type(v) == "table" then
                local ok, s = pcall(json.encode, v)
                if ok then return s end
              end
              return tostring(v)
            end

            function __kolahal_main(repl)
              local fn, err
              if repl then fn = realload("return " .. src, "=" .. name, "t") end
              if not fn then fn, err = realload(src, "=" .. name, "t") end
              if not fn then error(err, 0) end
              local res = pack(fn())
              -- A line's handle (a REPL line `log("x")`, or `return log(…)`) is no result.
              if res.n == 1 and handles[res[1]] then return nil end
              if res.n == 0 or (res.n == 1 and res[1] == nil) then return nil end
              local out = {}
              for i = 1, res.n do out[i] = render(res[i]) end
              return concat(out, "\t")
            end
        """.trimIndent()
    }
}
