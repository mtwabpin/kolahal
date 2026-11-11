-- countdown.lua — days until (or since) dates, and to the next birthday or anniversary. No network.
-- @command
-- @desc Days until a date, or the next birthday or anniversary: countdown YYYY-MM-DD | add [yearly] YYYY-MM-DD label | rm N
-- @usage [<YYYY-MM-DD>|add [yearly] <YYYY-MM-DD> <label>|rm <n>]
-- @title Countdowns
-- @at 00:05
--
-- Usage:
--   countdown 2026-12-25                          days until a date
--   countdown add 2026-12-25 Christmas            save a countdown
--   countdown add yearly 2001-06-15 Wedding anniversary
--                                                 one every year: to the next 15 June, and which one
--                                                 it is (25th)
--   countdown add 06-15 Mum's birthday            a date without a year comes every year too
--   countdown                                     list saved countdowns (nearest first)
--   countdown rm 2                                delete saved countdown #2
--
-- On a timer (e.g. daily) it sets the nearest upcoming countdown as its status line.

local DAY = 86400

local function isLeap(y) return (y % 4 == 0 and y % 100 ~= 0) or y % 400 == 0 end

-- A day at local noon (noon avoids DST edge cases). 29 February, in a year without one, is the 28th.
local function noon(y, m, d)
  if m == 2 and d == 29 and not isLeap(y) then d = 28 end
  return os.time({ year = y, month = m, day = d, hour = 12 })
end

-- "YYYY-MM-DD", or "MM-DD" for a date that comes every year: { year (nil without one), month, day }.
-- Nil when it isn't a date.
local function parseDate(s)
  local y, m, d = (s or ""):match("^(%d%d%d%d)-(%d%d?)-(%d%d?)$")
  if not y then m, d = (s or ""):match("^(%d%d?)-(%d%d?)$") end
  if not m then return nil end
  y, m, d = tonumber(y), tonumber(m), tonumber(d)
  -- os.time normalises 2026-02-31 to March; reject such dates. Without a year, 2000 (a leap year) lets 02-29 through.
  local back = os.date("*t", os.time({ year = y or 2000, month = m, day = d, hour = 12 }))
  if back.month ~= m or back.day ~= d then return nil end
  return { year = y, month = m, day = d }
end

local function today()
  local now = os.date("*t")
  return os.time({ year = now.year, month = now.month, day = now.day, hour = 12 })
end

local function daysUntil(t)
  return math.floor(os.difftime(t, today()) / DAY + 0.5)
end

-- When [date] comes: a one-time date is that day. One that comes every year ([yearly], or a date with
-- no year) is its next day, today or later; never before its first, for a first anniversary to come.
local function nextTime(date, yearly)
  if date.year and not yearly then return noon(date.year, date.month, date.day) end
  local y = os.date("*t").year
  if date.year and date.year > y then y = date.year end
  local t = noon(y, date.month, date.day)
  if daysUntil(t) < 0 then t = noon(y + 1, date.month, date.day) end
  return t
end

-- 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st, 112th.
local function ordinal(n)
  local suffix = "th"
  if n % 100 < 11 or n % 100 > 13 then
    suffix = ({ "st", "nd", "rd" })[n % 10] or "th"
  end
  return n .. suffix
end

-- The name to show: for one that comes every year from a known year, which one is next ("25th").
local function title(label, date, yearly, t)
  if not (yearly and date.year) then return label end
  local n = tonumber(os.date("%Y", t)) - date.year
  return n >= 1 and (label .. " · " .. ordinal(n)) or label
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

local function usage() log(fmt.color("muted", "Usage: countdown YYYY-MM-DD | add [yearly] YYYY-MM-DD label | rm N")) end

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

-- When a saved item comes next (0 for one that isn't a date).
local function itemTime(item)
  local date = parseDate(item.date)
  return date and nextTime(date, item.yearly) or 0
end

-- Saved items sorted by when they come (stable order for "rm N"): by the day, as 2026-11-5 sorts
-- after 2026-11-20 as text, and a yearly one by its next day.
local function sorted(list)
  table.sort(list, function(a, b) return itemTime(a) < itemTime(b) end)
  return list
end

-- The date column: a one-time date as typed; a yearly one as "every 15 Jun".
local function dateText(item, date)
  if not (item.yearly or not date.year) then return item.date end
  return "every " .. date.day .. " " .. os.date("%b", noon(2000, date.month, date.day))
end

local cmd, rest = (argline or ""):match("^%s*(%S*)%s*(.-)%s*$")

if cmd == "add" then
  local yearly = false
  local first, after = rest:match("^(%S+)%s*(.*)$")
  if first == "yearly" then yearly, rest = true, after end
  local date, label = rest:match("^(%S+)%s*(.*)$")
  local parsed = parseDate(date)
  if not parsed then return usage() end
  -- A date without a year can only come every year.
  if not parsed.year then yearly = true end
  if label == nil or label == "" then label = date end
  local list = load()
  list[#list + 1] = { date = date, label = label, yearly = yearly or nil }
  save(sorted(list))
  local t = nextTime(parsed, yearly)
  local note = yearly and "Saved · every year · countdown lists them" or "Saved · countdown lists them"
  -- A date over a year ago is most likely a birthday or an anniversary: say how to have it every year.
  if not yearly and daysUntil(t) < -365 then note = note .. "\nEvery year instead: countdown add yearly " .. date .. " " .. label end
  return show(title(label, parsed, yearly, t), t, fmt.color("muted", note))
end

if cmd == "rm" then
  local n = tonumber(rest)
  local list = sorted(load())
  if not n or not list[n] then return log(fmt.color("muted", "No countdown #" .. (rest ~= "" and rest or "?") .. ": countdown lists them")) end
  local removed = table.remove(list, n)
  save(list)
  return log("Removed " .. removed.label, removed.date .. (removed.yearly and " · every year" or ""))
end

if cmd ~= "" then
  -- A date without a year counts to its next day.
  local date = parseDate(cmd)
  if not date then return usage() end
  return show(rest ~= "" and rest or cmd, nextTime(date, false))
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
  local date = parseDate(item.date)
  if date then
    local yearly = item.yearly or not date.year
    local t = nextTime(date, yearly)
    local days = daysUntil(t)
    local name = title(item.label, date, yearly, t)
    local function quiet(text) return days < 0 and fmt.color("muted", text) or text end
    rows[#rows + 1] = { tostring(i), quiet(name), quiet(dateText(item, date)), tint(days, when(days)) }
    if days >= 0 and nearest == nil then
      nearest = describe(days, name)
      nearestBig = big(days, t, "to " .. item.label)
    end
  end
end
-- The next one first, in big numbers, then the table.
log("Countdowns", (nearestBig and (nearestBig .. "\n") or "") .. fmt.table(rows, { header = true, align = "rllr" }))
status(nearest)
