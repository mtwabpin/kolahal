---
title: Examples
description: Complete scripts to copy, from a battery logger to an uptime checker and command scripts.
---

Each example is a complete script. To use one:

1. Create a script with `script new <name>` (add `.js` for JavaScript).
2. Paste the code into the editor.
3. Save with `Ctrl+S` or **save**.

## Battery log (Lua, timer)

Records the battery level on each run. Its status line shows the change since the last run.

```lua
-- batterylog.lua — run with: script every batterylog 30m
-- @desc Battery level history

local b = device.battery()
if not b.level then return "battery level unknown" end

local history = json.decode(store.get("history") or "[]")
table.insert(history, { t = os.time(), level = b.level, charging = b.charging })
while #history > 48 do table.remove(history, 1) end
store.set("history", json.encode(history))

local prev = history[#history - 1]
local delta = prev and (b.level - prev.level) or 0
status(string.format("%d%%%s (%+d)", b.level, b.charging and " ⚡" or "", delta))

if b.level <= 15 and not b.charging then
  notify("Battery low", b.level .. "% left")
end
```

## Uptime checker (JavaScript, timer, parallel requests)

Checks several sites at the same time. It notifies you only when a site goes down or comes back up.

```js
// uptime.js — script every uptime 15m · script notify uptime on
// @desc Site uptime checker

const SITES = ["https://example.com", "https://github.com", "https://lwn.net"];

const results = await Promise.all(
  SITES.map(async (url) => {
    const started = Date.now();
    try {
      const r = await http.request({ url, method: "HEAD" });
      return { url, up: r.ok, info: `${r.status} in ${Date.now() - started}ms` };
    } catch (e) {
      return { url, up: false, info: String(e.message || e) };
    }
  })
);

const before = JSON.parse(store.get("state") || "{}");
const now = {};
for (const { url, up, info } of results) {
  now[url] = up;
  if (before[url] !== undefined && before[url] !== up) {
    notify(up ? "Back up" : "Down", `${url} — ${info}`, { url });
  }
  log(`${up ? "✓" : "✗"} ${url} · ${info}`, { url });
}
store.set("state", JSON.stringify(now));

const down = results.filter((r) => !r.up).length;
status(down ? `${down} site(s) down` : null);
```

## Notes (Lua, command)

A small notepad: `jot buy stamps` adds a note, `jot` lists the notes, and `jot rm 2` removes one.
The name `note` is already used by Kolahal's own notes.

```lua
-- jot.lua
-- @command
-- @desc Quick notes: jot <text> | jot | jot rm N

local notes = json.decode(store.get("notes") or "[]")
local function save() store.set("notes", json.encode(notes)) end

local n = argline:match("^rm%s+(%d+)$")
if n then
  local removed = table.remove(notes, tonumber(n))
  save()
  return removed and ("removed: " .. removed) or "no note #" .. n
end

if argline ~= "" then
  table.insert(notes, argline)
  save()
  return "saved (" .. #notes .. " notes)"
end

if #notes == 0 then return "no notes yet: jot <text>" end
for i, text in ipairs(notes) do log(i .. ". " .. text, { copy = text }) end
```

## GitHub stars (JavaScript, command with caching)

`stars owner/repo` shows how many stars a repository has. The response is cached for ten minutes,
so runs in that time make no new request.

```js
// stars.js
// @command
// @desc GitHub stars: stars <owner/repo>

const repo = (args[0] || "mtwabpin/kolahal").replace(/^https:\/\/github\.com\//, "");
if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) return "usage: stars owner/repo";

const r = await http.cached(`https://api.github.com/repos/${repo}`, 600);
if (!r.ok) return `stars: HTTP ${r.status}`;

const d = JSON.parse(r.body);
log(`${d.full_name}: ★ ${d.stargazers_count} · ${d.forks_count} forks · ${d.open_issues_count} open issues`,
  { url: d.html_url });
if (d.description) log(d.description);
```

## Morning briefing (Lua, daily time)

Runs at 07:30. It sends one notification with the weather and the number of waiting
notifications.

```lua
-- morning.lua — script at morning 07:30 · script notify morning on
-- @desc Morning summary
-- @uses notifications

local parts = {}

local ok, r = pcall(http.get, "https://wttr.in/?format=%C+%t")
if ok and r.ok then table.insert(parts, (r.body:gsub("%s+$", ""))) end

local waiting = #notifications.recent(50)
if waiting > 0 then table.insert(parts, waiting .. " notifications") end

local b = device.battery()
if b.level then table.insert(parts, "battery " .. b.level .. "%") end

local summary = table.concat(parts, " · ")
log(summary)
notify("Good morning", summary)
```

## Morning routine (Lua, command driving the launcher)

`launcher.run` works only when the script starts from the prompt, so it suits command scripts.
Commands that change settings or data (`work`, `notifs`, `clear`, `script` and similar) must be
typed by you. A script can't run them.

```lua
-- routine.lua
-- @command
-- @desc Routine: the weather, the day's agenda, then the news app
-- @uses commands

launcher.run("weather")
launcher.run("agenda")
launcher.run("open news")
return "good morning"
```

## Charging log (Lua, event trigger)

Uses [`@on` triggers](/scripting/overview/#event-triggers) to run when you connect or remove the
charger.

```lua
-- charging.lua
-- @on charger
-- @on unplug

local b = device.battery()
local what = b.charging and "plugged in" or "unplugged"
log(os.date("%H:%M") .. " " .. what .. " at " .. tostring(b.level) .. "%")
```
