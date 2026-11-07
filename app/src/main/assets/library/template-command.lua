-- @title Template: command
-- @desc A starting point for a command you type: reads its words, logs lines that do something when tapped
-- @author Kolahal
-- @version 1.2
-- @command
-- @usage [name]
-- @tags template
-- @requires 0.4.1
--
-- A starting point for a command of your own. Try `template-command`, then `template-command Ada`.
-- Edit this one, or start your own with `script new <name> --cmd` and copy what you need from here.
--
-- The @command line makes the file's name a command you type. The @usage line says what may follow
-- the name, the way a built-in command's usage reads ([x] optional, <x> needed, a|b one of): Kolahal
-- suggests it as you type, and knows when the line is complete.

-- The words after the name: `args` is a table of them, `argline` the same words as one string.
local name = argline ~= "" and argline or "world"

-- log(title, body, ctx) adds a line under this run's header: the title in bold, the body (if any)
-- muted under it. The table at the end decides what tapping and long-pressing the line do: url
-- opens a link, copy is what "Copy text" copies.
log("Hello, " .. name .. "!", "You gave " .. #args .. (#args == 1 and " word" or " words"), { copy = name })

-- md.log is log reading Markdown: **bold**, *italic*, `code`, and lists in the body.
md.log("**Try**", "- `template-command Ada`\n- `template-command` on its own")

-- fmt styles parts of any line, and text from the web needs no escaping in it. Colours come from
-- the theme by role (good, warn, bad, muted, accent, …), so they read in every theme.
log("The scripting guide", fmt.color("muted", "mtwabpin.github.io/kolahal"), {
  url = "https://mtwabpin.github.io/kolahal/scripting/overview/",
})

-- Whatever the script returns is printed as its result line (a table prints as JSON).
return os.date("It's %H:%M")
