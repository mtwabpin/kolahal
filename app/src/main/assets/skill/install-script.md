**A script**, on the phone:

1. `script new <name>` for Lua, `script new <name>.js` for JavaScript. The editor opens with a template. A name is lowercase letters, digits, `-` and `_`, at most 40, and not a built-in command's name.
2. Replace the template with the code, then tap **Save**. Saving points out unknown API names, a call that lacks its `@uses` line, and header lines it can't read. A script you made and saved may use what its `@uses` line names. `@every` and `@at` lines set its timers on saving.
3. Run it: a command by its name (`<name> args…`), any script with `script run <name> [args…]`.
4. As needed: `script notify <name> on` lets `notify` post; `script pin <name>` shows its status line at the top of home; for a widget, run `widget add` and pick it in the **Kolahal** group. A script can't do any of these itself.

**A script as a file**: save it as `<name>.lua` or `<name>.js` (the file name is its name, with the rules above), then **Scripts → Add script → Import a file**, or put it in the folder picked with `script dir` and type `script import`. A script from a file arrives switched off: `script on <name>` switches it on and allows what it declares.
