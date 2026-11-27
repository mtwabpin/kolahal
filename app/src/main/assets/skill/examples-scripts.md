### Script: a widget you tap (Lua)

Saved as `water`; place it with `widget add`.

```lua
-- @title Water
-- @desc Glasses of water today; a tap on the card adds one
-- @widget progress 2x1
-- @at 00:01
-- @option goal 8 Glasses a day

local today = os.date("%Y-%m-%d")
local n = 0
if store.get("day") == today then n = tonumber(store.get("count")) or 0 end
if event and event.name == "action" and args[1] == "add" then
  n = n + 1
  store.set("day", today)
  store.set("count", tostring(n))
end
local goal = tonumber(options.goal) or 8

widget.show{
  kind = "progress",
  value = n,
  max = goal,
  text = n .. " / " .. goal,
  label = "Glasses today",
  color = n >= goal and "good" or "accent",
  tap = { args = "add", label = "add a glass" },
}
```

### Script: a command with arguments (Lua)

Saved as `stars`; `stars neovim/neovim` logs the numbers. Asks for internet.

```lua
-- @command
-- @desc GitHub stars: stars [owner/repo]
-- @usage [<owner/repo>]

local repo = args[1] or "neovim/neovim"
if not repo:match("^[%w%.%-_]+/[%w%.%-_]+$") then return "usage: stars owner/repo" end
local r = http.cached("https://api.github.com/repos/" .. repo, 600)
if not r.ok then error("GitHub answered HTTP " .. r.status) end
local d = json.decode(r.body)
log(d.full_name, fmt.stats({
  { tostring(d.stargazers_count), "stars" },
  { tostring(d.forks_count), "forks" },
  { tostring(d.open_issues_count), "open issues" },
}), { url = d.html_url })
```

### Script: a timer that notifies (JavaScript)

Saved as `releases.js`; then `script notify releases on`, and `script pin releases` for its status line. Asks for internet.

```js
// @title Releases
// @desc Tells you when a GitHub project has a new release
// @every 6h
// @notify
// @option repo neovim/neovim The project, as owner/name

const repo = options.repo;
if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) return log(fmt.color("bad", "Set the repo option to owner/name"));
const r = await http.get(`https://api.github.com/repos/${repo}/releases/latest`, { Accept: "application/vnd.github+json" });
if (r.status === 404) return log(`${repo} has no releases yet`);
if (!r.ok) throw new Error(`GitHub answered HTTP ${r.status}`);
const release = JSON.parse(r.body);
status(`${repo.split("/")[1]} ${release.tag_name}`);
const seen = store.get("seen");
if (seen === release.tag_name) return;
store.set("seen", release.tag_name);
if (seen != null) {
  log(repo, fmt.color("good", "New: ") + fmt.code(release.tag_name), { url: release.html_url });
  notify(`${repo} ${release.tag_name}`, release.name || "New release", { url: release.html_url });
}
```
