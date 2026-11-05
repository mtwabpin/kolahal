package com.mtwabpin.kolahal.script

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import party.iroiro.luajava.JFunction
import party.iroiro.luajava.Lua
import party.iroiro.luajava.lua54.Lua54
import com.mtwabpin.kolahal.data.StyledText

/**
 * Native Lua 5.4 through LuaJava. Each run gets a fresh, sandboxed state:
 * no `java` bridge, `io`, `package`/`require`, `debug`, or process-level `os` functions.
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
        // withContext cannot cancel blocking JNI. As a last resort, kill the isolated :script process.
        // The hook checks memory every thousand instructions, but a few huge allocations (`s = s .. s`
        // in a loop doubles a string every few instructions) outrun any count: the watchdog also
        // stops a run whose native memory grows past NATIVE_LIMIT_MB, looking often enough that it
        // overshoots by about one doubling. LuaJava gives no say over Lua's allocator itself.
        val watchdog = RunWatchdog(host, timeoutMs, NATIVE_LIMIT_MB)
        try {
            lua.openLibraries()
            lua.push { l ->
                if (!ctx.isActive || System.currentTimeMillis() > deadline) {
                    l.push("script timed out after ${timeoutMs / 1000}s")
                    -1
                } else 0
            }
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
            // Close while the watchdog still runs: lua_close runs pending __gc finalizers, where the
            // instruction hook is off, so a looping finalizer is only stopped by the watchdog. They
            // can't call the host: LuaJava lets go of the state before closing it.
            watchdog.use { lua.close() }
        }
    }

    private fun bindHost(lua: Lua, host: HostApi) {
        /** Kotlin exceptions become Lua errors instead of crossing JNI, masked as the script may see them. */
        fun safe(fn: (Lua) -> Int) = JFunction { l ->
            try {
                fn(l)
            } catch (e: Throwable) {
                l.push(host.redact(e.message ?: e.javaClass.simpleName))
                -1
            }
        }

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

        lua.global("log") { l ->
            val (title, body, ctx) = l.logArgs()
            host.log(title, body, ctx)
            0
        }
        lua.global("logs") { l ->
            // logs({ "a", "b" }) or logs("a", "b"): one title line each.
            val titles = if (l.isTable(1)) {
                // An empty table is an empty list (it has no keys to tell them apart).
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
            // No lifetime, or nil, is the documented 600 s (nil read as a number would be 0: never cached).
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
            // A boolean or a table is kept as text (true, JSON), not dropped as nil.
            "set" to { l: Lua -> host.storeSet(l.checkString(), l.str(2)); 0 },
        )
        lua.table(
            "md",
            "log" to { l: Lua -> l.logArgs().let { (title, body, ctx) -> host.logMarkdown(title, body, ctx) }; 0 },
            "escape" to { l: Lua -> l.pushValue(Markdown.escape(l.str(1) ?: "")); 1 },
        )
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
     * Argument [index] as text, as `tostring` shows it: a boolean as true/false and a table as
     * JSON (lua_tostring gives nil for both), a number as Lua writes it; null for nil or none.
     */
    private fun Lua.str(index: Int): String? = when {
        top < index || isNil(index) -> null
        isBoolean(index) -> toBoolean(index).toString()
        isTable(index) -> Json.stringify(Json.normalizeLua(toMap(index)))
        else -> toString(index)
    }

    private fun Lua.checkString(): String =
        toString(1) ?: throw IllegalArgumentException("argument #1 must be a string")

    /** `fmt.color(role, text)`'s role, or an error naming the ones there are. */
    private fun colourNamed(role: String?): StyledText.Colour = role?.let(StyledText.Colour::named)
        ?: throw IllegalArgumentException("fmt.color: no colour '$role'; use ${StyledText.Colour.entries.joinToString { it.role }}")

    /** `fmt.<style>(text)`: the text with [style] on it. */
    private fun Lua.styled(style: StyledText.Style) {
        pushValue(StyledText.wrap(style, str(1) ?: "nil"))
    }

    /** `(title, body?, ctx?)` as log and md.log take them; a table second is the ctx: log(title, ctx). */
    private fun Lua.logArgs(): Triple<String, String?, Map<*, *>?> {
        val tableSecond = top >= 2 && isTable(2)
        val body = if (top >= 2 && !tableSecond) str(2) else null
        return Triple(str(1) ?: "nil", body, mapArg(if (tableSecond) 2 else 3))
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
              if res.n == 0 or (res.n == 1 and res[1] == nil) then return nil end
              local out = {}
              for i = 1, res.n do out[i] = render(res[i]) end
              return concat(out, "\t")
            end
        """.trimIndent()
    }
}
