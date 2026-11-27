### Widget: `stat`

```lua
-- @title Battery
-- @widget stat 2x1
-- @every 30m

local b = device.battery()
widget.show{
  value = b.level and tostring(b.level) or "?",
  unit = "%",
  label = b.charging and "Charging" or "Battery",
  progress = b.level and b.level / 100 or nil,
  color = (b.level or 100) < 20 and "warn" or "good",
  icon = "battery",
}
```

### Widget: `text`

```lua
-- @title Daily line
-- @widget text 2x2
-- @at 00:01
-- @option lines "Drink water;Call a friend;Take a walk" Lines, with ; between

local lines = {}
for l in options.lines:gmatch("[^;]+") do lines[#lines + 1] = l end
local day = tonumber(os.date("%j"))
widget.show{
  title = "Today",
  text = lines[day % #lines + 1] or "",
  caption = os.date("%A"),
}
```

### Widget: `list`

A tap on a row ticks that todo.

```lua
-- @title Open todos
-- @widget list 4x2
-- @uses todos
-- @every 1h

if event and event.name == "action" and args[1] == "done" then todos.setDone(args[2], true) end
local rows = {}
for _, t in ipairs(todos.list()) do
  if not t.done then
    rows[#rows + 1] = { title = t.text, done = false, tap = { args = "done " .. t.id, label = "done" } }
  end
end
widget.show{ title = "Todo", items = rows, empty = "Nothing open" }
```

### Widget: `chart`

```lua
-- @title Unlocks
-- @widget chart 2x2
-- @every 1h

local days = device.unlocks(7)
local first = os.date("%a", os.time() - 6 * 86400)
widget.show{
  points = days,
  style = "bars",
  value = tostring(days[#days]),
  label = "unlocks today",
  labels = { first, "today" },
  summary = "Unlocks a day for the last 7 days",
}
```

### Widget: `countdown`

```lua
-- @title New year
-- @widget countdown 2x1
-- @option label "New year" What it counts to

local at = os.time{ year = tonumber(os.date("%Y")) + 1, month = 1, day = 1, hour = 0 }
widget.show{
  to = at,
  label = options.label,
  style = "days",
  reached = "Happy new year",
  staleAfter = 604800,
}
```

### Widget: `buttons`

```lua
-- @title Mood
-- @widget buttons 4x1

local key = os.date("%Y-%m-%d")
if event and event.name == "action" then store.set(key, args[1]) end
local today = store.get(key)
local items = {}
for _, m in ipairs({ "good", "fine", "low" }) do
  items[#items + 1] = { label = m, icon = m == today and "check" or nil, tap = { args = m } }
end
widget.show{ items = items }
```

### Widget: `toggles`

```lua
-- @title Quiet
-- @widget toggles 2x1
-- @uses dnd

if event and event.name == "action" and args[1] == "dnd" then
  device.setDnd(device.dnd() == "off" and "on" or "off")
end
widget.show{
  items = { { label = "Do not disturb", icon = "dnd", on = device.dnd() ~= "off", tap = { args = "dnd" } } },
}
```

### Widget: `clock`

```lua
-- @title Two clocks
-- @widget clock 2x1
-- @option zone Europe/London The second place

widget.show{
  zones = { { zone = "Europe/Berlin", label = "Home" }, { zone = options.zone } },
  staleAfter = 604800,
}
```

### Widget: `agenda`

```lua
-- @title Next days
-- @widget agenda 4x2
-- @uses calendar
-- @every 1h

widget.show{ events = calendar.events(3), days = 3, empty = "Nothing planned" }
```

### Widget: `image` (JavaScript)

```js
// @title Comic
// @widget image 2x2
// @every 6h

const r = await http.get("https://xkcd.com/info.0.json");
if (!r.ok) throw new Error(`xkcd answered HTTP ${r.status}`);
const c = JSON.parse(r.body);
widget.show({
  image: c.img,
  alt: c.alt.slice(0, 200),
  caption: c.safe_title,
  fit: "fit",
  tap: { url: `https://xkcd.com/${c.num}/` },
});
```

### Widget: `versus`

Also a command: `tt left` gives the left side a point.

```lua
-- @command
-- @desc Table tennis score: tt left, tt right, tt reset
-- @usage [left|right|reset]
-- @widget versus 4x1
-- @option left Me Left player
-- @option right Asha Right player

local a = tonumber(store.get("a")) or 0
local b = tonumber(store.get("b")) or 0
if args[1] == "left" then a = a + 1 elseif args[1] == "right" then b = b + 1
elseif args[1] == "reset" then a, b = 0, 0 end
store.set("a", tostring(a))
store.set("b", tostring(b))
widget.show{
  sides = { { name = options.left, value = a }, { name = options.right, value = b } },
  score = a .. " – " .. b,
  tap = { args = "reset", label = "new game" },
}
```

### Widget: `sky` (JavaScript)

```js
// @title Sun
// @widget sky 2x2
// @every 6h
// @option lat 18.52 Latitude
// @option lng 73.86 Longitude

const r = await http.cached(`https://api.sunrise-sunset.org/json?lat=${options.lat}&lng=${options.lng}&formatted=0`, 21600);
if (!r.ok) throw new Error(`HTTP ${r.status}`);
const d = JSON.parse(r.body).results;
widget.show({ rises: Date.parse(d.sunrise) / 1000, sets: Date.parse(d.sunset) / 1000 });
```
