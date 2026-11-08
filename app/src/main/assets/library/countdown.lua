-- countdown.lua — days until (or since) dates. No network.
-- @command
-- @desc Days until a date: countdown YYYY-MM-DD | add YYYY-MM-DD label | rm N
-- @usage [<YYYY-MM-DD>|add <YYYY-MM-DD> <label>|rm <n>]
-- @title Countdowns
-- @at 00:05
--
-- Usage:
--   countdown 2026-12-25               days until a date
--   countdown add 2026-12-25 Christmas save a countdown
--   countdown                          list saved countdowns (nearest first)
--   countdown rm 2                     delete saved countdown #2
--
-- On a timer (e.g. daily) it sets the nearest upcoming countdown as its status line.

local DAY = 86400

-- Parse "YYYY-MM-DD" into a timestamp at local noon (noon avoids DST edge cases).
local function parseDate(s)
  local y, m, d = (s or ""):match("^(%d%d%d%d)-(%d%d?)-(%d%d?)$")
  if not y then return nil end
  y, m, d = tonumber(y), tonumber(m), tonumber(d)
  if m < 1 or m > 12 or d < 1 or d > 31 then return nil end
  local t = os.time({ year = y, month = m, day = d, hour = 12 })
  -- os.time normalises 2026-02-31 to March; reject such dates.
  local back = os.date("*t", t)
  if back.month ~= m or back.day ~= d then return nil end
  return t
end

local function today()
  local now = os.date("*t")
  return os.time({ year = now.year, month = now.month, day = now.day, hour = 12 })
end

local function daysUntil(t)
  return math.floor(os.difftime(t, today()) / DAY + 0.5)
end

-- "today", "in 62 days", "3 days ago".
local function when(days)
  if days == 0 then return "today" end
  if days == 1 then return "tomorrow" end
  if days == -1 then return "yesterday" end
  if days > 0 then return "in " .. days .. " days" end
  return (-days) .. " days ago"
end

local function describe(days, label) return label .. ": " .. when(days) end

-- [text] coloured by how near it is: past ones muted, today's in the good colour, this week's in
-- the warning one, later ones as they are.
local function tint(days, text)
  if days < 0 then return fmt.color("muted", text) end
  if days == 0 then return fmt.color("good", text) end
  if days <= 7 then return fmt.color("warn", text) end
  return text
end

-- The date as it reads on a calendar: "25 Dec", with the year when it isn't this one.
local function dayMonth(t)
  local text = tonumber(os.date("%d", t)) .. " " .. os.date("%b", t)
  if os.date("%Y", t) ~= os.date("%Y") then text = text .. " " .. os.date("%Y", t) end
  return text
end

-- Big numbers (Kolahal 0.4.16's fmt.stats): the days, large and coloured by how near they are,
-- with what they count under them ([what]: "to go" by default, or "to Christmas"), then the date
-- with its weekday, smaller.
local function big(days, t, what)
  local count, under
  if days == 0 then
    count, under = "Today", what and what:gsub("^to ", "") or ""
  elseif days > 0 then
    count, under = tostring(days), (days == 1 and "day " or "days ") .. (what or "to go")
  else
    count, under = tostring(-days), (days == -1 and "day ago" or "days ago")
  end
  return fmt.stats({ { tint(days, count), under }, { dayMonth(t), os.date("%A", t) } })
end

-- One countdown: its name as the title, coloured by how near it is, the big numbers under it, and
-- [after] (a note such as "Saved") under them.
local function show(label, t, after)
  local days = daysUntil(t)
  log(tint(days, label), big(days, t) .. (after and ("\n" .. after) or ""))
end

local function usage() log(fmt.color("muted", "Usage: countdown YYYY-MM-DD | add YYYY-MM-DD label | rm N")) end

local function load()
  local raw = store.get("items")
  if not raw then return {} end
  local ok, list = pcall(json.decode, raw)
  if ok and type(list) == "table" then return list end
  return {}
end

local function save(list)
  store.set("items", json.encode(list))
end

-- Saved items sorted by date (stable order for "rm N"): by the day, as 2026-11-5 sorts after 2026-11-20 as text.
local function sorted(list)
  table.sort(list, function(a, b) return (parseDate(a.date) or 0) < (parseDate(b.date) or 0) end)
  return list
end

local cmd, rest = (argline or ""):match("^%s*(%S*)%s*(.-)%s*$")

if cmd == "add" then
  local date, label = rest:match("^(%S+)%s*(.*)$")
  if not parseDate(date) then return usage() end
  if label == nil or label == "" then label = date end
  local list = load()
  list[#list + 1] = { date = date, label = label }
  save(sorted(list))
  return show(label, parseDate(date), fmt.color("muted", "Saved · countdown lists them"))
end

if cmd == "rm" then
  local n = tonumber(rest)
  local list = sorted(load())
  if not n or not list[n] then return log(fmt.color("muted", "No countdown #" .. (rest ~= "" and rest or "?") .. ": countdown lists them")) end
  local removed = table.remove(list, n)
  save(list)
  return log("Removed " .. removed.label, removed.date)
end

if cmd ~= "" then
  local t = parseDate(cmd)
  if not t then return usage() end
  return show(rest ~= "" and rest or cmd, t)
end

-- No arguments: list saved countdowns and set the nearest upcoming one as the status line.
local list = sorted(load())
if #list == 0 then
  status(nil)
  return log(fmt.color("muted", "No countdowns yet: countdown add 2026-12-25 Christmas"))
end

-- One table: each countdown with its date and when it is, coloured by how near (past ones muted).
-- The number is the one `countdown rm` takes.
local rows = { { "#", "Countdown", "Date", "When" } }
local nearest, nearestBig = nil, nil
for i, item in ipairs(list) do
  local t = parseDate(item.date)
  if t then
    local days = daysUntil(t)
    local function quiet(text) return days < 0 and fmt.color("muted", text) or text end
    rows[#rows + 1] = { tostring(i), quiet(item.label), quiet(item.date), tint(days, when(days)) }
    if days >= 0 and nearest == nil then
      nearest = describe(days, item.label)
      nearestBig = big(days, t, "to " .. item.label)
    end
  end
end
-- The next one first, in big numbers, then the table.
log("Countdowns", (nearestBig and (nearestBig .. "\n") or "") .. fmt.table(rows, { header = true, align = "rllr" }))
status(nearest)
