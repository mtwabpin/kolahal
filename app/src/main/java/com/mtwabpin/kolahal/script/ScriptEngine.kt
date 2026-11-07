package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang

/**
 * A scripting runtime. Both implementations expose the same [HostApi] surface. So a script's
 * behaviour depends only on its language syntax, not on the engine that runs it.
 */
interface ScriptEngine {
    /**
     * Runs [code] to completion.
     *
     * @param repl true when the code is a one-liner typed at the prompt. Its value is then returned.
     * @param args arguments when the script is run as a command (`args` / `argline` globals).
     * @param event what started the run: a device event or a daily time (`event` global; nil/null otherwise).
     * @return a printable form of the result value, or null when there is nothing to show.
     */
    suspend fun run(
        code: String,
        chunkName: String,
        host: HostApi,
        timeoutMs: Long,
        repl: Boolean,
        args: List<String> = emptyList(),
        event: Map<*, *>? = null,
    ): String?

    companion object {
        fun forLang(lang: ScriptLang): ScriptEngine = when (lang) {
            ScriptLang.LUA -> LuaEngine()
            ScriptLang.JS -> JsEngine()
        }
    }
}
