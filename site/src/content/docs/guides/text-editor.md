---
title: Text editor
description: Open, edit and save any text file on the phone, in a window that stays open when you go home.
---

Kolahal has an editor for any text file on the phone: notes, Markdown, configuration files, JSON, code.
It is the same editor as the [script editor](/scripting/editor/), in a window of its own.

## Opening a file

```sh
edit                  # pick a file with Android's file picker
edit new              # make a new file; the picker asks where
edit new todo.md      # the same, with this name filled in
edit recent           # the files you opened lately; tap one to open it
edit todo.md          # open one of those by its name
```

- `edit` always needs Enter. A script can't run it.
- After you type `edit` and a space, the suggestion strip lists your recent files.
- `edit <name>` takes the start of a name too, when only one recent file starts with it.
- The picker shows every file, not only text files. Some apps mark a `.lua`, `.toml` or `.conf` file as
  binary, and you can still open it this way.

You can also open a file from another app. In a file manager, choose **Open with** and then
**Kolahal Editor**. It is offered for text files: plain text, Markdown, JSON, XML, YAML and similar.
If it isn't offered for a file, open the file with `edit`.

- Some apps share a file with **Open with** to read only. Then Kolahal can't save it in place: see
  [files Android gives read-only](#files-android-gives-read-only).
- Where a file manager has **Edit** or **Edit with**, Kolahal Editor is offered there too. That
  usually lets Kolahal save the file.

Kolahal asks for no storage permission. It reads and writes only the file you pick, or the file
another app hands to it.

## The file stays open

Each file opens in a window of its own. Android's Recents screen shows it with the file's name, apart
from the home screen.

- Press Home, use the phone, and come back: the text, the cursor, the scroll position and undo are as
  you left them.
- To come back, pick the window in Recents, or open the same file again (`edit todo.md`). The same
  window comes to the front.
- Back and `Esc` leave the editor like Home does. The file stays open.
- **Close** (× at the top right) closes the file. If you have unsaved changes, it asks: **Save**,
  **Discard** or **Cancel**.
- You can have several files open. Each is its own entry in Recents.

## Your text is not lost

Kolahal keeps a draft of your unsaved text in its own storage. It writes the draft a moment after you
stop typing, and again when you leave the editor.

- If Android closes the editor while it is in the background, open the file again. Your unsaved
  changes are back, and the editor says so. The undo history does not come back.
- `edit recent` also lists every file that has unsaved text, marked **unsaved changes**.
- If the file can no longer be opened (it was deleted, or Kolahal lost its permission), the editor
  opens your unsaved text anyway. Tap the message to save it as another file.
- **Discard changes** in the menu throws the unsaved text away and shows the file as it is. Undo
  brings the text back.
- A draft is deleted when you save or discard. Drafts are not in Android's cloud backup.

## Saving

**Save** (the disk icon at the top) writes your text to the file itself. A `*` after the file's
name means there are unsaved changes.

- The file keeps its line endings (LF or CRLF), and its line break at the end if it had one.
- Text is read and written as UTF-8.
- If another app changed the file after you opened it, Kolahal does not overwrite it. It asks:
  **Overwrite**, **Save as…** or **Cancel**.
- If the file can't be written, the message says why. Tap it to save your text as another file.
- **Save as…** writes the text to a new file. The editor then moves to that file, in its own window.

### Files Android gives read-only

Sometimes Android lets Kolahal read a file, but not write it:

- a file you picked under **Recent**, **Images**, **Videos** or **Documents** in the file picker,
  on Android 9 or on some phones;
- a file another app shares with **Open with** to read only. Samsung's My Files does this.

The editor then says "Read Only - Reopen again to edit". Your text
is safe: it is kept as a draft as usual. To save it in place:

1. Tap the message, or choose **Open from its folder…** in the menu. The file picker opens in the
   file's folder when Kolahal knows it.
2. Pick the same file there. It opens in a new window, with your unsaved text, and **Save** works.

Or use **Save as…** to write the text to another file.

A file picked under Recent saves in place where Android allows it. From Android 10, when the picker's
address refuses a save, Kolahal tries Android's media store too. Only if both refuse do you need
**Open from its folder…**.

If the file you pick in its folder is already open in another editor window, nothing moves: close
that window first, then try again.

When you come back to the editor and the file has changed meanwhile:

- with no unsaved changes, the editor shows the file as it is now;
- with unsaved changes, it tells you, and asks before it overwrites when you save.

## What it can open

- UTF-8 text only. A binary file, UTF-16 text, or text in an older encoding is refused, with the
  reason. Saving such a file as UTF-8 would damage it.
- Up to 500,000 characters and 50,000 lines. A bigger file is refused.
- From about 150,000 characters, typing gets slow. The editor tells you when you open such a file.
  Reading, searching and small changes still work.

## The menu

**More** (the three dots at the top) opens the menu. Under the file's name it shows the encoding,
the line endings (when they are not LF), and how many lines and characters the file has.

- **Save as…**
- **Open from its folder…** (only for a file Android may give read-only; see above)
- **Open a file…** and **New file…**: the other file opens in its own window. This one stays open.
- **Wrap lines** / **Don't wrap lines**
- **Discard changes** (only when there are unsaved changes)

### Wrapped lines

- Writing opens with wrapped lines and no line numbers: `.txt`, `.md`, `.org`, `.rst`, and a file
  with no ending such as `README`.
- Code and data open with line numbers, and scroll sideways: every other file.
- **Wrap lines** in the menu changes this for the open file.

## Editing

Editing works as in the [script editor](/scripting/editor/):

- the [symbol bar](/scripting/editor/#symbol-bar) above the keyboard, with undo, redo and **Ln** (Go to line);
- [find and replace](/scripting/editor/#find-and-replace), from the magnifier at the top;
- a message line that says what the last button did. Tap a message to close it.

Lua, JavaScript and JSON files are colored. In JSON, keys, text and other values each have their own color. Other files are plain text. The editor uses the
[theme and font](/guides/themes-and-settings/) of the terminal.

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| `Ctrl+S` | save |
| `Ctrl+Shift+S` | save as another file |
| `Ctrl+O` | open a file |
| `Ctrl+N` | make a new file |
| `Ctrl+W` | close the file |
| `Esc`, Back | close the find bar; otherwise leave the editor, with the file still open |
| `Ctrl+Z` | undo |
| `Ctrl+Shift+Z`, `Ctrl+Y` | redo |
| `Tab` | insert two spaces |
| `Ctrl+F` | find |
| `Ctrl+H` | find and replace |
| `F3`, `Shift+F3` | next and previous match |
| `Ctrl+G` | go to line |
