-- @title Template: fetch
-- @desc A starting point for a command that fetches JSON from the web and logs a line you can tap
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage [topic]
-- @tags template
-- @requires 0.4.1
--
-- The Lua version of template-js. It looks up a Wikipedia article's summary. Try `template-fetch`,
-- then `template-fetch Lua (programming language)`. Talks to: en.wikipedia.org.
--
-- In Lua, http.get waits for the answer and returns it: no await. `return` stops the script early.

-- Lua has no URL encoder built in: this one keeps letters, digits and - _ . ~, and escapes the rest.
local function urlencode(s)
  return (s:gsub("[^%w%-_%.~]", function(c)
    return string.format("%%%02X", string.byte(c))
  end))
end

local topic = argline ~= "" and argline or "Android (operating system)"
local r = http.get("https://en.wikipedia.org/api/rest_v1/page/summary/" .. urlencode((topic:gsub(" ", "_"))))
if r.status == 404 then return log(fmt.color("muted", "Wikipedia has no page called '" .. topic .. "'")) end
if not r.ok then error("Wikipedia answered HTTP " .. r.status) end
local page = json.decode(r.body)

-- log(title, body, ctx): the article's title (its short description beside it, in italics), its
-- summary muted under it; the table at the end decides what tapping the line does: here, open the
-- article. fmt styles parts of a line.
local title = page.title .. (page.description and (" " .. fmt.italic(page.description)) or "")
log(title, page.extract, { url = page.content_urls.desktop.page })
