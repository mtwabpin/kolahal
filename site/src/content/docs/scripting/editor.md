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
- Next to the title are icon buttons: **Find** (magnifier), **API reference** (question mark),
  **Save**, **Run** (play) and **Close** (×). Run saves first. TalkBack reads each button's name.
- A long press on **Run** (or `Ctrl+Shift+R`) opens **Run with**. Type words in **Arguments**: the
  script gets them in [`args`](/scripting/api/#args-and-argline), as when it runs as a command. If
  the script has [`@on`](/scripting/overview/#event-triggers) or `@at` lines, you can also pick an
  event under **Simulate event**, with sample values (a charger at 64%, a message from Alice).
- A message line above the symbol bar shows what a button did:
  - **Saved** (or **No changes to save**),
  - **Saved · running…**, then the run's result (`Ran in 812ms · 3 lines below`),
  - how many replacements **Replace all** made.

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
undo  redo  ⇥  (  )  {  }  [  ]  "  '  =  .  ,  :  ;  #  -  ~  <  >  Ln
```

- **Undo** and **redo** are the two curved arrows. They are dimmed when there is nothing to undo or redo.
- `⇥` inserts two spaces.
- `Ln` opens **Go to line**.

## Find and replace

The magnifier at the top opens the find bar. Type to find; the matches are marked in the code.

- **Aa** matches upper and lower case, or not.
- The up and down arrows go to the previous and next match.
- The button with two arrows opens a second row to replace: type the new text, then tap
  **Replace** for this match or **Replace all**.
- × closes the find bar.

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

## Reference

**API reference** (the question mark in the top bar) opens the reference: a page over the code,
for the script's language. **Back** returns to the script, with the text and the cursor as they
were.

At the top is one search field, then a tab for each part:

- **Functions** lists the whole API, in groups: each namespace (`http`, `store`, `fmt`…) under its
  own line, and the functions outside a namespace (`log`, `print`, `args`) under **Globals**. The
  chips above the list show one group; **All** shows them all. Each `fmt` function also shows what
  it makes, as the log draws it: a bold word for `fmt.bold`, a row of tags for `fmt.tag`.
- **Directives** lists every `@` line a script can start with:
  [`@command`](/scripting/overview/#command-scripts), `@desc`, `@on`, `@uses`, `@every` and the
  others, as in [describing a script](/scripting/overview/#describing-a-script). The language's
  keywords are at the end.
- **Widgets** has a row for each kind of [widget](/scripting/api/#widgets) card, with a small
  picture of it.
- **Log lines** has a row for each example of the lines that
  [`log`, `md.log` and `fmt`](/scripting/api/#output) make.
- **Icons** shows every [widget icon](/scripting/api/#icons) with its name, in groups.

Tap a function to insert it at the cursor, ready for its arguments. An `@` line goes in as a whole
comment line, above the cursor's line. An icon's name goes in quotes, unless the cursor is already
after a quote.

Tap a widget or log example to open it. Its view shows the card or the lines as Kolahal draws
them, then its code in the script's language: the same examples as the scripting guide.

- **Example** picks the guide's example or a variant of it: more rows or switches, another style.
  Only kinds with variants have it.
- **Size** picks one of the sizes the kind is made for.
- **Look** draws the card in one of the [looks](/guides/widgets/#looks) that the person who places
  the widget can choose. Only the looks that change this kind of card are offered.
- **Copy** puts the code on the clipboard. **Insert** puts it in at the cursor, on lines of its
  own, and returns to the script. Both take the code of what you chose.

The card and the lines only show: a tap on one of their buttons or links does nothing. TalkBack
reads a card as its description.

Typing in the search looks in every tab at once: names, signatures, descriptions, example code and
icon names. The results come in groups by tab, with how many each has, and the words found are
marked. A tap on a result does what it does in its tab. **Back** from an example opened there
returns to the results; clearing the search returns to the tabs.


## Editing elsewhere

For longer scripts, you may prefer a computer. You can:

- export a script as a file,
- sync a folder (`script dir`, `script export`, `script import`),
- edit the [backup file](/guides/backup/) directly.
