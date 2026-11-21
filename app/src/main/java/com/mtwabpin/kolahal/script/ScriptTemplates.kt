/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang

/** Starter code for `script new`. */
object ScriptTemplates {
    /** Template for a script that runs from the prompt: `<name> some args`. */
    fun command(lang: ScriptLang, name: String): String = when (lang) {
        ScriptLang.LUA -> """
            -- @command
            -- @desc Current weather: $name [city]
            -- Run as '$name london'. Arguments: args (table), argline (string). The return value is printed.
            local city = argline ~= "" and argline or "London"
            local r = http.get("https://wttr.in/" .. city:gsub(" ", "+") .. "?format=3")
            if not r.ok then error("HTTP " .. r.status) end
            return (r.body:gsub("%s+$", ""))
        """.trimIndent()
        ScriptLang.JS -> """
            // @command
            // @desc Current weather: $name [city]
            // Run as '$name london'. Arguments: args (array), argline (string). The return value is printed.
            const city = argline || "London";
            const r = await http.get("https://wttr.in/" + encodeURIComponent(city) + "?format=3");
            if (!r.ok) throw new Error("HTTP " + r.status);
            return r.body.trim();
        """.trimIndent()
    }

    fun forLang(lang: ScriptLang, name: String): String = when (lang) {
        ScriptLang.LUA -> """
            -- $name.lua — runs on its timer in the :script process.
            -- API: log(title, body, ctx)  notify(title, body, ctx)  http.get(url)  rss.fresh(url)
            --      rss.fetch(url)  store.get(k)  store.set(k, v)  json.encode(v)  json.decode(s)
            local feed = rss.fresh("https://hnrss.org/frontpage")
            for i, item in ipairs(feed.items) do
              if i > 5 then break end
              log(item.title, { url = item.link })
            end
            if #feed.items > 0 then
              notify("$name", feed.items[1].title, { url = feed.items[1].link })
            end
        """.trimIndent()
        ScriptLang.JS -> """
            // $name.js — runs on its timer in the :script process. Top-level await works.
            // API: log(title, body, ctx)  notify(title, body, ctx)  await http.get(url)  await rss.fresh(url)
            //      await rss.fetch(url)  store.get(k)  store.set(k, v)  JSON.*
            const feed = await rss.fresh("https://hnrss.org/frontpage");
            for (const item of feed.items.slice(0, 5)) {
              log(item.title, { url: item.link });
            }
            if (feed.items.length > 0) {
              notify("$name", feed.items[0].title, { url: feed.items[0].link });
            }
        """.trimIndent()
    }
}
