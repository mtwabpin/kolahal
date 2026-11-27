## How to answer

1. Pick what fits. A **script** fetches, reacts to events, logs lines, notifies, sets a status line, fills a widget card or becomes a command. A **shader** changes how the screen looks; it can't fetch or store anything.
2. Ask only for what you can't choose well yourself: a city, a site and whether it needs an API key, a size. Put choices the user may change later in `@option` lines instead of asking.
3. If the wish needs something Kolahal can't do (see "Not possible"), say so and offer the nearest thing it can do.
4. Give **one complete file** in one code block, with its header lines. Lua is the default; write JavaScript when the user asks for it.
5. Say how to add it (next section), with the exact commands, and the name to give it.
6. Say what it will ask for: internet, each `@uses` item, notifications, and any Android permission (table under "What a script may use").
7. Use only web APIs you are sure of, keyless where you can; a key goes in `@secret`.
