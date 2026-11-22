# Kolahal TTY mode: architecture

Status: design, written on 21 November 2026 against branch 0.5.17 (minSdk 28, compileSdk 37, Compose).
The work is tracked in [tty.org](tty.org).

## The maintainer's decisions (21 November 2026)

These override anything below that says otherwise.

- **TTY is an opt-in style, not a second GUI.** It is there for people who want a real terminal and
  accept what that costs. It does not have to do everything the GUI does: whatever a character grid
  can't do well is left out of TTY.
- **No images.** Log images, person pictures, app icons and notification icons are not drawn in TTY.
  An image shows as its alt text (`[image: a graph of the week]`, a link when it has one); a person as
  their initials; an app or notification source as its name. `CellImage`, `tty.images` and the
  half-block image drawing below are dropped.
- **One font size on the grid.** Nothing is drawn larger or smaller than the grid's cells: a stat's
  big value is bold (or inverse) at the grid size, a heading is bold. `font.size` and pinch-to-zoom
  still set the size of the whole grid.
- **The widgets page always stays GUI.** Only home's log page and its parts (header, pinned line,
  prompt, suggestions, key bar, sheets) have a TTY form.
- **No GUI catch-up.** The GUI does not get the scroll thumb or the selection mode because TTY has
  them. Where the parity checklist (§12) lists something only the GUI can do (images, pictures,
  icons, mixed sizes), TTY's answer is the plain form above, and that counts as done.
- **Shaders come after TTY**, as their own option (§7).

## 0. The decisions in one page

| Question | Decision |
|---|---|
| What is shared | Everything in the Model and in `terminal/`: `LogEntry`, `buildRows`, `LogPager`, `LogSearcher`, `LogLineActions`, `PromptActions`, `HomeSession`, the view model's flows. Plus one new shared layer: a renderer-agnostic `LineModel` (what a row shows and what can be tapped), which both renderers consume. |
| What is per mode | Only drawing and pointer geometry: `ui/Log*.kt`, `Prompt`, `Header`, … for GUI; `ui/tty/*` for TTY. |
| Where the cell layout lives | Pure Kotlin in `terminal/tty/` (no `android.graphics`, no `compose.ui`), JVM-tested. Painting lives in `ui/tty/`. No new top-level package, so `LayersTest` needs no change and stays true. |
| The switch | `home.style = gui | tty` (a `ConfigKey.Choice`), read in `TerminalScreen` through `configVersion`. Live, no restart; scroll anchor, input, focus, fold state and search survive the switch because they are hoisted above both renderers. |
| Rendering engine | A `LazyColumn` whose items are log entries, each item one draw-only node that paints its cached `CellBlock` with `android.graphics.Canvas`. Item heights are whole cell rows; every x position comes from one shared `GridMetrics`. Header, pinned line, prompt, suggestions and key bar are the same kind of node. Full-screen screens (matrix…) paint a dense `CellBuffer` on one canvas. Not one giant canvas with its own scrolling, and not Compose `Text`. |
| Box drawing, blocks, sparklines | Drawn as geometry by the painter (U+2500–259F), never as font glyphs, so they join at every font, size and line height. |
| Width of a glyph | A pure `cellWidth(cluster)` table (0, 1 or 2 cells) decides layout. The painter then makes the pixels fit the cells (scale or center), so layout never depends on a device's fallback fonts. |
| Colors | Only the 14 `Palette` roles, as `Ink`. Dim is a color (blend toward bg), not layer alpha. |
| Accessibility | One semantics node per log row built from `LineModel.spoken()` and `LineModel.targets`, the same source the GUI uses. The canvas itself is not exposed. |
| Screens | `TtyScreen` interface over `CellBuffer`, pure and deterministic; hosted by `TtyScreenHost`. The commands (`matrix`, `clock`, `pipes`, `sl`, `top`, `boot`) work in both home styles. |
| Shaders | `RenderEffect.createRuntimeShaderEffect` on the one `graphicsLayer` that wraps the TTY home (API 33+). The grid knows nothing about shaders. API 28–32 get a built-in CRT look drawn without AGSL (scanlines, vignette, glow; real bloom from API 31). |
| Shipping | `home.style` stays out of Settings until the parity checklist (§12) is complete. Screens ship first because they are complete on their own. |

Two facts about today's GUI log that the brief lists as existing: it has no scroll thumb and no
free text selection (copy is by swipe or by "Copy text"/"Copy output" in the long-press sheet).
TTY gets both (§3.4, §2.7); the thumb should then be added to the GUI too, or it is a TTY extra.

## 1. The seam

### 1.1 What exists today

```
data/        LogEntry, LogContext, StyledText (markers → Line/Span/Row/Meter/Stat/Columns), Config, Keys
terminal/    buildRows(entries) → List<LogRow>      (dimmed, gutter, unread, blockStart, showSource, showTime, itemGap)
             LogPager, LogSearcher, LogLineActions(onTap, actionsFor, swipeCopy…), PromptActions, HomeSession
ui/          TerminalScreen → LazyColumn → LogLine(row) → renderLine()/RichBody/LogTable/Meters/ActionPills/AppGrid/LogImage
```

`buildRows` is already renderer-agnostic and stays exactly as it is. The gap is one level lower:
`LogLine` (ui/LogView.kt:125–510) and `renderLine` (ui/LogLineText.kt:141) decide *what a row
consists of* while they draw it: whether the time shows, whether the row is the two-column or
name-column form, the app grid, where the text ends and the tables and bars start
(`StyledText.splitAtBlocks`), the built-in bar (`LogContext.BAR`), the buttons
(`LogContext.actionsOf`), the icon that replaces the source name, the person's picture, whether
the row is tappable, whether it may fold, the `[W]` profile tag, the unread mark. A second
renderer that repeats those decisions will drift. So they move.

### 1.2 The new shared layer: `LineModel`

`terminal/LineModel.kt`, pure, JVM-tested. One function, called once per row and cached by both
renderers:

```kotlin
package com.mtwabpin.kolahal.terminal

/** The theme's roles, by name. ui/ maps each to a Color (GUI) or to a cell's ink (TTY). */
enum class Ink { BG, SURFACE, FG, DIM, PROMPT, ERROR, INFO, LINK, SCRIPT, PERSONAL, WORK, PRIVATE, GOOD, WARN }

/** A piece of text with its look. [url] makes it a link target. */
@Immutable data class Piece(
    val text: String,
    val ink: Ink? = null,                       // null: the line's own ink
    val styles: Set<StyledText.Style> = emptySet(),
    val url: String? = null,
)

@Immutable data class LineModel(
    val id: Long,
    val kind: LogKind,
    val gutter: Gutter,
    val ink: Ink,                               // the line's own ink (FG, PROMPT for a command, ERROR…)
    val dimmed: Boolean, val unread: Boolean,
    val gapAbove: Gap,                          // NONE, ITEM, GROUP (from blockStart / itemGap)
    val time: Long?,                            // null: no time on this row (log.time decided here)
    val source: Source?,                        // Name(text, ink) | Icon(key, fallbackName)
    val person: Person?,                        // name, photo, big
    val body: Body,
    val blocks: List<Block>,                    // what follows the words: tables, bars, stats, charts, text
    val bar: StyledText.Meter?,                 // a built-in command's bar after the words
    val image: String?,
    val buttons: List<Button>,                  // ctx.actions: label, args, done
    val tappable: Boolean,                      // ACTIONABLE_KEYS or a command
    val mayFold: Boolean,
) {
    sealed interface Body {
        data class Words(val lines: List<Para>) : Body          // Para: level, prefix, pieces, title/body role
        data class TwoColumns(val name: List<Piece>, val desc: List<Piece>) : Body
        data class NameColumns(val name: List<Piece>, val desc: List<Piece>, val args: String?, val nameWidthOf: String) : Body
        data class AppGrid(val apps: List<AppRef>) : Body       // pkg, cls, user; resolved by the renderer's `apps(entry)`
    }
    sealed interface Block {
        data class Text(val lines: List<Para>) : Block
        data class Table(val rows: List<StyledText.Row>) : Block
        data class Meters(val rows: List<StyledText.Meter>) : Block   // consecutive bars, aligned as one
        data class Stats(val items: List<StyledText.Stat>) : Block
        data class Chart(val columns: StyledText.Columns) : Block
    }
}

/** Everything a row can do, in reading order. Both renderers build pointer handling AND semantics from this. */
sealed interface Target {
    data object Line : Target                    // onTap / onLongPress of the entry
    data class Link(val url: String, val text: String) : Target
    data class Button(val index: Int, val label: String, val done: Boolean) : Target
    data class App(val index: Int) : Target
    data object Fold : Target
}

data class LineOptions(val timeMode: TimeMode, val clock24: Boolean, val foldLines: Int, val boxedTables: Boolean)

fun lineModel(row: LogRow.Entry, o: LineOptions, hasIcon: (String) -> Boolean): LineModel
fun LineModel.targets(): List<Target>
fun LineModel.spoken(timeText: String?): String   // what TalkBack reads for the row; tables and bars as words
```

`TimeMode` moves from `ui/LogView.kt` to `terminal/` with it (ui already imports terminal).

The GUI's `LogLine` is changed to take a `LineModel` and to draw from it. This is a mechanical
refactor: the `remember { … }` blocks that compute `richRest`, `bar`, `actions`, `actionable`,
`showTime`, `isGrid`, `desc`, `iconKey` become reads of the model; `renderLine` builds its
`AnnotatedString` from `Body.Words` instead of from the entry. Pixels do not change; the existing
`LogLineTest`, `LogLineTextTest`, `DimmedLineTest`, `LogGalleryTest` must stay green untouched.

Why a model and not "TTY just reuses buildRows": `buildRows` says which rows exist; it does not
say what a row is made of. Sharing only `buildRows` leaves ~400 lines of per-row decisions
duplicated. Why not a full "document" (rows already wrapped, positioned): wrapping and geometry
are exactly what differs between a proportional layout and a cell grid; a shared layout would
force one renderer's constraints on the other.

### 1.3 Other state that must be hoisted (so both modes share it)

| State | Today | After |
|---|---|---|
| Scroll follow, jump-to-line flash, load-older near the top, "away from bottom" | inline effects in `TerminalScreen` (lines 183–290) over a `LazyListState` | `rememberLogScroll(vm, rows): LogScroll` in `ui/LogScroll.kt`, holding the `LazyListState`; both renderers use a `LazyColumn` with it, so the anchor (an entry id key) survives a style switch |
| Fold open/closed | `rememberSaveable(id)` inside each `Foldable` | `FoldState` (a saveable set of open entry ids) created in `TerminalScreen`, passed down |
| Menu (long-press sheet), swipe-copy notice, undo row | `TerminalScreen` locals | stay there; the sheet composable is chosen by style |
| Header content | computed inside `Header` composable from `Status`, config | `terminal/HeaderModel.kt`: `headerParts(status, unlocks, profiles, todoCount, config): HeaderParts` (left/right lists of `Part(kind, text, ink, target, description)`); the GUI maps `kind` to its icons, TTY to text |
| Key bar | `KeyBarKey` + spec strings already in `terminal/` | unchanged |
| Suggestions | `vm.suggestions(text)` → `Suggestion` already in `terminal/` | unchanged |

None of this adds a composable that takes the whole `TerminalViewModel`. `TtyHome` receives the
same narrow things `TerminalScreen` already passes down (`vm::onTap`, `vm::actionsFor`, flows'
values); the new files must not appear in the "52 composables take the whole view model" count
in docs/tasks.org. `TerminalScreen` itself is split first (§11, step 0) into state (`HomeState`)
and two bodies, so it does not grow.

### 1.4 The switch

```kotlin
val HOME_STYLE = ConfigKey.Choice("home.style", "gui", listOf("gui", "tty"),
    "How home is drawn: gui (the log as text, chips and cards) or tty (a strict character grid, like a terminal)")
```

- Name: `home.style`, not `log.style`. The mode covers the header, the pinned line, the prompt,
  suggestions and the key bar, not only the log.
- Applied in `TerminalScreen`: `when (remember(configVersion) { config[Keys.HOME_STYLE] })`.
  `HomeState` (list state, fold state, menu, focus requester, search) is created above the
  `when`, so a switch recomposes the body only. The prompt's text is in the view model already.
- Settings' live preview (`TerminalViewModel.Peek`) passes through unchanged, so every existing
  setting previews in TTY as well.
- Also a command: `tty on|off` as an alias in the Model's command catalog.
- Later keys, all under `tty.`: `tty.cursor` (block, bar, underline), `tty.blink`, `tty.images`
  (picture, blocks, off), `tty.crt`, `screen.shader`, `tty.boot`. (The screensaver became
  `screen.saver` in step 5.)

### 1.5 Packages

```
terminal/LineModel.kt, HeaderModel.kt            shared, pure
terminal/tty/   Cell.kt  CellWidth.kt  CellBlock.kt  CellBuffer.kt
                layout/  LineLayout.kt  Wrap.kt  TableLayout.kt  MeterLayout.kt  StatsLayout.kt  ChartLayout.kt
                         HeaderLayout.kt  PromptLayout.kt  SuggestionLayout.kt  KeyBarLayout.kt  SheetLayout.kt
                Selection.kt
                screens/ TtyScreen.kt  Matrix.kt  Clock.kt  Pipes.kt  Sl.kt  Top.kt  Boot.kt
ui/tty/         GridMetrics.kt  GridPainter.kt  BoxGlyphs.kt  TtyPalette.kt
                TtyHome.kt  TtyLog.kt  TtyBlock.kt  TtyHeader.kt  TtyPinned.kt  TtyPrompt.kt  TtySuggestions.kt
                TtyKeyBar.kt  TtySheet.kt  TtyBands.kt  TtySelector.kt  TtyScreenHost.kt  TtyCrt.kt  TtyShader.kt
```

`terminal/tty/` may import `data/` and `terminal/`, never `ui/` (LayersTest's
`theTerminalNeverReachesTheScreenCode` walks subfolders, so this is enforced for free). Add one
assertion to LayersTest: no file under `terminal/tty/` imports `android.graphics` or
`androidx.compose.ui` — that is what keeps the layout JVM-testable.

## 2. The rendering engine

### 2.1 Options weighed

| | A. One Canvas, own scroll | B. Compose `Text` per row | C. `LazyColumn` of canvas-drawn blocks (chosen) | D. Custom `View`/`SurfaceView` |
|---|---|---|---|---|
| Strict grid | yes | no (fallback fonts, emoji, ligatures, letter-spacing rounding, box glyph gaps) | yes | yes |
| Virtualization, fling, overscroll, nested scroll with the pager | rewrite all | free | free | rewrite |
| TalkBack rows, scroll actions, focus order | virtual tree by hand | free | free (one node per item) | `ExploreByTouchHelper` by hand |
| Existing tests (`line-<id>`, `log`, scroll-follow) | break | keep | keep | break |
| Shaders via `RenderEffect` | yes | yes | yes (effect is on the parent layer) | no for `SurfaceView` |
| Full-screen animation | natural | poor | uses A for screens only | natural |

C gives the pixels of A (a block's draw code is the same code a single canvas would run) with the
scrolling, accessibility and test surface of the existing log. The one thing A does better — a
single buffer for full-screen programs — is kept for screens, where there is no scrolling,
selection or per-row semantics to lose.

### 2.2 Cells

```kotlin
package com.mtwabpin.kolahal.terminal.tty

/** A cell's look in 32 bits: fg ink (4), bg ink (4), flags. No colors, only roles. */
@JvmInline value class CellStyle(val bits: Int) {
    companion object {
        const val BOLD = 1 shl 8; const val FAINT = 1 shl 9; const val ITALIC = 1 shl 10
        const val UNDERLINE = 1 shl 11; const val STRIKE = 1 shl 12; const val INVERSE = 1 shl 13
        const val BLINK = 1 shl 14
        fun of(fg: Ink, bg: Ink = Ink.BG, flags: Int = 0) = CellStyle(fg.ordinal or (bg.ordinal shl 4) or flags)
    }
}

/** A run of cells on one row that share a style. [src] maps back to the entry's plain text (for copy, search, selection). */
class CellRun(val col: Int, val cells: Int, val text: String, val style: CellStyle, val src: Int, val shaped: Boolean)

/** A picture that occupies whole cells (a log image, an app icon, an avatar, a notification icon). */
class CellImage(val col: Int, val row: Int, val cols: Int, val rows: Int, val source: ImageRef, val tint: Ink?)

/** A tappable area: one or more cell rectangles (a link that wraps has two). */
class CellTarget(val target: Target, val rects: List<CellRect>)

/** One laid-out log row (or header, prompt…). Immutable; cached. */
class CellBlock(
    val cols: Int, val rows: Int,
    val lines: Array<Array<CellRun>>,          // per grid row
    val softWrap: BooleanArray,                // row i continues on row i+1 (copy joins them)
    val images: List<CellImage>,
    val targets: List<CellTarget>,
    val foldedRows: Int,                       // rows hidden behind "▾ N more lines"; 0: none
) {
    fun hitAt(col: Int, row: Int): Target?     // innermost target, else Target.Line
    fun srcAt(col: Int, row: Int): Int         // plain-text offset, for selection and search marks
    fun dump(): String                         // rows as text, for golden tests
}

/** A dense grid for screens: one int for the cluster, one for the style, per cell. */
class CellBuffer(cols: Int, rows: Int) {
    val cols: Int; val rows: Int
    fun put(col: Int, row: Int, text: String, style: CellStyle)
    fun fill(rect: CellRect, cp: Int, style: CellStyle)
    fun clear(style: CellStyle = CellStyle.of(Ink.FG))
    fun scrollUp(n: Int)
    val dirty: BitSet                          // rows changed since the last paint
}
```

Runs, not per-cell objects, for log blocks: a 40-column screen with 300 cached blocks is about
1 MB (budget: 2 MB, LRU). `CellBuffer` is dense because screens touch single cells every frame:
60×45 cells are 2 × 2700 ints.

### 2.3 Measuring: `GridMetrics` (ui/tty)

- One instance per (typeface, font size px, line height px). Rebuilt when `Term.fontFamily`,
  `Term.fontSize`, `Term.lineHeight` or the density changes; that also bumps the layout cache key.
- `cellW = round(paint.measureText("0"))` in whole pixels. `paint.textScaleX = cellW / advance`
  takes up the rounding (under 4 %), so 80 cells are exactly 80 × cellW.
- `cellH = max(ceil(descent − ascent), round(log.line-height in sp → px))`, whole pixels.
  `baseline = (cellH − (descent − ascent)) / 2 − ascent`. `log.line-height` therefore keeps its
  meaning (10..56), `font.size` too, and pinch-to-zoom keeps working (cols change → relayout).
- `cols = floor(contentWidth / cellW)`; the leftover pixels are split into the left and right
  margins. Header, log, prompt and key bar all use the same `originX`, so columns line up from
  the top of the screen to the bottom.
- Ligatures off: `paint.fontFeatureSettings = "'liga' 0, 'calt' 0"` (Fira Code, JetBrains Mono).
- A font is "monospace" if `i`, `M` and space measure the same. An imported font that is not
  still works (each glyph is centered in its cell); `font` prints a note that it is not monospace.
- `font.sans` has no meaning on a grid. In TTY the setting shows as unavailable with the reason
  "TTY is monospace"; its stored value is kept for GUI.

### 2.4 Width of a glyph: `CellWidth` (terminal/tty, pure)

Layout must give the same answer on every phone and in JVM tests, so it cannot ask a `Paint`.

- Text is split into grapheme clusters by a small table-driven segmenter (Extend, ZWJ sequences,
  variation selectors, regional-indicator pairs, emoji modifiers, Hangul). A device test compares
  it with `android.icu.text.BreakIterator` over a corpus.
- `cellWidth(cluster)`: 0 for a lone combining mark or zero-width character; 2 for East Asian
  Wide/Fullwidth and for emoji presentation (including text + VS16 and flags); 1 otherwise.
  Tables generated from the Unicode data files, checked in as a compact range array.
- A tab advances to the next multiple of 8. Control characters render as `␀`-style pictures or
  are dropped (as `StyledText.strip` already does for markers).
- A wide cluster never splits across the row end: it wraps whole, and the last cell is padding.
- Painting makes the glyph obey: the painter measures the cluster in the font that Android
  picks; if it is wider than its cells it is scaled down horizontally (`canvas.scale`) to fit, if
  narrower it is centered. So an emoji font or a CJK fallback with odd advances cannot push the
  next cell.
- Complex scripts (Arabic, Hebrew, Indic, Thai): letters must be shaped together, so cells cannot
  be drawn one by one. `Wrap` groups such text into *shaped runs*: the run takes
  `clusterCount`-many cells for layout, is drawn with one `drawTextRun` (shaping on), and is
  scaled horizontally to exactly its cell span. Hit-testing inside it uses
  `Paint.getOffsetForAdvance`. `CellRun.shaped` marks these.

### 2.5 Layout: `LineModel` → `CellBlock` (terminal/tty/layout, pure)

Row frame, left to right (mirrored in RTL, §2.9):

```
│ text text text text text text text text      12:04
│ continued text
^gutter (col 0, `│` in the gutter's ink; space when log.gutter off → text starts at col 0)
  ^text from col 2                              ^time: right-aligned, DIM, only on the row's first line
```

| Model part | Cells |
|---|---|
| Gap above (`log.spacing`, `log.item-spacing`) | whole rows only: group gap = `round(dp / cellH)` rows, but at least 1 when the setting is > 0; item gap = `round(dp / cellH)` (0 at the default 5 dp). The gutter `│` runs through an item gap, not through a group gap, as now. |
| Gutter | `│` in PROMPT / SCRIPT / ERROR ink (drawn by BoxGlyphs, so it is continuous down a group). |
| Words | greedy wrap at spaces to the text width; a word longer than the width breaks at a cluster; list levels indent 2 cells each with the prefix (`•`, `◦`, `3.`) hanging; a script line's title is BOLD and its body DIM on the following rows. |
| Unread | `●` in the line's ink at the text column (the bullet the GUI uses), and the divider row `── 3 new since you left ──` filled to the width with `─`. |
| Day | `── Tue 17 Nov ──────────`, DIM. |
| Styles | BOLD → bold face; ITALIC → italic (skew if the family has none); UNDERLINE, STRIKE → line flags; CODE → bg SURFACE; TAG → INVERSE with one padding cell each side (` done `); color role → fg ink. |
| Links | UNDERLINE + LINK ink, a `CellTarget(Link)` with one rect per wrapped piece. |
| Profile tag `[W]`, `[P]` | INVERSE in WORK / PRIVATE ink. |
| Source name / notification icon | right after the text, DIM name, or a 2-cell tinted `CellImage`. |
| Time | `log.time` as today; occupies `len + 1` cells at the right edge of the first row; text wraps short of it on that row only. |
| Dimmed (`log.dim`) | FAINT flag on every run of the block (color blend, §5), not alpha. |
| TwoColumns / NameColumns | name column = widest name (`namecol`) or 40 % of the width, desc wraps in its own column, `args` DIM under it. |
| AppGrid | `ls`-style columns: each app `icon(2 cells) name`, column width = longest label + 2, as many columns as fit; one `Target.App` per entry. |
| Table (`log.tables boxed`) | `┌─┬─┐ │ │ ├─┼─┤ └─┴─┘`; header row BOLD with `╞═╪═╡` under it; right-aligned cells padded on the left. `plain`: two spaces between columns, header underlined. Widths: natural if they fit; else shrink the widest columns and wrap inside cells (a table row may be several grid rows); if even 3 cells per column do not fit, the table turns into records (`header: value` lines, one blank row between rows). |
| Meter(s) | `label  start ▕████████▌       ▏ end`; label, start and end columns sized over the consecutive bars so tracks align (as the GUI does); fill in eighths; a range (`from > 0`) is drawn by geometry, so both ends have eighth precision; track ink DIM, fill ink the meter's role or PROMPT. |
| Built-in bar (`ctx.bar`) | the same track after the words, to the right edge short of the time. |
| Stats | values BOLD on one row, labels DIM under them, in equal columns; wraps to a second pair of rows when they do not fit. |
| Chart (`fmt.columns`) | sparkline of `▁▂▃▄▅▆▇█`, 2 rows tall by default (16 levels), one cell per column (2 when there is room), newest column in PROMPT, future slots `·` DIM, labels on the row under it. |
| Buttons (`ctx.actions`) | `[ Snooze 30 min ]  [ Done ]` in PROMPT, on their own row(s); the done one `[✓ Done ]` DIM, the others removed (as now). |
| Image | a `CellImage` as wide as the text column, height in whole rows from the aspect ratio (max 12 rows); `tty.images blocks` draws it as `▀` half-blocks in nearest role colors; `off` shows `[image]` as a link. |
| Person | a `CellImage` 4×2 cells (call with details) or 2×1 (one line); initials when there is no photo. |
| Fold (`log.fold`) | after N rows: `▾ 23 more lines` (DIM, `Target.Fold`); open: `▴ fold`. Rows are exact here, where the GUI estimates. |

Long lines: `Wrap` is incremental. While a row is folded only `foldLines + 1` rows are laid out
(the 64 K-character line case that `keepWhole` handles today); the hidden row count is an
estimate from the remaining length until opened.

### 2.6 Painting: `GridPainter` (ui/tty)

```kotlin
class GridPainter(val m: GridMetrics, val palette: TtyPalette) {
    fun draw(canvas: android.graphics.Canvas, block: CellBlock, rowFrom: Int, rowTo: Int,
             selection: CellRange? = null, marks: IntArray? = null, pressed: CellTarget? = null)
    fun draw(canvas: android.graphics.Canvas, buffer: CellBuffer)      // screens
}
```

- Order per row: backgrounds (only cells whose bg is not BG, so `log.wallpaper` still shows
  through) → box/block glyphs as geometry → text runs → underline/strike → images → selection
  and press inversion.
- A run of single-cell clusters from the primary font is one `drawText` call (advances equal
  `cellW` by construction). Other clusters are drawn one by one at their cell.
- `BoxGlyphs`: U+2500–257F (lines, joints, double lines, rounded corners), U+2580–259F (blocks,
  shades, eighths), plus `▕▏`. Line weight = `max(1 px, cellW / 8)`, centered on the cell's
  mid-lines, extended to the cell edges. This is what kitty, Alacritty and WezTerm do, for the
  same reason: font glyphs leave gaps when the line height is not the font's.
- No allocation in `draw`: one `Paint`, one `TextPaint` per face (regular, bold), reused arrays.
- No bitmaps or glyph atlas: Android's text stack caches glyphs already, a theme change needs no
  invalidation of ours, and memory stays flat.
- Blink (cursor, BLINK flag) is a 530 ms toggle that invalidates only the nodes that contain a
  blinking cell; it stops when home is not resumed, in battery saver, and when animations are
  off system-wide.

### 2.7 Scrolling and the list

- `TtyLog` is a `LazyColumn(reverseLayout = true, state = logScroll.listState)` with the same
  keys (`LogRow.key`) and the same extra items (empty hint, `▴ 120 earlier lines · tap to show`).
- Each item is `TtyBlock(model, …)`: a `Modifier.Node` that implements `LayoutModifierNode`
  (height = `block.rows × cellH`, width = full), `DrawModifierNode` (calls the painter for the
  rows inside the clip), `PointerInputModifierNode` (§3) and `SemanticsModifierNode` (§4). It has
  no children except the invisible accessibility targets.
- Layout happens in `measure`, from an LRU cache keyed by
  `(entry identity, row flags, cols, metrics version, options, fold open, marks)`. A cache miss
  lays out on the spot (a normal row is microseconds). When `vm.rows` changes, the newest 60
  rows are laid out ahead on `Dispatchers.Default`.
- Scrolling is by pixel while a finger is down and settles on a row boundary
  (`SnapFlingBehavior` to multiples of `cellH`), so at rest the screen is a true grid. The log's
  height is rarely a multiple of `cellH`; the spare pixels go at the top, where a row is cut
  anyway while scrolling.
- Scroll thumb: the last grid column, drawn by `TtyLog` over the list: `█` cells for the visible
  share on a `░` track, DIM, shown while scrolling and for 1.5 s after. Position from
  `listState` (index + offset over loaded rows; the pager loads older pages as today, so the
  thumb shortens as they arrive). Dragging it scrolls. It has no semantics (TalkBack scrolls by
  its own actions).
- "↓ Scroll to bottom" becomes a row above the prompt: `── ↓ newest ──`, PROMPT ink, same
  `jump-latest` tag, 48 dp touch height (§3.1).

### 2.8 Performance budgets

| Thing | Budget |
|---|---|
| Layout of one ordinary row (cache miss) | < 100 µs on a mid-range phone |
| Main-thread time per scroll frame (draw of ~45 rows) | < 3 ms; no allocation |
| Block cache | ≤ 2 MB, LRU ~400 blocks, registered with `LauncherApp.keepsInMemory` so it is dropped on trim-memory like the screen's other caches |
| Cold start to first log frame | not slower than GUI by more than 10 ms (the newest screenful is laid out before first draw) |
| Screen animation | 30 fps default, 60 with `tty.fps 60`; frame work < 2 ms; only dirty rows repainted |
| A 50 000-line log | unchanged cost: `LogPager` pages the entries; only visible + 60 rows are laid out |

Macrobenchmark is not in the project; measure with a `FrameMetrics` device test that scrolls
5000 corpus lines in both styles and asserts the 90th percentile.

### 2.9 RTL and bidi

- Direction of a paragraph: first strong character (as Compose does), via `java.text.Bidi`,
  which exists on the JVM and on Android. Each wrapped row is reordered to visual order before
  it becomes runs. Mirrored characters (brackets) follow Bidi.
- In an RTL locale the frame mirrors: gutter in the last column, time at the left edge, tables'
  first column on the right, bars filling from the right, the key bar's order reversed, the
  scroll thumb at the left. Implemented once: layout produces logical columns and
  `CellBlock.mirrored` flips `col → cols − col − cells` when runs are emitted; box corners swap.
- The swipe directions follow `deleteRight` exactly as now (TerminalScreen.kt:452).
- As built (step 4): a row of a paragraph starts at the frame's start, whatever its direction,
  as the GUI's one-line text does; only the order inside the row follows bidi. `BlockWriter`
  keeps each wrapped row as a *unit* that moves to the other side whole, and turns runs of box
  drawing and blocks around cell by cell (`┌` ↔ `┐`; eighths with no mirror character get
  `CellStyle.MIRROR`). Home's parts are mirrored after layout (`HomeCells.mirrored`); their nodes
  keep `Modifier.offset`, which Compose already turns around in a right-to-left layout.

## 3. Interaction

### 3.1 Hit-testing

`pointer (px) → GridMetrics.cellAt(x, y) → item (the LazyColumn already routed the event to the
item's node) → CellBlock.hitAt(col, row) → Target → the same callbacks the GUI calls`:

| Target | Tap | Long press |
|---|---|---|
| `Line` | `vm.onTap(entry)` | `showMenu(…) { vm.actionsFor(entry) }` |
| `Link(url)` | `vm.openUrl(url)` | the line's menu (it already offers Copy link, Share) |
| `Button(i)` | `vm.runLineAction(entry, i)` | — |
| `App(i)` | `vm.launchApp(app)` | `onAppLongPress(app)` |
| `Fold` | `foldState.toggle(id)` | — |
| empty space | `focusPrompt()`; double tap → the gesture, when one is set | — |

- Touch size: cells are small (a row is ~22 dp). A tap resolves to the nearest target within
  24 dp of the finger, preferring the one under it; a `Line` target is the whole block. This
  replaces `log.taller-taps`' extra height, which would break the grid; the setting keeps its
  GUI meaning and in TTY widens the slop to 32 dp.
- Press feedback: the target's cells go INVERSE while pressed (a line: its gutter cell and the
  first row get SURFACE bg). No ripple. The row whose menu is open, and the row a search jump
  flashes, get SURFACE bg, as `highlighted` does now.
- Haptics on long press as now.

### 3.2 Swipe to copy / delete

Same thresholds and directions. The block slides by whole cells; the uncovered cells show
`⧉ Copy` (PROMPT) or `✕ Delete` (ERROR). Past a third of the width → `vm.swipeCopy` /
`vm.swipeDelete`; the undo row appears as now. Horizontal drags that a row did not take still
turn the pager (§3.9).

### 3.3 Menus and sheets: `TtySheet`

A panel drawn on the grid, anchored above the prompt, in a `Popup`/dialog window so Back, focus
and TalkBack's dialog handling are the platform's:

```
┌─ countdown · today 6:55:12 ──────────┐
│ Open link                            │
│▌Copy text                            │   ← pressed/focused row: inverse
│ Run again                            │
│ Delete                               │
└──────────────────────────────────────┘
```

It renders the same `ActionMenu` (title, detail, `List<LogAction>`, icons dropped or shown as
2-cell images) that `ActionSheet` renders; rows are at least 2 grid rows tall to keep 44 dp
targets. Hardware keys: ↑/↓/Enter/Esc. The quick-actions menu, app actions, pinned-line menu and
media menu all go through it. The secret prompt (`TerminalSecretPrompt`) becomes a sheet with a
masked field (`•` cells). Material screens (Settings, Scripts, the editor…) are not part of TTY
and stay Material 3 Expressive; `ExpressiveOnlyTest` is unaffected because `ui/tty/` calls no
Material component at all (add `ui/tty/` to a new assertion: it must not import
`androidx.compose.material3`).

### 3.4 Selection and copy on the grid

New in TTY (the GUI has none). Entered from the long-press sheet ("Select text") or by a
long-press-then-drag. Model: `Selection(anchor: LogPos, focus: LogPos)` with
`LogPos(entryId, srcOffset)`: positions are in *source text*, not cells, so a selection survives
re-wrapping, a font-size change and scrolling. Display: selected cells INVERSE; two handle cells
(`▌`) that drag; dragging at the edge auto-scrolls; a small bar row above the prompt
`Copy  Select all  Share  ✕`. Copy takes the plain text between the two positions
(`StyledText.plain` semantics: soft-wrapped rows joined, box-drawing and padding never copied,
table cells tab-separated, the time column excluded). `Selection.kt` is pure and JVM-tested.

### 3.5 Search (`/`)

`LogSearcher` and `vm.search` are unchanged. Results are `LogRow`s built with `trim = false`, so
`TtySearchResults` is the same `TtyBlock` list plus the "found in" row. Matches: `markRanges`
(the pure core of today's `markMatches`, moved to `terminal/`) gives source ranges; the painter
draws those cells INVERSE in WARN ink. A fold never hides a match (rows with marks do not fold,
as now). `LogSearchSummary` and `LogFilterBar` are one grid row each (`TtyBands`).

### 3.6 Prompt and keyboard: `TtyPrompt`

- The editing engine stays `BasicTextField` with `vm.input`/`vm.onInputChange`: IME composition,
  hardware keys, autofill hints, `ime.suggestions`, editable-text semantics and every
  `PromptActions` path stay as they are. Its own text and cursor are transparent.
- The visible prompt is a grid row drawn from the same `TextFieldValue` by `PromptLayout`:
  `$ ` (or the profile's prompt) in PROMPT ink, the text in cells, the composing range
  underlined, the selection inverse, the cursor a block (`tty.cursor`) that inverts the cell
  under it and blinks (`tty.blink`). Text longer than the width scrolls horizontally by cells,
  keeping the cursor in view.
- A tap on a cell sets the cursor (`cell → text offset → vm.onInputChange(selection)`); a long
  press opens a `TtySheet` with Paste / Select all / Copy. The quick-actions cell (`≡`), the
  clear cell (`✕`) and the key-bar toggle are 3-cell targets at the row's ends with the same
  test tags (`prompt-quick`, `prompt-clear`, `keybar-toggle`).
- `prompt.height` and `prompt.bottom-padding` round up to whole rows (the text sits on the
  middle row).

### 3.7 Suggestions and the app selector

- `suggestions.style list`: rows above the prompt, best one nearest the prompt, the Enter
  target INVERSE; an app is `icon(2 cells, with suggestions.icons) name [W]`; a completion shows
  its help DIM after two spaces; `suggestions.rows`, `suggestions.max`, `suggestions.wrap`,
  `suggestions.idle`, `suggestions.auto` keep their meaning.
- `suggestions.style chips`: one row (or `suggestions.rows` rows) of ` name ` cells separated by
  a space, the first INVERSE, scrolling horizontally by cells.
- `chips.height` → rows per suggestion (44 dp → 2 rows), so targets stay finger-sized.
- The app selector (`selector.rail`): the same `SelectorMath`, drawn as a column of names with
  the letter rail in the edge columns; the log fades through `vm.selectorFade` (the TTY log
  applies it as a blend toward bg on its inks, or as layer alpha — here alpha is fine, it is a
  transition).

### 3.8 Header, pinned line, key bar, bands

- Header: one grid row from `HeaderParts`. Parts are text with glyphs from the font or from
  `BoxGlyphs`: `14:02  Tue 17`, battery `▕███▌ ▏82%` (`⚡` when charging), `wifi`/`5G`, `⏰ 07:00`,
  `☐ 3`, `🔓 41` as `unl 41`, profile tags INVERSE, `⚙`. Each part is a `CellTarget` calling
  `vm.onHeaderTap(target)` / `vm.onProfileTap`; swipe down on the row → the gesture.
  `header.left`, `header.right`, `header.padding` (rows), `header.below-bar`, `header.show` keep
  working. The camera cutout: the row starts under the status-bar inset as now; cells under a
  cutout are skipped by layout (parts flow around it).
- Pinned line: one row (two with the media line): `▸ name: text`, turning as `PinnedTurns`
  decides; the dots become `·●·`; `header.pinned-bar-above/below` become `─` rows. What's
  playing: `♪ title — artist  ⏯ ⏭` with the two controls as targets. Hidden while typing, by
  rows sliding (AnimatedVisibility with a whole-row step).
- Key bar: `KeyBarLayout` splits the width into equal spans; a key is its label centered in a
  span, `│` between keys; `keybar.height` → rows (44 dp → 2); pressed key INVERSE; key repeat
  and `onText` as now; tags `keybar` and per key.
- Bands: undo / "Copied" (live region), update progress (a `█` progress bar row), default-home
  band, live preview band — each a row or two through `TtyBands`, same tags.

### 3.9 The widgets page

The pager is in `MainActivity` (line 626) and wraps `terminal()`. TTY replaces only what
`TerminalScreen` draws, so page turning, the page's accessibility actions and
`vm.onHomePageShown` are untouched. The widgets page itself stays as it is (cards). A TTY
rendering of widgets is a separate, later decision; it is not needed for TTY home to be
complete, but it is listed as open in §12.

## 4. Accessibility

The canvas draws nothing TalkBack can see; the semantics tree is built from the same models.

- Log row: `TtyBlock`'s node sets `text = AnnotatedString(model.spoken(time))` (so
  `onNodeWithText` in tests works too), `onClick`, `onLongClick(label = "actions")`, and
  `customActions`: one per `Target.Link` ("Open <text>"), per `Target.Button` (its label), Copy
  and Delete (the swipe's equivalents), "Show more"/"Fold". `testTag("line-<id>")` as in GUI.
  Dimmed rows add no noise; an unread row's text starts with "new".
- Sub-targets a switch or keyboard user must reach directly (buttons, app-grid entries, the
  fold row) are also real child nodes: empty focusable boxes placed at the target's first
  rectangle, `role = Button`, same callbacks. They come from `block.targets`, so they cannot
  differ from what a finger can hit.
- Tables, bars, stats and charts are read as words: `LineModel.spoken` uses the wording the GUI
  already has (`LogTable`'s `contentDescription`, `spoken(Meter)`, `Columns.said`), moved to
  `terminal/` so both renderers say the same sentence.
- Day divider: `heading()`. Unread divider: text "3 new since you left". Undo/Copied band:
  `liveRegion = Polite` as now.
- Header parts, pinned line, key-bar keys, suggestions: one node per target with the
  description the GUI gives (`"${line.name}: ${line.text}"`, "Quick actions", "1 of 3"…).
- Prompt: the transparent `BasicTextField` carries the editable-text semantics.
- Focus order = list order; the `LazyColumn` provides scroll actions and collection info.
- Screens: the host is one node, "Matrix rain. Double-tap to close", with `onClick` = exit.
  The screensaver never starts while a screen reader is on (`rememberScreenReader()`); the boot
  sequence is skipped.
- Font scale: `font.size` is in sp already, so cells grow with the system text size.
- Contrast is a tested property of the palette (§5), since TTY relies on color pairs more
  (inverse video).

`ParityTest` (§8) asserts that for every corpus row the GUI and TTY nodes have the same text,
the same click/long-click presence and the same custom-action labels.

## 5. Theming

### 5.1 Palette

`TtyPalette(palette: Palette)` in `ui/tty/` resolves `Ink → ARGB int` once per palette change:

| Ink | From `Palette` | ANSI analogy |
|---|---|---|
| BG, SURFACE | `bg`, `surface` | black, bright black (as a background) |
| FG, DIM | `fg`, `dim` | white, bright black |
| PROMPT | `prompt` (the accent; `fmt` role `accent`) | bright green / theme accent |
| ERROR, WARN, GOOD | `error`, `warn`, `good` | red, yellow, green |
| INFO, LINK | `info`, `link` | blue, cyan |
| SCRIPT | `script` | magenta |
| PERSONAL, WORK, PRIVATE | the profile colors | — |

- There are no other colors. A script's per-name color (`scriptColor`) already picks from the
  palette's roles; that function moves to return an `Ink`.
- FAINT (dimmed old output) = `lerp(ink, bg, 1 − DIM_ALPHA)` precomputed per ink: the same
  pixels as today's alpha fade over a plain background, without a layer. Over a wallpaper
  (`log.wallpaper`) the result differs slightly from true alpha; accepted, it is what SGR 2 does.
- INVERSE swaps fg and bg; an inverse cell whose bg ink is BG uses the fg ink as its fill.
- Dark and light, every built-in theme, custom themes from the theme builder and Material You
  need nothing special: they all arrive as a `Palette` through `Term.palette`, and
  `theme.always-dark` is applied before that.
- Contrast: a JVM test over every built-in palette, dark and light, asserts ≥ 4.5:1 for each
  text ink on BG and SURFACE, ≥ 3:1 for DIM and FAINT, and ≥ 4.5:1 for BG on each ink (inverse).
  For Material You and custom themes, `TtyPalette` nudges an ink's lightness until its inverse
  pair passes; the GUI colors are not touched.
- `log.wallpaper`: BG cells are not painted; `Modifier.page(wallpaper)` stays the backdrop.

### 5.2 The CRT look without shaders (`tty.crt on`, API 28+)

Removed on 22 November 2026, by the maintainer's decision: it added nothing beside the `crt` shader. `tty.crt`
no longer exists; the rest of this section and other mentions of it are kept as the design was.

Drawn by `TtyCrt`, a `drawWithContent` wrapper on the TTY home layer:

- scanlines: a 1×(2–3 px) `BitmapShader` tiled over the content, 8–12 % dark;
- vignette: a `RadialGradient` overlay;
- glow: `Paint.setShadowLayer(radius = cellH / 6, ink at 60 %)` on text runs (hardware
  accelerated for text since API 28);
- API 31+: true bloom as `RenderEffect.createBlendModeEffect(content, blur(content), PLUS)`;
- API 33+: `tty.crt on` selects the bundled AGSL shader `crt` instead (curvature, scanlines,
  bloom, slight aberration), with the same watchdog as user shaders.

Static cost only: nothing here animates, so nothing redraws unless the content does.

## 6. Screens

### 6.1 Interface (terminal/tty/screens, pure)

```kotlin
interface TtyScreen {
    val name: String
    /** Frames per second wanted; 0: only when [tick] asks. The host may give fewer. */
    val fps: Int
    fun start(env: ScreenEnv)
    fun resize(cols: Int, rows: Int)
    /** Draws into [grid]. Returns false when the program has ended (boot, sl). */
    fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean
    /** True if the screen used it; otherwise the host exits and passes it on. */
    fun key(key: TtyKey): Boolean = false
    fun tap(col: Int, row: Int): Boolean = false
    fun stop() {}
}

class ScreenEnv(val cols: Int, val rows: Int, val random: kotlin.random.Random, val clock: () -> Long,
                val clock24: Boolean, val data: ScreenData)       // ScreenData: what `top` and `boot` read
object TtyScreens { fun named(name: String): TtyScreen? ; val names: List<String> }
```

Screens only use `Ink`s, so every theme works, light ones included (matrix rain is PROMPT on BG
with a FG head; on a light theme it is dark rain on paper).

| Screen | Notes |
|---|---|
| `matrix` | half-width katakana need a font: cells that the primary font lacks fall back per §2.4; default glyph set is ASCII + katakana when available |
| `clock` | big digits from block cells, date under it; fps 0, ticks each second (or minute without seconds) |
| `pipes` | box-drawing geometry, so pipes join at any font |
| `sl` | ends by itself; `frame` returns false |
| `top` | built in step 5, through `screen top` only: Kolahal's uptime, today's unlocks, bars for the battery, free memory, Kolahal's memory and today's screen time (usage access), then the scripts by latest run with their result and next timer; read each second from `ScreenData.top` (`TopSource`, made by `terminal/TopFacts`); any key or tap leaves |
| `boot` | `[ ok ]` lines from real startup facts (apps read, scripts loaded, theme); at most 1.2 s; `tty.boot on` plays it at cold start only, never after process death recovery |

### 6.2 Host and lifecycle (`ui/tty/TtyScreenHost`)

- State: `vm.ttyScreen: StateFlow<String?>`. A command (`matrix`, …, in `commands/` with the
  others) calls a new `TerminalUi.showTtyScreen(name)` (no default body, per that interface's
  rule; test fakes implement it).
- The host is an overlay inside the terminal page, above the log, below sheets. It owns one
  `CellBuffer` sized to the page, and a loop:
  `repeatOnLifecycle(RESUMED) { while (active) withFrameNanos { … } }`, throttled to the
  screen's fps, running only while `LocalTerminalShown` is true and the window has focus. It
  repaints dirty rows only.
- Exit: any tap the screen did not use, Back, Esc, `q`, Ctrl-C on a hardware keyboard, the home
  button. A printable key typed on a keyboard exits and goes into the prompt, so typing is never
  swallowed. Leaving home or turning the page stops the loop; coming back resumes the same
  screen within 30 s, else it is closed.
- Screens work in **both** home styles: the host only needs `GridMetrics` and the painter. A
  command that works in one style only would be the compromise this design avoids.
- Process death: the screen is not restored (a program that was running in a terminal is gone
  after a reboot too); the log, input and scroll anchor restore as today.
- Battery: 30 fps default; 15 fps and no screensaver in battery saver; stop at thermal status
  ≥ MODERATE (API 29+); never a wake lock; never `FLAG_KEEP_SCREEN_ON`.
- Screensaver (built in step 5 as `screen.saver off|matrix|clock|pipes` and `screen.saver-after`
  minutes, default 5, both in Settings → Look & feel): after that long with no touch while home is
  resumed and focused, on the log page, with no sheet open and nothing typed. Since the phone's own
  screen timeout usually comes first, it mostly shows on a charger or dock; that is intended. First
  touch only dismisses it (the touch is not passed to the log); not in battery saver, with the
  screen off or with "Remove animations" on.
- Entering a screen from TTY home can use the visible grid: `TtyLog.snapshot(): CellBuffer`
  composes the visible blocks into a buffer, so `matrix` can dissolve the log into rain and
  `boot` can scroll into the log. In GUI style the screen starts from blank.

### 6.3 Scripts drawing on the grid (built in step 7)

What was built differs from the sketch below:

- The script runs in its own process (`:script`), as every script does, so there is no shared buffer
  or thread to swap. Its drawing calls go to home as numbers and texts (`ScreenFrame`), and home puts
  them on its grid (`FramePlayer`), bounded in size. Home sends the next tick only after it got the last
  one's frame, so a slow script drops its own frames.
- Functions: `start(g)`, `frame(g, t, dt)`, `key(k)`, `tap(col, row)`, `resize(g)`, `stop()`. Only a
  returned `false` closes the screen. A tap or a key closes it unless the script has `tap` or `key`;
  Back always does.
- The budget is per call: 1 s (QuickJS's timeout, the Lua hook) ends the screen at once; 30 frames in
  a row over 50 ms (not 8 ms: QuickJS on a mid-range phone takes longer than that for a screen of
  cells) end it too, with an error. A screen lasts at most 5 minutes, and a call may draw 8 screens.
- A style names the text's `color` and `bg`, not an `ink`, as `fmt.bar` and `status` do.

```js
// @name life
// @uses screen
tty.screen({
  fps: 10,
  start(g) { /* g.cols, g.rows */ },
  frame(g, t, dt) { g.clear(); g.put(2, 1, "gen " + n, {ink: "accent", bold: true}); return true },
  key(k) { return k === "q" ? false : true },
  tap(col, row) { return true },
})
```

- `g.put / g.fill / g.clear / g.box(x, y, w, h) / g.bar(x, y, w, value, ink) / g.cols / g.rows`;
  inks by role name only, the same names `fmt.color` takes.
- A `ScriptScreen : TtyScreen` adapter runs the callbacks on the script engine's thread into a
  back buffer; the host swaps buffers per frame, so a slow script drops its own frames, not the
  UI's. `RunWatchdog` gets a per-frame budget (8 ms; 30 overruns in a row end the screen with a
  log line). Same in Lua. `@uses screen` is shown at install like other permissions.

## 7. Shaders (after TTY; API 33+)

**As built (step 6, 22 November 2026).** Where it differs from what follows:

- *Where it attaches*: two layers, not one — home's root (`TtyHome`) and the screen host's box
  (`TtyScreenHost`) — since they are siblings in the pager page. Both get `rememberTtyShader`
  (`ui/tty/TtyShader.kt`), so a screen shows it in either home style, as `tty.crt` does. Settings'
  preview of home has none.
- *Files*: `ShaderMeta` and the pure rules are in `script/` (`ShaderMeta`, `ShaderCatalog`,
  `ShaderWatchdog`, `ShaderSafety`); the Android side is `script/Shaders.kt`. The user's files are in
  `files/shaders/`, kept in step with the script folder by `script import` / `script export`, so a
  shader is found by name without the folder. Bundled: `crt`, `bloom`, `rain` and the rest of `ShaderCatalog.BUNDLED` (no `scanlines`,
  `aberration`; CRT has both).
- *Commands*: `shader list | use <name> | on | off | check <name> | reload | copy <name> | remove
  <name>`; `shader <name>` turns one on. `screen.shader` is a `Choice` whose list grows by the user's
  files (`Choice.added`, as `theme.name`), so it completes. `shader get` and `shader update` install
  and update shared ones.
- *Settings*: Settings → Shaders (`ui/ShadersScreen.kt`), by the maintainer's request of 22 November; the
  key itself is on no settings page. Its preview uses the code that sets the uniforms, but neither the
  watchdog nor the safe mode's mark.
- *Uniforms*: `content`, `resolution`, `cell`, `grid`, `origin`, `time`, `touch`, `touchAge`, `dark`, `date`,
  `tilt`, `pressed`, `battery`, `charging` and the ten colors. Not built: `cells` (step 7), `scroll`, `param0..7` and `@param`, `@warp`,
  `@bloom`. A `layout(color)` color must be four numbers, as Android requires.
- *Tilt*: `ShaderTilt` (pure, JVM-tested) turns gravity into the lean: compared with a level that follows
  the phone (6 s), smoothed (0.12 s), on the screen's own sides, 1 at 30 degrees, and changed only by more
  than 0.012 and at most 30 times a second, so it rests on a table. `LiveTilt` has the one sensor listener
  that all surfaces share (`TYPE_GRAVITY`, or the accelerometer steadied; `SENSOR_DELAY_GAME`, so no
  permission), registered while a surface holds it: a shader that declares `tilt`, home resumed, focused
  and on its page, and `ShaderClock.moves`. When the last lets go it eases to 0 in about 0.7 s, and then
  nothing runs: a surface that had to stop waits for a touch or a change of the battery. A still shader is
  drawn again at each change; an animated one reads the lean as it draws, and follows it only while its
  clock stands still. The preview in Settings follows it too.
- *Finger and battery*: `pressed` comes from the touch watcher (Initial pass, nothing consumed; a cancel
  ends it), `battery` and `charging` from `DeviceStatus`, the header's. Each draws a still shader again
  only when it changes.
- *Watchdog*: the 90th percentile of the GPU time (`FrameMetrics.GPU_DURATION`) over 60 frames
  against two frames of the display's refresh rate, but at least 33 ms, and it has to stay over that for
  120 frames in a row; the first 60 frames don't count (the GPU compiles the program). Where the phone
  doesn't measure the GPU, only stalls count: the whole frame's time is no measure of a shader (it has
  the wait for the screen in it, 26 ms at the 90th percentile on a healthy 120 Hz phone). Three frames
  over 250 ms of GPU time (a whole second when unmeasured) are a stall. A compile error is checked
  before the first frame, and one log line.
- *Safe mode*: the mark is written before the shader is compiled, so a crash in the compiler counts;
  cleared after 120 good frames, when the surface stops, or with the process's end for a reason that
  isn't a crash or a hang (`ApplicationExitInfo`). There is no `screen.shader-tripped` key: what is
  tripped is in the crash preferences, which stay on this phone.
- *Off*: `shader off`, `config set screen.shader off`, two fingers held for a second (observed on the
  layer itself). No app shortcut. The screen reader rule is as designed; with **Remove animations** a
  moving shader stands still rather than going off.
- `tty.crt on` keeps its drawn look on Android 13+; it doesn't select the shader.

### 7.1 Where the shader attaches

On the single `graphicsLayer` that wraps `TtyHome` (header to key bar) and the screen host:

```kotlin
Modifier.graphicsLayer {
    renderEffect = shaderState.effect   // RenderEffect.createRuntimeShaderEffect(runtimeShader, "content").asComposeRenderEffect()
    clip = true
}
```

- The grid is drawn normally into the layer; the shader post-processes the layer's pixels.
  Nothing in `terminal/tty/` or the painter changes, which is the reason for this choice.
- Rejected: drawing the grid *inside* a shader (text as a data texture). It would move font
  fallback, shaping and emoji into GLSL, and it gains only effects that can also be fed by the
  small `cells` uniform below.
- Sheets and Android's own windows (IME, dialogs) are outside the layer and stay sharp. Settings
  is a different screen and is never affected, which makes it the guaranteed way out.
- Touch is not warped by a `RenderEffect`. The contract for shader authors: displacement inside
  the screen must stay under half a cell except near the corners; the bundled `crt` obeys it.
  A shader may declare `// @warp barrel <k>`; then the host applies the inverse mapping to
  pointer positions in `TtyHome`'s root pointer node. Undeclared warps are the author's problem,
  and the docs say so.

### 7.2 Uniforms (the host sets those the shader declares; missing ones are skipped)

```glsl
uniform shader content;        // the TTY layer
uniform shader cells;          // optional: cols×rows image, one texel per cell: rgb = fg color, a = ink coverage (0 blank)
uniform float2 resolution;     // px
uniform float2 cell;           // cell size, px
uniform float2 grid;           // cols, rows
uniform float2 origin;         // px of cell (0,0)
uniform float  time;           // seconds, only advances for @animated shaders
uniform float2 touch;          // last touch, px
uniform float  touchAge;       // seconds since it; large when none
uniform float  scroll;         // log scroll offset in rows (for scan effects that should not swim)
uniform float  dark;           // 1 dark theme, 0 light
layout(color) uniform half4 bg, surface, fg, dim, accent, error, warn, good, info, link;
uniform float  param0..param7; // from @param lines, set in Settings
half4 main(float2 xy) { … }
```

`cells` is a `BitmapShader` over a cols×rows `Bitmap` the painter fills only when a shader
declares it (cost: one small upload per content change).

### 7.3 Files

- `*.agsl` in the scripts folder's `shaders/` directory, picked up by the same folder watching
  as scripts; bundled ones (`crt`, `scanlines`, `bloom`, `aberration`, `rain`) ship in assets
  and can be copied out to edit.
- Header comments, in the scripts' style: `// @name`, `// @description`, `// @animated` (needs
  a frame loop; absent → the shader costs nothing while the screen is still),
  `// @param curvature 0..0.3 = 0.1` (becomes a slider), `// @warp`, `// @bloom <radius> <gain>`
  (asks the host for a blurred second pass, composed with `createBlendModeEffect`).
- Commands: `shader list | use <name> | off | check <name>`; Settings → TTY → Shader.
- Validation: metadata parse and size limit (64 KB) in the Model (`script/ShaderMeta.kt`, pure);
  compile with `RuntimeShader(source)` in `ui/tty/TtyShader.kt` inside try/catch
  (`IllegalArgumentException` carries the compiler's message and line). An error is a log line
  with `LogContext.LINE`, so a tap opens the editor at that line, as script errors do. A shader
  that fails keeps the previous one (or none) active.
- Sandbox: AGSL has no I/O, no loops over unbounded data, no access outside its inputs; it is a
  pure function of the uniforms. The only harms are unreadable output and GPU time. Both are
  handled below. No `@uses` is needed.

### 7.4 Safety

| Guard | Mechanism |
|---|---|
| Frame-time watchdog | `Window.addOnFrameMetricsAvailableListener`: if the GPU's 90th percentile over 60 frames stays over two frame intervals (33 ms at least) for 120 frames, or 3 frames stall the GPU (250 ms), the shader is turned off (as built: see *Watchdog* above), a log line says why, and `screen.shader-tripped = <name>@<hash>` stops it from re-arming until the file changes or the user turns it on again. |
| Crash / hang at start | An "arming" flag is written to preferences before the first frame with a new shader and cleared after 120 good frames. Found set at startup → safe mode: shaders off, one log line. Three trips in a row → `screen.shader` set to off. |
| Always-available off | (1) Settings → TTY, which is outside the layer; (2) the `shader off` command (the prompt and IME still work even if nothing is readable); (3) a two-finger long press anywhere on home for 1 s, handled above the layer; (4) long-press on the launcher icon → app shortcut "Safe mode", which starts home with `screen.shader off` and `home.style gui`. |
| Battery | Battery saver, thermal ≥ MODERATE, or below 15 % and not charging: `@animated` shaders freeze `time` (static frame) or turn off (`screen.shader-battery static|off`). |
| Idle | The frame loop for `@animated` runs only while home is resumed, focused and the terminal page is shown; it stops after 60 s without touch unless on a charger. |
| Accessibility | Off while a screen reader or "remove animations" is on, unless the user opts in. |

### 7.5 Android 9–12

No AGSL (API 33). They get `tty.crt` as in §5.2: scanlines, vignette and text glow on 9–11, plus
real bloom on 12. `shader list` on those versions says that shaders need Android 13 and lists
the built-in look. User `.agsl` files are listed but marked unavailable.

## 8. Testing

### 8.1 JVM (pure; the bulk)

- `CellWidthTest`: clusters and widths for ASCII, CJK, emoji (ZWJ families, flags, skin tones,
  VS15/VS16), combining marks, tabs.
- `WrapTest` with properties over generated text: no row wider than `cols`; no wide cluster
  split; joining soft-wrapped rows through `srcAt` gives back `StyledText.plain(text)`; wrapping
  at width w then reading back equals wrapping at any other width.
- `LineLayoutTest`, `TableLayoutTest`, `MeterLayoutTest`, `StatsLayoutTest`, `ChartLayoutTest`:
  golden `CellBlock.dump()` text (rows as text plus a style line under each), files in test
  resources, at 32, 40, 53 and 80 columns, LTR and RTL.
- `HitTest`: every `Target` in `LineModel.targets()` is hit by at least one cell of the block,
  and no cell hits a target the model does not list.
- `SelectionTest`, `PromptLayoutTest`, `HeaderLayoutTest` (TTY), `KeyBarLayoutTest`.
- `LineModelTest` over the corpus; and a reflection test that every `LogContext` constant is
  either read by `lineModel`/`LogLineActions` or listed as "not shown", so a new context key
  cannot be handled in one renderer only.
- `TtyPaletteContrastTest` (§5.1).
- Screens: with a seeded `Random` and a fake clock, N frames of each screen dump to golden text;
  `sl` and `boot` terminate; no screen writes outside the buffer at 20×10 and 200×60.
- `ShaderMetaTest`: header parsing, params, limits.
- LayersTest additions (§1.5, §3.3).

### 8.2 Keeping the two renderers from drifting

1. One `LineModel`: both renderers `when` over the sealed `Body` and `Block` types with no
   `else`, so a new kind of block does not compile until both draw it.
2. One corpus: `LogCorpus` (test fixtures: one `LogEntry` per context key and per `fmt.*`
   function, taken from the existing `LogGalleryTest` and `DocsExamples`), used by the JVM
   layout tests and by the device tests of both styles.
3. `ParityTest` (device): each corpus row composed in GUI and in TTY; asserts equal semantics
   text, equal presence of click / long-click, equal custom-action labels, equal child button
   labels; then performs each action in both and asserts the same callback fired with the same
   arguments.
4. `site/…/scripting/api.mdx` examples already run as tests (`DocsExamples`); they run for both
   styles.

### 8.3 Device tests

- The existing home suites that go through `TerminalScreen` (`HomeScreenTest`, `HomePagesTest`,
  `LogLineTest`, `LogSearchResultsTest`, `KeyBarTest`, `SuggestionListTest`, `PinnedHeaderTest`,
  `TerminalHeaderTest`, `FontModesTest`, `ThemeModeTest`, `WallpaperTest`) get a
  `home.style` parameter where they assert behavior (tags, taps, text) rather than pixels. TTY
  keeps the tags: `log`, `line-<id>`, `fold-<id>`, `prompt`, `prompt-quick`, `prompt-clear`,
  `keybar`, `keybar-toggle`, `suggestions`, `header`, `header-<part>`, `pinned-header`,
  `pinned-<name>`, `undo-row`, `jump-latest`, `empty-hint`, `home-pager`.
- Pixel assertions (no screenshot library is in the project; keep it that way):
  `captureToImage()` then check structure, not goldens — a table's vertical line is one
  unbroken column of the DIM color from `┌` to `└`; row tops are at multiples of `cellH`; the
  glyph of column n starts at `originX + n·cellW` for an ASCII ruler line, a CJK line and an
  emoji line; inverse cells have the ink as background.
- `TtyScrollPerfTest`: FrameMetrics percentiles while flinging 5000 corpus rows, both styles.
- TalkBack: `AccessibilityChecks.enable()` on the TTY home tests; one manual pass per release
  (documented checklist), since focus order and speech cannot be fully asserted.
- Process death: `StateRestorationTester` for scroll anchor, fold state, selection, style.
- Shaders (API 33 emulator): bundled shaders compile; a deliberately slow shader trips the
  watchdog; the arming flag leads to safe mode.

Per the project's rules, agents write code only and one Gradle run happens at the end on the
merged branch, with `ANDROID_SERIAL=emulator-5554`.

## 9. Phasing

Sizes: S ≤ 2 days, M ≈ 1 week, L ≈ 2 weeks of focused work. Every step leaves master shippable.

| # | Step | What the user gets | Size |
|---|---|---|---|
| 0 | **The seam.** `LineModel` + GUI `LogLine` consumes it; `HeaderParts`; `rememberLogScroll`; `FoldState`; `TerminalScreen` split into `HomeState` + `GuiHome`; `LogCorpus`. | Nothing visible; existing tests green. Reduces the "screens take the whole view model" debt a little. | M |
| 1 | **The grid engine and the screens.** `Cell*`, `CellWidth`, `GridMetrics`, `GridPainter`, `BoxGlyphs`, `TtyPalette`, `TtyScreen`, host; `matrix`, `clock`, `pipes`, `sl`, `boot`. | Five commands that work today, in the current home. The painter and metrics meet real devices and fonts early. | M |
| 2 | **The TTY log.** Layouts for every `Body`/`Block`, `TtyBlock`, `TtyLog`, hit-testing, swipe, fold, dim, unread, day, time, images, search results, semantics. `home.style` exists but is not in Settings (`config set home.style tty`); the rest of home is still GUI. | Opt-in preview for the maintainer. | L |
| 3 | **The rest of home on the grid.** Prompt, suggestions (both styles), key bar, header, pinned line, bands, `TtySheet`, app selector, scroll thumb, empty-log tips. | Complete TTY home, still opt-in. | L |
| 4 | **Parity.** RTL and bidi, shaped runs, TalkBack pass, `ParityTest` over the whole corpus, perf test, process death, Settings section "TTY" with the style switch, website page (Guides → TTY, profiles material last, nothing marked "new"). | **TTY is released.** | M |
| 5 | Selection mode; `top`; screensaver; `tty.crt` (no AGSL); thumb in GUI. | Extras, each on its own. | M |
| 6 | **Shaders** (API 33+): effect layer, uniforms, files, watchdog, safe mode, bundled set, Settings. | The second option the maintainer asked for. | M–L |
| 7 | Script screens (`tty.screen`), `cells` uniform. | Scripts draw on the grid. | M |

Steps 0 and 1 are independent and can go in parallel worktrees. Step 2's layouts (pure) can be
split across agents by block type once `CellBlock` is fixed.

### Risks

| Risk | Mitigation |
|---|---|
| Two renderers double the cost of every future log feature | The seam: a feature is a `LineModel` change that fails to compile until both renderers handle it; parity tests. This cost is real and permanent; it is the price of "an alternative version". |
| Fallback fonts and emoji do not fit cells | Layout by table, painter scales to the cell (§2.4); device test with CJK/emoji ruler lines. |
| Arabic/Indic look wrong on a grid | Shaped runs scaled to their span; if a script reads badly at a size, that is true of every terminal — test with real text before release (step 4). |
| Transparent `BasicTextField` under a drawn prompt (IME quirks, cursor handles, autofill popups in the wrong place) | Keep the field's layout the same size and font as the drawn row so platform popups anchor correctly; device tests with Gboard composition; fall back to showing the field's own text (still monospace) if a keyboard misbehaves. |
| Grid-quantized spacing surprises users of `log.spacing` 5 | Documented mapping; Settings shows the value in rows while in TTY. |
| Row-snapped fling feels different | It is the terminal feel; `tty.smooth-scroll on` disables the snap. |
| Shader warps misalign touch | Half-cell contract, `@warp` inverse mapping, docs. |
| GPU hang takes home down with it | Arming flag, safe-mode shortcut, Settings outside the layer. |
| `TerminalScreen` and the view model grow | Step 0 splits first; new composables take narrow parameters only. |
| Memory after hours | Block cache bounded and trim-aware; screens allocate once. |

## 10. What not to do

| Approach | Why not |
|---|---|
| A "terminal skin": the current composables with a monospace font, square corners and flat chips | It is the degraded skin the maintainer ruled out. Compose `Text` cannot hold a grid: fallback fonts and emoji have their own advances, ligatures merge cells, box-drawing glyphs gap at any line height other than the font's, and tables would still be laid out in pixels. |
| One canvas for the whole home with hand-written scrolling | Re-implements virtualization, fling, overscroll, nested scrolling with the pager, scroll semantics and item keys, and breaks every test that finds `line-<id>`. Gives no pixel the chosen design cannot produce. Kept only for full-screen screens. |
| A custom `View` / `SurfaceView` / OpenGL glyph atlas | `SurfaceView` cannot take a `RenderEffect`, is opaque to the wallpaper setting and to Compose's semantics; an atlas duplicates Android's glyph cache and needs its own shaping and fallback. The log is not redrawn fast enough to need it. |
| A real terminal emulator (VT parser, PTY, a Termux-style view) fed with ANSI text | The log is structured data with actions, not a byte stream. Flattening `LogEntry` to escape codes throws away taps, long-press actions, links, folds and semantics, then has to smuggle them back. |
| `StyledText` → ANSI string → cells | Same loss, smaller. `LineModel` → cells keeps targets and source offsets. |
| Per-cell composables, or a `Text` per row | Thousands of nodes; no control over glyph placement. |
| A separate activity or view model for TTY | Forks state (history, undo, search, pinned turns) and makes live switching impossible. |
| Sharing a wrapped, positioned "document" between the renderers | Wrapping is the part that differs; one of the renderers would get the other's line breaks. Share what a row *is*, not where its glyphs go. |
| Mode-specific script API (`fmt.ttyTable`, a flag telling scripts the mode) | A script must not know or care. `fmt.*` means the same; only screens (`tty.screen`) are grid-specific, and they work in both styles. |
| Letting cells carry arbitrary RGB | Breaks "colors only from the theme's roles", light themes and contrast guarantees. Images are the one exception and are confined to `CellImage`. |
| Fractional-row gaps to match `log.spacing` in dp | One half row and it is no longer a grid; selection and screens' snapshots would need special cases. |
| Dimming with layer alpha | Needs an offscreen layer per dimmed row (the GUI already works around this with `DIMMED_BY_PART`); a color blend is free and is how terminals do faint text. |
| Text drawn in the shader | See §7.1. |
| Shipping TTY in Settings before step 4 | A half-converted home under the name "TTY" is the compromise. Until then it is a config key for testing. |
| Pinning the shader feature to a blur/bloom library or to OpenGL for Android 9–12 | A second rendering path to maintain for a decorative effect; the drawn CRT look covers those versions honestly. |

## 11. Step 0 in detail (the only step that touches existing code heavily)

1. `terminal/LineModel.kt` with `lineModel`, `targets`, `spoken`; tests from `LogCorpus`.
2. `ui/LogView.kt`: `LogLine(model: LineModel, …)`; delete the inline derivations; `renderLine`
   takes `Body.Words`. `TimeMode` moves to `terminal/`. `markRanges` extracted from `markMatches`.
3. `terminal/HeaderModel.kt`; `Header` maps parts to its icons.
4. `ui/LogScroll.kt`: the follow/jump/load-older effects, unchanged in behavior.
5. `FoldState` hoisted; `Foldable` takes `open` and `onToggle`.
6. `TerminalScreen` → `HomeState` + `GuiHome(state, …)`; `when (style)` with only `gui` for now.
7. `Keys.HOME_STYLE` is added in step 2, not here (a key that does nothing should not ship).

Acceptance: no pixel change (the existing gallery and line tests), no new composable taking
`TerminalViewModel`, LayersTest and ExpressiveOnlyTest green.

## 12. Parity checklist (TTY is not released until every row is done)

Log: tap · long-press sheet · swipe copy/delete + undo · links · script buttons · app grid ·
two-column and name-column rows · tables (boxed, plain) · bars, ranges, built-in bars · stats ·
column charts · images · person pictures · notification icons · profile tags · fold · dim ·
unread divider and marks · day dividers · time column (off, commands, groups) · gutter · group
and item gaps · hidden earlier lines · empty-log tips · search results and marks · filter bar ·
jump-to-line flash · scroll follow · jump to newest · scroll thumb · pinch font size ·
`log.wallpaper` · selector fade.

Home: header (every part, both sides, cutout, taps, swipe down) · pinned line and media line ·
prompt (IME, hardware keyboard, composition, cursor, clear, quick actions) · suggestions (list,
chips, icons, idle, wrap) · app selector · key bar (two rows, repeat, auto) · undo/copied band ·
update progress · default-home band · live preview band · secret prompt · action sheets ·
gestures (double tap, swipe up/down) · widgets page swipe · Settings live preview.

Across: every built-in theme dark and light · Material You · custom themes · font family, size,
line height · imported font · RTL · TalkBack · switch access / keyboard focus · font scale ·
process death · rotation / fold / split-screen resize · work and private profiles.

Open, for the maintainer (not blocking): whether the widgets page gets a TTY form; whether the
GUI log also gets the scroll thumb and selection mode added here.
