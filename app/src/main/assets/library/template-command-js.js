// @title Template: command (JavaScript)
// @desc A starting point for a command you type, in JavaScript: reads its words, logs lines that do something when tapped
// @author Kolahal
// @version 1.0
// @command
// @usage [name]
// @tags template, javascript
// @requires 0.4.1
//
// The JavaScript version of template-command. Try `template-command-js`, then `template-command-js Ada`.
// Edit this one, or start your own with `script new <name>.js --cmd` and copy what you need from here.
//
// The @command line makes the file's name a command you type. The @usage line says what may follow
// the name, the way a built-in command's usage reads ([x] optional, <x> needed, a|b one of): Kolahal
// suggests it as you type, and knows when the line is complete.
//
// The script runs inside an async function: `await` the http and rss calls, and `return` to stop early.

// The words after the name: `args` is an array of them, `argline` the same words as one string.
const name = argline || "world";

// log(title, body, ctx) adds a line to the log: the title, and the body (if any) muted under it.
// With a body, the title is bold, as a heading. The object at the end decides what tapping and
// long-pressing the line do: url opens a link, copy is what "Copy text" copies.
log(`Hello, ${name}!`, `You gave ${args.length} ${args.length === 1 ? "word" : "words"}`, { copy: name });

// md.log is log reading Markdown: **bold**, *italic*, `code`, and lists in the body.
md.log("**Try**", "- `template-command-js Ada`\n- `template-command-js` on its own");

// fmt styles parts of any line, and text from the web needs no escaping in it. Colours come from
// the theme by role (good, warn, bad, muted, accent, …), so they read in every theme.
log("The scripting guide", fmt.color("muted", "mtwabpin.github.io/kolahal"), {
  url: "https://mtwabpin.github.io/kolahal/scripting/overview/",
});

// Whatever the script returns is printed as its result line (an object prints as JSON).
const now = new Date();
return `It's ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
