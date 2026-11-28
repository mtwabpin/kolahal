### Sizes and looks

The `@widget` size is where a card starts; the user can give it any of the six sizes and pick its look, and a script sets neither (an unknown size gives the kind's own). Each kind is made for some sizes and offers some looks:

| Kind | Made for | Looks |
| --- | --- | --- |
| `stat` | 1x1, 2x1, 2x2, 4x1 | Plain, Tonal, Bold, Split, Shape, Poster |
| `text` | 2x1, 2x2, 4x1, 4x2 | Plain, Tonal, Bold, Split, Shape |
| `list` | 2x2, 4x2, 4x4 | Plain, Tonal, Bold, Split, Shape |
| `progress` | 1x1, 2x1, 2x2, 4x1, 4x2 | Plain, Tonal, Bold, Split, Shape |
| `chart` | 2x1, 2x2, 4x2 | Plain, Tonal, Bold, Split, Shape |
| `countdown` | 1x1, 2x1, 2x2 | Plain, Tonal, Bold, Split, Shape, Poster |
| `buttons` | 2x1, 2x2, 4x1, 4x2 | Plain, Tonal, Split, Shape |
| `toggles` | 2x1, 2x2, 4x1 | Plain, Tonal, Split, Shape |
| `clock` | 2x1, 2x2, 4x1 | Plain, Tonal, Bold, Split, Shape, Poster |
| `agenda` | 2x2, 4x2, 4x4 | Plain, Tonal, Bold, Split, Shape, Now |
| `image` | 2x1, 2x2, 4x2, 4x4 | Plain, Tonal, Shape |
| `versus` | 2x1, 2x2, 4x1, 4x2 | Plain, Tonal, Bold, Split |
| `sky` | 2x1, 2x2 | Plain, Tonal, Bold, Split, Shape |

Plain is the default. Tonal fills the card with its `color`; Bold makes the main value large; Split draws connected tiles; Shape puts the main value in a shape; Poster makes the value so large the edge crops it; Now shows an agenda's event now and the free time to the next. While a card has a `note` or its last run failed, any look but Tonal draws as Plain; a `footer` doesn't.

A shape the user picks for the card wins over `shape`. It is left out for `countdown`, `sky`, `list`, `agenda`, `buttons`, `toggles`, `versus` and progress bars; a clock, a gauge and rings skip `slanted`, `arch`, `ghost` and `pill`. In a square card (1x1, 2x2) the shape is the whole card; in a wide one the main value sits in it.

### What a card shows at each size

A card draws for the space it gets: **small** (1x1), **wide** (one row: 2x1, 4x1) or **large** (two rows or more). There is no title line; a large text or list shows `title` over its content, and a stat without `label` uses `title` as its label. Small and wide cards have no last line. A `badge` sits at the top; 1x1, Poster, one-shape cards and cropped pictures leave it out.

- `stat`: small shows `value`, `unit` and `label` only. Wide puts `delta` by the value and `more` between it and the label, as many as fit. `points` draw only on wide and large; large has `more` and the `progress` bar at the bottom. `target` follows the value, or the label at 2x1.
- `text`: small has no `caption`; large shows the text bold.
- `list`: as many rows as fit, then "+3 more". Under 200 dp wide (a 2x2 on most phones) `detail` is left out. Small and wide show only the `lead`, when there is one; large shows it over the rows.
- `progress`: without `style` the space picks a ring or a bar. A wide ring has the title and `label` beside it; a large one has `label` under it. Stage `details` show at 4x2 and up.
- `chart`: `labels` show on large only, with `value` large and `label`.
- `countdown`: the `from` bar is left out on small, takes the label's place on wide, and is at the bottom with dates at its ends on large.
- `buttons`: 2 fit at 2x1, 4 at 2x2 and 4x1, 8 at 4x2; a button is never under 48 dp, so later ones are left out: put the most used first. A button's or switch's `value` is left out in icon-only and low cells.
- `clock`: small, or one zone: one large time. Wide: zones side by side. Large: a row a zone.
- `image`: `caption` on large only. Cropped (`fit = "crop"`, the default) on wide and large, the picture covers the card.
- `agenda`: as many events as fit, then "+3 more".

### Options of a card

The first card of a script reads the script's options (`script set`, the script's sheet). Another card, with its `event.instance`, reads its own values where the user set them on the card's settings page, under **This card**, and the script's elsewhere. Saving an option runs the script again for each card that reads it; so does switching on an access it uses. The others fill on `"widget"` runs and taps.

The card's settings page also has its color (the script's own or a role), its shape, its scene, and **What shows**: Name, Icon and **Last updated**.

### When it runs, and what the card says

- A `"widget"` run comes only while the widgets page shows the card: when it has no content yet, or content older than `staleAfter` (checked each minute). After a failed run the next try waits `staleAfter` too. `refresh` runs it again every so many seconds while the card shows.
- `staleAfter` and `refresh` out of range are brought into range, not refused.
- A card never has two runs at once; a run asked for meanwhile is dropped.
- A card with no content yet shows a loading shape, then "Nothing to show" after 20 seconds.
- A failed run with no content yet shows the error's first line and its line number; an older failure reads "Failed 2 d ago". A timer run's failure doesn't mark the card; its error is logged once, until a run works.
- The age ("Updated 3 min ago", "Updated yesterday") shows on a large card once the content is older than `staleAfter`. With **Last updated** on, it always shows, also on a wide card with room for it; never at 1x1, nor in Bold, Split, Shape or Poster. The last line is the `note`, the `footer` (cut first), then the age; the footer shows where the age may.

### Taps

- A tap with `args` runs the script with `event.name` `"action"`, `event.widget` true, `event.label` when the tap has a `label`, and `event.instance` on a further card. It counts as a run the user started.
- One run each 2 seconds for a card and its words: a second quick tap waits, then runs. A switch shows it is working, and taps meanwhile are left out.
- A long press on any card opens Arrange widgets; it is never sent to the script.
- With the script off, or without its `@widget` line, a tap opens Scripts.

### Mistakes it refuses

Each error starts with `widget:` and names the place: `widget: a stat widget has no field 'colour'; use kind, title, …`; `item 2 of items: …`; `… must be text, not a table`; `… must be a time in seconds, not in milliseconds: divide it by 1000`; `items has 9 buttons: give 8 at most`. Also refused: more than 6 switches, an empty `items` of buttons or switches, `sides` other than 2.

`widget.show` checks the description even when it returns false (no card placed, or its access off), so `script run <name>` finds a mistake before the card is placed. Code typed at the prompt can't fill a card.
