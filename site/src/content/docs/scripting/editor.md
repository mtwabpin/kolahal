---
title: Editor
description: The built-in script editor, its symbol bar and keyboard shortcuts.
---

There are three ways to open the editor:

- run `script edit <name>`,
- tap **Edit** in a script's sheet in the script manager,
- tap a script error.

The editor is a monospace text area with line numbers. It colours Lua and JavaScript code lightly,
in the terminal theme. [Parts of the app](/getting-started/parts/#script-editor) shows each part of
it, numbered.

- The title shows `name.lua`, with `*` while there are unsaved changes.
- **[save]**, **[run]** and **[close]** are next to the title. Run saves first.
- A long press on **[run]** (or `Ctrl+Shift+R`) opens **Run with**. Type words for its
  [`args`](/scripting/api/#args-and-argline), as when the script runs as a command. If the script
  has [`@on`](/scripting/overview/#event-triggers) or `@at` lines, you can also pick an event to
  simulate, with sample values (a charger at 64%, a message from Alice).
- A message line above the symbol bar shows what a button did:
  - **Saved** (or **No changes to save**),
  - **Saved · running…**, then the run's result (`Ran in 812ms · 3 lines below`),
  - how many replacements **[all]** made.

  A failed run stays there in red, as `Line 13: attempt to …`. Tap it to go to that line. Tap any
  other message to close it.
- A run's output appears under the code, and also in the log. Errors are red. The output stays
  until you tap **✕** or run the script again.
- Saving also tells you what else changed. A new or changed `@every` or `@at` line sets the script's
  timers (`Saved · runs every 1h`). Removing the line removes the timers it set.
- Saving also marks likely mistakes in red, with the line number. Tap it to go there. It finds:
  - a name the API doesn't have (`http.gte`, with the hint `http.get?`),
  - a call that needs a `@uses` line the script doesn't have (`clipboard.get`),
  - `@` lines Kolahal doesn't know or can't read (`@evrey`, `@at 25:00`, `@on unlok`).

  The script is saved in every case.
- When you open the editor from an error, it selects the line that failed and highlights it for a
  few seconds.
- Your changes are saved when you close the editor, press Back or switch to another app. If you
  close with unsaved changes, the log shows `Saved name.lua`.

## Symbol bar

Above the keyboard there is a row of keys that on-screen keyboards hide:

```
↶  ↷  ⇥  (  )  {  }  [  ]  "  '  =  .  ,  :  ;  #  -  ~  <  >  Ln
```

- `↶` / `↷` are undo and redo. They are dimmed when there is nothing to undo or redo.
- `⇥` inserts two spaces.
- `Ln` opens **Go to line**.

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| `Ctrl+S` | save |
| `Ctrl+R` | save and run |
| `Ctrl+Shift+R` | run with args or as an event |
| `Ctrl+Z` | undo |
| `Ctrl+Shift+Z`, `Ctrl+Y` | redo |
| `Tab` | accept the first completion while completions show; otherwise insert two spaces |
| `Shift+Tab` | insert two spaces |
| `Esc`, Back | save and close |
| `Ctrl+F` | find |
| `Ctrl+H` | find and replace |
| `Ctrl+G` | go to line |

## Autocomplete and inline docs

As you type, the editor suggests [API](/scripting/api/) names such as `http.get`, `rss.fresh`,
`store.set` and `device.battery`. For the call under the cursor, it shows the signature and a short
description. This is only a quick reminder. The [API reference](/scripting/api/) has the full
details and examples.

**[?]** in the top bar lists the whole API. It also lists every `@` line a script can start with:
[`@command`](/scripting/overview/#command-scripts), `@desc`, `@on`, `@uses`, `@every` and the
others, as in [describing a script](/scripting/overview/#describing-a-script). Tap an entry to
insert it at the cursor. An `@` line is inserted as a whole comment line.

## Editing elsewhere

For longer scripts, you may prefer a computer. You can:

- export a script as a file,
- sync a folder (`script dir`, `script export`, `script import`),
- edit the [backup file](/guides/backup/) directly.
