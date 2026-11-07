-- @title Template: API key
-- @desc A starting point for a script that needs an API key: you set the key on the phone, never in the code
-- @author Kolahal
-- @version 1.3
-- @every 6h
-- @secret GITHUB_TOKEN api.github.com A GitHub token: github.com → Settings → Developer settings → Fine-grained tokens (no permissions needed)
-- @tags template, github
-- @requires 0.4.1
--
-- A starting point for a script that calls an API with a key. This one shows your GitHub followers
-- in the header. Talks to: api.github.com.
--
-- The @secret line names the key (GITHUB_TOKEN), the site it may be sent to, and what it is. You
-- set the value on the phone: Scripts → tap this script → Secrets, or
-- `script secret template-api-key GITHUB_TOKEN`. It is kept encrypted, never in the code, backups
-- or the log, and a request that carries it anywhere but api.github.com stops the script.

-- nil until you set it: say how, instead of failing.
local token = secrets.get("GITHUB_TOKEN")
if not token then
  status("GitHub: set GITHUB_TOKEN")
  -- A hint, muted: fmt.color takes one of the theme's colours by role.
  return log(fmt.color("muted", "Set GITHUB_TOKEN"), "Scripts → template-api-key → Secrets")
end

local r = http.get("https://api.github.com/user", {
  Accept = "application/vnd.github+json",
  Authorization = "Bearer " .. token,
})
if r.status == 401 then
  status("GitHub: token refused")
  return log(fmt.color("bad", "Token refused"), "GitHub doesn't accept it: set GITHUB_TOKEN again")
end
if not r.ok then error("GitHub answered HTTP " .. r.status) end
local me = json.decode(r.body)

status(string.format("GitHub %s: %d followers", me.login, me.followers))
