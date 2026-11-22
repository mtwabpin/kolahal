-- effect-on-word.lua — plays a one-off shader over home when a notification has one of your words. No network.
-- @on notification
-- @uses notifications, effects
-- @desc Plays an effect, such as hearts, when a notification has one of your words
-- @title Effect on a word
-- @author Kolahal
-- @version 1.0
-- @requires 0.5.17
-- @option words "" Words to look for, with commas between: a name, a nickname
-- @option effect hearts The one-off shader to play
--
-- It looks in the notification's title and text, whole words only, in any case. Run by hand
-- (event is nil) it plays the effect once, so you can see it.

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

local effect = trim(options.effect)
if effect == "" then effect = "hearts" end

-- Whether text has word, not as a part of a longer one. Letters outside a to z can't be told
-- from signs here, so next to one of those any place counts.
local function has(text, word)
  local from = 1
  while true do
    local a, b = text:find(word, from, true)
    if not a then return false end
    local before, after = text:sub(a - 1, a - 1), text:sub(b + 1, b + 1)
    if not before:match("%w") and not after:match("%w") then return true end
    from = a + 1
  end
end

local words = {}
for word in options.words:gmatch("[^,]+") do
  word = trim(word)
  if word ~= "" then words[#words + 1] = word:lower() end
end

if event == nil then
  shader.play(effect)
  if #words == 0 then
    log(fmt.color("muted", "No words yet"), "Set them in the script's sheet, or type: script set effect-on-word words \"Alex, home\"")
  end
  return
end

local text = ((event.title or "") .. "\n" .. (event.text or "")):lower()
for _, word in ipairs(words) do
  if has(text, word) then return shader.play(effect) end
end
