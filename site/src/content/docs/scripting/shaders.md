---
title: Shaders
description: AGSL shaders over home and the full-screen screens, the ones that come with Kolahal, shared ones, how to write and add your own, and how to turn one off.
---

A shader is a small program that the phone's graphics chip runs for every pixel. Kolahal can draw
one over home and the [full-screen screens](/reference/commands/#screens), such as `matrix` and
`clock`. Settings, the widgets page, menus and the keyboard stay as they are. Shaders are
written in AGSL, Android's version of GLSL.

Shaders need **Android 13 or newer**.

## Turning a shader on

Open the **Shaders** tab and tap a shader. It is checked first: if it doesn't compile, the one that is
on stays, and its row says why. **None** turns shaders off.

The search looks in names, descriptions and tags. The chips under it keep the shaders that work well in a
[dark theme, a light one or over a wallpaper](#what-it-works-well-over), are still or animated, use tilt, can be adjusted, are one-off or are yours.

Each row shows what the shader looks like and what it is: in use, yours or from a
[shared list](#getting-shaders-from-others), animated, using [tilt](#tilt-and-power), edited, turned off (and why),
or with an update. Tap ⓘ for its details: a preview, its version and author, its code, and what you can do with it.
The preview stays at the top while the rest scrolls under it, where the screen is tall enough.

- Some shaders have **parameters**, such as the amount of snow: sliders under the preview, which follows them as you
  drag. To type a number instead, tap and hold the parameter's name or its value. **Reset** sets them to their defaults.
- A parameter that [follows a value](#values-to-follow) is one line: what it follows, its value and a thin bar for it.
  Its slider comes back when you pick **Nothing** in its **Follow** menu.
- Parameters that follow a row of values, such as the eight parts of today's screen time in `seismograph`, are one row
  with a small chart of their values. Tap it to show or hide each of them. There, a parameter's **Follow** button is
  its link icon: hold it to see what it follows.
- **Check** compiles it and says which [uniforms](#uniforms) it uses.
- **Save to folder** writes its file into the script folder, to edit it there, and **Share** passes its code on.
- **Update** brings a newer version of a shared shader.
- **Remove** deletes one of yours. **Undo** brings it back.

Or type:

| Command | What it does |
| --- | --- |
| `shader` | lists the shaders; a tap on one turns it on |
| `shader <name>` | turns it on |
| `shader off` | turns shaders off |
| `shader play <name>` | plays a [one-off shader](#one-off-shaders) once |
| `shader words <name> <words>` | plays it when a notification has one of the words. `off` ends it |
| `shader on` | turns the last one on again |
| `shader set <name>` | a shader's parameters, and what they can be |
| `shader set <name> <parameter> <value>` | changes one: `shader set snowfall amount 1.5` |
| `shader follow <name> <parameter> <value>` | makes one [follow a value](#values-to-follow): `shader follow rain amount weather.rain`, or a formula: `shader follow dunes camels battery / 20`. `off` ends it |
| `shader values` | the values there are to follow |
| `shader reset <name>` | resets its parameters to their defaults |

The setting behind them is [`screen.shader`](/reference/config/#screens). These come with Kolahal. The ones marked "moves" are drawn again and again; the others cost
nothing while the screen is still.

| Name | What it looks like |
| --- | --- |
| `crt` | an old CRT monitor: curved glass, scanlines, a glow and a little color fringe |
| `lcd` | an old handheld's display: small square pixels, and text casts a shadow on the back |
| `eink` | an e-reader's page: matte, nearly without color, with flat areas in a dither pattern |
| `paper` | uncoated paper: a fine grain and fibres, and ink that soaks in a little |
| `riso` | a stencil print: a second impression of the text in the accent color, a little off register |
| `graph` | squared paper behind the text |
| `foil` | plain text stamped in foil: bands of the theme's colors that slide with your finger and as the phone leans |
| `lamp` | a soft pool of light where you last touched, and the rest of the page a little dimmer |
| `dusk` | a sky behind the text that follows the phone's clock: night, dawn, daylight and dusk |
| `frost` | ice grows in from the edges and corners, and melts under your finger. The amount of ice can be changed |
| `mountains` | flat mountain ridges in mist, after woodblock prints. They breathe slowly, follow the hour, and shift a little as the phone leans. The number of birds can be changed. It moves |
| `pond` | a still pond under far hills, after woodblock prints, with rings of rain and a path of light. It follows the hour. The number of birds can be changed. It moves |
| `dunes` | a long dune after woodblock prints, with ranges of dunes and far mesas behind it, and a caravan that climbs it once an hour. It follows the hour, and a wider screen shows more of the desert. The number of camels can be changed. It stands still, drawn again once a minute |
| `rain` | a calm rain seen out of a window, all of it some way off: short, faint streaks that fall slowly, nearly straight down, and mist; a touch holds it off. Its amount and speed can be changed. It moves |
| `snowfall` | snow falls behind the text and drifts as the phone leans; a touch blows it away. Its amount and size can be changed. It moves |
| `grove-snow` | small, crisp flakes fall and wander slowly behind the text, and dim with the hour. It moves |
| `grove-autumn` | a few orange, gold and rust leaves turn as they drift down, and dim with the hour. It moves |
| `aurora` | curtains of northern lights in the top of the screen, behind the text. It moves |
| `night` | stars that twinkle slowly and shift a little as the phone leans, and a meteor now and then; on a light theme, an engraved star chart. Their number can be changed. It moves |
| `fireflies` | small lights wander behind the text, and a touch draws them near; on a light theme, soft motes in the sun. It moves |
| `contour` | the contour lines of a map, slowly changing; a touch raises a hill. It moves |
| `hearts` | a [one-off](#one-off-shaders): hearts rise from the bottom of the screen, sway and fade. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `hearts-fountain` | a [one-off](#one-off-shaders): hearts burst from one point at the bottom, fan up and out, slow under gravity and fade near the top of their arcs. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `hearts-falling` | a [one-off](#one-off-shaders): hearts drift down from the top like petals, swaying and turning, in three depths. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `hearts-stream` | a [one-off](#one-off-shaders): small hearts rise along the right edge on a wavy path, and the middle of the screen stays clear. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `hearts-poppers` | a [one-off](#one-off-shaders): two bursts of hearts from the bottom corners cross in the middle, then fall back and fade. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `hearts-fireworks` | a [one-off](#one-off-shaders): a spark rises and opens into a heart of sparks, which widens, twinkles and sinks, three times. It can play [over other apps](#over-other-apps) |
| `hearts-bounce` | a [one-off](#one-off-shaders): hearts drop from the top, bounce on the bottom edge and settle in a row, then hop and fall away. Their number and the [style of the heart](#the-hearts-style) can be changed. It can play [over other apps](#over-other-apps) |
| `ink` | a touch drops a faint cloud of ink that spreads behind the text and fades. It moves |
| `aura` | a soft light along the edges of the screen, rounded like its corners, whose colors drift slowly around it; a touch gathers it on the nearest edge. Its width, brightness, colors and speed can be changed. It moves |
| `day-ring` | the day as a dial around the edge of the screen, midnight at the top: a thin line, a point of light for now, and a glow over the hours gone by, as wide and bright as the screen was on in each three hours. It follows your [screen time](#values-to-follow) |
| `windows` | the week's screen time as an apartment block seen from the street: a floor for each day, the oldest lowest and today's on top behind a balcony rail in the accent color, with as many of its eight windows lit as that day had screen time. On a light theme it is the block by day, and a lit window is one with its blind up. It follows the hour and your [screen time](#values-to-follow) |
| `lanterns` | paper lanterns let go into a festival night over dark water: seven for the week's unlocks, the oldest on the left, each as high as that day's unlocks, today's in the accent color. Far lanterns rise from the far shore and people watch from the bank. It follows the hour and your [unlocks](#values-to-follow). It moves |
| `record` | the week's screen time as a record on a turntable seen from above under a lamp: seven bands of grooves, the oldest outside and today's inside where the needle rides, each as wide as that day's screen time. The label is in the accent color. It follows the hour and your [screen time](#values-to-follow). It moves |
| `candle` | a candle in a brass dish on a table in a quiet room. It burns down as today's screen time is spent: its top stands at the accent-colored notch on a brass rod after a usual day, and past that it is a stub in a pool of wax and smokes. The flame lights the wall and the table; on a light theme it is a pale room with the window's light moving across the wall. It follows the hour and your [screen time](#values-to-follow). It moves |
| `seismograph` | today's screen time drawn by a recording instrument on a desk: a pen on a long arm writes the day on a strip of paper from midnight to midnight, level while the phone rested and shaking as hard as each three hours were busy, and stops at now, marked on the frame in the accent color. It follows the hour and your [screen time](#values-to-follow) |
| `kintsugi` | the week's unlocks as a dark tea bowl mended with gold, on a table by a window: a seam for each day from left to right, the oldest first, each as long and branched as that day's unlocks, today's the brightest. On a light theme the bowl is pale over the log. It follows the hour and your [unlocks](#values-to-follow) |
| `hourglass` | today's screen time as an hourglass on a table: the top holds what is left of a usual day, and the sand stands at a mark in the accent color when it is spent; past that it spills in a heap at the foot of the stand. It follows the hour and your [screen time](#values-to-follow) |
| `pulse` | a [one-off](#one-off-shaders): light runs in around the edges of the screen from one point, swells once and is gone. Its colors and where it starts can be changed. It can play [over other apps](#over-other-apps) |
| `comet` | a [one-off](#one-off-shaders): one bright head with a long tail runs a full lap around the edge, quickest halfway, and slows as it gets home. Its colors can be changed. It can play [over other apps](#over-other-apps) |
| `breath` | a [one-off](#one-off-shaders): the whole edge glows in and out twice, softly, its colors drifting round, with no running fronts. Its colors can be changed. It can play [over other apps](#over-other-apps) |
| `scan-line` | a [one-off](#one-off-shaders): one bright line travels down the screen with a short afterglow in rows behind it, as on an old tube. Its colors can be changed. It can play [over other apps](#over-other-apps) |
| `spill` | a [one-off](#one-off-shaders): light gathers at the top middle, where notifications come from, runs down both sides and drains away at the bottom. Its colors can be changed. It can play [over other apps](#over-other-apps) |
| `trace` | a [one-off](#one-off-shaders): a heartbeat line runs across the bottom of the screen once, two beats behind a bright head, and fades after it. Its colors can be changed. It can play [over other apps](#over-other-apps) |
| `northern-lights` | a [one-off](#one-off-shaders): a curtain of soft color falls from the top edge, its folds drifting slowly, and fades like northern lights. Its colors can be changed. It can play [over other apps](#over-other-apps) |

On the **Shaders** tab, a tap turns one on. Its details (ⓘ) have a preview: swipe left or right for the
others, and hold the eye to see your own home with it, without turning it on.

## What it works well over

A shader's details rate it for a dark theme, a light theme and a wallpaper that shows behind the log
([`log.wallpaper`](/reference/config/#log)):

- **Good**: it looks as it should.
- **Fair**: it works, but shows less, or takes some of what is behind it.
- **Poor**: it hides what is behind it, or is hard to read.

If more than 20% of your wallpaper shows, turning on a shader that is poor over one gives a warning. It is turned on all
the same. A scene gets none, since it hides the wallpaper while it is on (below). The author rates a shader in its
[header](#the-header), and one without a rating shows none.

A [one-off shader](#one-off-shaders) can have a fourth rating, for **other apps**: only one that is good or fair
there plays [over the app in front](#over-other-apps).

A scene, a shader that can be your [live wallpaper](#as-a-live-wallpaper), brings its own ground: while it is on over
home, your wallpaper doesn't show behind the log, whatever `log.wallpaper` says. The setting keeps its value, and the
wallpaper is back when the shader is off. Shaders that work on text and one-offs leave it as it is.

## As a live wallpaper

A shader can be your phone's wallpaper, behind any launcher and on the lock screen. Open its details and tap **Set as
wallpaper**, or type `wallpaper shader pond`. Android's own preview opens with it each time: there you see it and choose
the home screen, the lock screen or both. Kolahal has one live wallpaper, so where it is your wallpaper already the new
shader takes the old one's place. `wallpaper` says what your wallpaper is, and `wallpaper live` opens Android's own
list of live wallpapers, for a phone whose wallpaper settings don't link to it.

- It is apart from the shader over home: both can be on, or one of them.
- Its parameters, and what they follow, are the same as on home. The theme's colors are Kolahal's.
- Kolahal works out the values it follows (your screen time, your unlocks, what scripts give) while it is your home
  app or open. Behind another launcher they stay as they were when Kolahal was last open.
- It moves only while you can see it: behind an app and with the screen off its frames stop. In battery saver, with **Remove
  animations** on, when the phone is hot or nearly out of battery, and after a minute without a touch unless it charges, a moving
  shader stands still. When what it shows changes (another shader, a parameter, the theme's colors, a value it follows), it
  is drawn once, also while you can't see it.
- Shaders that change text (`crt`, `lcd`, `eink`, `foil`, `riso`) and [one-off shaders](#one-off-shaders) can't be one.
- It runs apart from home, so a shader that crashes doesn't stop Kolahal. One that does so three times in a row isn't drawn until
  its code changes.
- To end it, pick another wallpaper in Android's settings.

To see it behind the log, use [`log.wallpaper`](/reference/config/#log).

## A picture from a shader

With [Lab](/guides/themes-and-settings/#lab) on, a scene can also become a still picture: a wallpaper that costs no
power, or a picture to keep or share. Open the scene's details and tap **Make a picture**, under **Set as wallpaper**,
or type `wallpaper picture pond`. Shaders that change text and one-off shaders can't make one, as they can't be a
live wallpaper. Lab's features may change or go in a later version.

- The picture is at the top, in your screen's proportions, and follows every change. Tap it to see it on the whole screen.
- Under it are the scene's parameters. What you change here stays with the picture: home and the live wallpaper keep theirs.
- **Frame** picks the moment of a scene that moves, **Time of day** the hour of one that follows it, and **Shuffle**
  another arrangement of a scene that declares `seed`. Each shows only where the scene has it.
- **Dark** or **Light** picks the theme's colors, and **Size** the picture's: this screen, 2K or 4K, at your screen's
  proportions. A larger picture is a larger copy of the screen, not a finer one. A size the phone can't draw is greyed, with why.
- **Save to gallery** puts it in Pictures/Kolahal; **Undo** in the message deletes it again. **Share** is at the top.
- **Set as wallpaper** opens Android's own screen to crop it and choose the home screen, the lock screen or both. On a
  phone without that screen Kolahal asks where, and replaces your wallpaper itself: the old one can't come back.

## One-off shaders

A one-off shader runs once for some seconds, over the shader that is on, and goes. It draws over everything on the
screen, text and the shader that is on included. Tap one under **One-off** in the **Shaders** tab, or its **Play**, and it plays
right there, over the tab; **Play** in its details does the same. On home, type `shader play hearts`. It can't be turned on to stay.

**On a notification.** In its details, under **Plays on a notification**, type words with commas between, such as a name. It
plays when a notification's title or text has one of them: whole words, in any case. Kolahal needs notification access for
this: without it, the field is "Needs notification access" and an **Allow notification access** button, and the words you
typed come back once it is allowed. Notifications from the private space don't count. `shader words hearts Alex, picnic` does the same, and
`shader words hearts off` ends it. It can also play [over the app you are in](#over-other-apps).

A script can play one too, with [`shader.play`](/scripting/api/#shaderplayname-seconds).

- One plays at a time. Another that comes meanwhile is left out.
- It waits for home, or the Shaders screen, to be in front, for half an hour at most.
- It isn't played in battery saver, with **Remove animations** on, when the phone is hot or nearly out of battery, or with a
  screen reader on.

To write one, say for how many seconds it runs, from 1 to 10, with `// @once 6`. Its `progress` uniform goes from 0 to 1
meanwhile: start and end with nothing.

### The heart's style

`hearts`, `hearts-fountain`, `hearts-falling`, `hearts-stream`, `hearts-poppers` and `hearts-bounce` draw their hearts
in the style you choose under **Heart** in their details: **Glossy** (the default), **Flat**, **Gradient**, **Outline**,
**Double**, **Neon**, **Cartoon** or **Halftone**. Each of them keeps its own. `shader set hearts style neon` does the
same.

### Over other apps

A one-off can play over the app you are in, so you see it without going home. It is off until you turn it on:

1. On the **Shaders** tab, under the one-off shaders, **Play over other apps** says what it needs first:
   "Needs display over other apps", and "Needs notification access" while Kolahal can't see your notifications.
   Tap **Allow display over other apps**. Kolahal says what it will draw and when: tap **Allow**, and Android's
   **Display over other apps** page opens: turn Kolahal on there. `allow overlay` does the same.
2. Back in Kolahal, turn on **Play over other apps**. `config set screen.shader-over-apps on` does the same.

Then a notification with one of a shader's words plays it at once over the app in front, when all of these hold:

- The shader is rated good or fair for other apps in its [header](#the-header), as every one-off that comes with Kolahal is.
- Home isn't in front, the screen is on and the phone is unlocked.
- Do Not Disturb doesn't hold the notification back.
- No full-screen app, such as a video or a game, is in front.
- Nothing keeps a one-off from playing on home: battery saver, **Remove animations**, a hot phone, a nearly empty
  battery or a screen reader.

Otherwise it waits for home, as before. One that played over an app doesn't play again on home.

- The shader is drawn in a window that takes no touches, so the app under it works as before. Android passes
  touches only through a window that is partly see-through, so a shader is a little fainter there than on home.
- Kolahal reads nothing of the app under it, and keeps nothing about it.
- If you turn Display over other apps off later, the setting says that it needs it, and one-offs wait for home.
- Some screens hide the windows of other apps, as Android's own Settings and Files do. Nothing shows over them.
- After Kolahal starts again in the background, as after an update, one-offs wait for home until home has been in
  front once.
- `shader play` and a script's `shader.play` play over home only.
- Android Go and other phones with little memory can't allow Display over other apps, so Kolahal doesn't offer it there.

To let a shader of yours play there, add `apps good` (or `apps fair`) to its `@suits` line, and draw on a clear ground:
over another app there is nothing to read, so `content.eval` gives 0 everywhere, and what the shader returns is laid over
the app as premultiplied color. Return only what you draw: no color for the page, and no veil over the app. The template
`template-once` does.

## If something goes wrong

A shader can make the screen hard to read. These always work:

- Type `shader off`. The keyboard and the prompt work, even when you can't see them.
- Hold two fingers still on the screen for a second.
- **Shaders → None**. The **Shaders** tab has no shader over it.
- `config set screen.shader off`.

Shaders also stop on their own:

- A shader that doesn't compile isn't used. A line in the log says why, with the line of the file
  where the compiler stopped. Tap it to check the shader again.
- If Kolahal crashes or freezes while a shader that hasn't proven itself is on, or while a one-off plays,
  the shader is turned off at the next start.

A shader that was turned off stays off until its file changes or you turn it on again; a one-off, until
you turn a shader on. One that was playing [over another app](#over-other-apps) stays away from other apps until then,
too. After three times in a row, `screen.shader` is set to `off`.

Shaders are off while TalkBack or another screen reader is on. Moving shaders stand still in battery
saver, with **Remove animations** on, when the phone is hot or nearly out of battery, and after a
minute without a touch unless the phone is charging. Shaders that follow the phone's lean stop following
it [at the same times](#tilt-and-power).

## Adding your own

A shader is a text file that ends in `.agsl`. Its name is the file's name in lowercase: `My Glow.agsl`
is `my-glow`. A file can have at most 64 KB. A few words that `shader` uses itself, such as `off` and
`ls`, can't be a shader's name.

There are three ways to add one, all under **Shaders → Add shader**:

- **Import a file.** Pick one or more `.agsl` files. (**Add script → Import a file** in the Scripts screen
  takes them too.)
- **Read the script folder again.** Put the file in the folder you picked with `script dir`, at the top or
  in its `shaders` folder. [`script import`](/scripting/overview/#moving-scripts) reads it as well.
- **Start from a template.** A short shader to edit, with comments. See [Writing one](#writing-one).

Your shaders are listed under **Shaders → Your shaders**, and are part of the
[backup file](/guides/backup/).

`script export` also writes your shaders into the folder's `shaders` folder, so you can keep them in
git or edit them on a computer. `shader save <name>` writes a copy of any shader there. After editing
a file, run `shader reload` to use it. To change one on the phone, open it in the
[editor](#editing-one).

`shader rm <name>` deletes a shader of yours. A file of yours with the name of one that comes with
Kolahal is used instead of it, until you remove it.

| Command | What it does |
| --- | --- |
| `shader check <name>` | compiles the shader and says which uniforms it uses |
| `shader edit <name>` | opens the shader's code in the [editor](#editing-one) |
| `shader reload` | reads the `.agsl` files in the script folder again |
| `shader save <name>` | writes a shader into the script folder's `shaders` folder, to edit it there. A template's name, such as `template-tint`, makes a shader of yours from it |
| `shader rm <name>` | deletes your shader. `shader delete <name>` does the same |

### Editing one

**Edit** in a shader's details (**Shaders**, then the details button of its row) opens its code
in the same editor as [scripts](/scripting/editor/). `shader edit <name>` does the same at the prompt.

- The code is colored as AGSL: its types, the word of each [header](#the-header) line, such as `@name`, and
  the names Kolahal sets, such as `time` and `content`.
- Find, undo, Go to line and the symbol bar work as for a script. There is no **Run**, API reference or
  completion: those are a script's.
- **Save** (or `Ctrl+S`) saves the shader and checks it as `shader check` does. The message line says
  **Saved · compiles**, or in red why it can't be used, such as `line 12: unknown identifier 'x'`. Tap it to
  go to that line. A shader that doesn't compile is saved all the same, as a file you import is: home goes on
  without it, and a line in the log says why.
- When the shader is on, or is your live wallpaper, what you save shows at once.
- Closing, Back and switching to another app save your changes too. When they can't be saved, the editor stays
  open and says why.
- When the script folder (`script dir`) has a file of the shader, saving writes that file too, so `shader reload`
  reads what you saved.

A shader that comes with Kolahal isn't changed in place. Saving it makes a file of yours with the same
name, which is used instead of it, as after `shader save` and `shader reload`. Its details then say it is
from you, and **Remove** brings Kolahal's back.

## Getting shaders from others

**Shaders → Add shader → Get more shaders** lists the shaders that people share in
[kolahal-scripts](https://github.com/mtwabpin/kolahal-scripts) on GitHub, beside its scripts. Each is
reviewed before it is added.

1. Tap a shader to see what it looks like, its license and its code.
2. Tap **Install**. The file must match the checksum in its list.
3. Tap **Use**. Installing doesn't turn it on.

It is then one of your shaders. When the list has a newer version, its row says **Update to …**. One you
edited is replaced only when you ask, and **Undo** puts your copy back.

The sources are the same as for scripts: [add your own](/scripting/overview/#your-own-scripts-repository)
with **Sources**, at the top of the page. Kolahal doesn't check the shaders of a source you add. As for
scripts, Kolahal asks a source for the list of its own version first, and for the general list when the
source has none.

| Command | What it does |
| --- | --- |
| `shader get` | lists the shared shaders, and marks those you installed that have a newer version |
| `shader get <name>` | installs or updates one. `shader get <source>/<name>` picks the source |
| `shader get --update` | updates every shader you installed this way |

The list is kept for an hour. `--refresh` reads it again, and `--replace` replaces a copy you edited.

To share a shader from [a repository of your own](/scripting/overview/#your-own-scripts-repository), put it
in `shaders/<name>/<name>.agsl` with a full [header](#the-header).

## Writing one

The quickest start is a template: a working shader of 30 to 50 lines whose comments say what each line is for.

1. **Shaders → Add shader → Start from a template**, and pick one: each row shows a small picture of the
   screen with it on. A copy becomes one of your shaders: `my-tint`, or `my-tint-2` when that name is taken. With a
   script folder (`script dir`), its file is written into the folder's `shaders` folder. Without one, Kolahal asks
   for a folder, and **Save to folder** in the shader's details writes the file there. `shader save template-tint` does the
   same at the prompt.
2. Edit it: **Edit** in its details opens it in the [editor](#editing-one). Or edit the file on a computer. Its
   comments say what to change first.
3. In the editor, **Save** checks it at once. For a file you changed elsewhere, run `shader reload`, or
   **Add shader → Read the script folder again**. If it doesn't compile, a line in the log says why.

| Template | What it shows |
| --- | --- |
| `template-tint` | Tint: the smallest shader. A theme color over the screen, and one parameter |
| `template-scene` | Hills: a scene behind the text only, for a dark and a light theme and as a wallpaper |
| `template-moving` | Sea: a scene that moves with `time`, kept cheap with `@fps` |
| `template-touch` | Ripple: a ring from each touch, with `touch`, `touchAge` and `pressed`, that costs nothing once it fades (`@settles`) |
| `template-value` | Gauge: a parameter that follows the battery (`@follow`), a choice of words, and a formula |
| `template-once` | Burst: a [one-off](#one-off-shaders) with `@once` and `progress`, and the words that play it |

To have an AI assistant write one, type `skill.md shaders` in full and paste the guide it copies into
the assistant ([more](/scripting/overview/#writing-one-with-an-assistant)).

Or start from nothing. This one tints the screen with the theme's accent color:

```glsl
// @name Tint
// @description A little of the accent color over everything

uniform shader content;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    half4 c = content.eval(xy);
    return half4(mix(c.rgb, accent.rgb * c.a, 0.1), c.a);
}
```

`content` is what is on the screen. `content.eval(xy)` gives the color of the pixel at `xy`, in
pixels from the top left corner of the surface.

Good to know:

- The shader's result and what `content.eval` gives are premultiplied: red, green and blue are already
  multiplied by the alpha. `c.rgb` of a half-transparent pixel is darker than its color.
- `uniform shader content;` is required, and it is the only shader input. A shader has no other
  images, files or network.
- Look at a few pixels around each one, not hundreds. A shader that does too much work makes home slow.
- An `@animated` shader runs over every pixel of the screen at each frame. A scene that moves slowly costs a
  third with `// @fps 10`, and one that moves only after a touch costs nothing once it fades with `// @settles`.
- To draw behind the text only, as the scenes do, tell the page from what is on it by its alpha. Kolahal
  draws the page a step under opaque (an alpha of 254/255) while a shader reads it, and text, icons, photos
  and emoji on it are opaque: a pixel with `c.a > 0.999` is covered, whatever its color. A test by color
  alone would paint over an icon's white or dark parts. `template-scene` uses both: the alpha, and the
  color for the soft edges of letters.
- If the screen shows your wallpaper ([`log.wallpaper`](/reference/config/#log)), the pixels behind the
  text are more transparent still: check `c.a`.
- Moving pixels far from where they are makes a tap miss what it shows. Keep it under half a cell,
  except near the corners.

### The header

Comments at the top say what the shader is. All are optional:

| Comment | Meaning |
| --- | --- |
| `// @name Tint` | its name for people |
| `// @description …` | what it looks like. `shader` shows it |
| `// @param amount 0.0..2.0 = 1.0 …` | something people can change. See [Parameters](#parameters) |
| `// @suits dark good, light fair, wallpaper poor` | [what it works well over](#what-it-works-well-over): `good`, `fair` or `poor`. A one-off can add `apps good`, to play [over other apps](#over-other-apps) |
| `// @text` | it works on text, so it can't be a [live wallpaper](#as-a-live-wallpaper) |
| `// @once 6` | it is a [one-off](#one-off-shaders), and runs for this many seconds |
| `// @animated` | it changes on its own, so it is drawn up to 30 times a second. Without it a shader is only drawn when the screen changes, so it uses no power while the screen is still |
| `// @fps 10` | an `@animated` shader is drawn this many times a second, from 1 to 30 (30 without it). Use it for a scene that moves slowly, such as drifting mist, which looks the same at 10 |
| `// @still 41.5` | where a [picture](#a-picture-from-a-shader) of it starts, in seconds of `time`, from 0 to 120 (0 without it). Pick a moment where the scene looks its best |
| `// @settles 4` | a touch changes the picture for this many seconds, from 0.5 to 60 (60 without it). Use it for ripples or ink that fade: a shader that reads the touch is drawn 30 times a second for that long, and one whose only clock is `touchAge` stops then until the next touch |
| `// @author …` | who made it. A shared shader needs it, as it does `@version` and `@license` |
| `// @version 1.0` | its version |
| `// @license MIT` | its license |
| `// @tags glow, retro` | words that find it in Get more shaders |
| `// @requires 0.5.17` | the oldest Kolahal it works with |

### Parameters

A parameter is something people can change in the shader's details or with `shader set`. A header line names
it, says what it can be and gives its default and its title. A `float` uniform of its name holds its value:

```glsl
// @param amount 0.0..2.0 = 1.0 Amount of snow
// @param shape round|star = round Shape of the flakes

uniform float amount;
uniform float shape;
```

- **A range** is a slider. If nothing in it has a decimal point (`1..8 = 3`), it goes in whole numbers.
- **Words**, two to eight of them, are buttons: up to four in one row, more in two. The uniform gets the word's place: 0
  for the first, 1 for the second.
- A shader can have up to 8. Their names can't be those of the uniforms below.
- Values are kept for each shader, in the [backup file](/guides/backup/) too, and when the shader is updated. One that
  the new version can't have becomes the default again.
- A shader that isn't `@animated` is drawn again when one changes.

#### Values to follow

A parameter can follow a value instead of its slider: tap **Follow** under it. Kolahal has these values:

| Value | What it is |
| --- | --- |
| `battery` | how full the battery is, from 0 to 100 |
| `charging` | 0, or 1 while the phone is plugged in |
| `day` | the time of day in hours, from 0 to 24 |
| `night` | 0 by day and 1 at night, with a dusk and a dawn |
| `unlocks` | how often you unlocked the phone today. 100 is a parameter's most |
| `unlocks.usual` | today's unlocks against a usual day, the average of the six days before: 0 for none, 1 for as many, 2 at most |
| `screen` | today's screen time, in minutes. 480 is a parameter's most |
| `screen.usual` | today's screen time against a usual day, as for unlocks |
| `away` | how many minutes the phone was locked before you last unlocked it. 240 is a parameter's most |

Each day and each part of today has a value of its own too, for a shader that draws them side by side. They aren't in
the **Follow** menu: use them with `shader follow` or in a header's `@follow`, and `shader values` shows them.

| Value | What it is |
| --- | --- |
| `unlocks.d1` … `unlocks.d7` | each of the last seven days' unlocks, `d7` today, from 0 to 1: 1 is the day of the seven with the most |
| `screen.d1` … `screen.d7` | the same for screen time |
| `screen.h1` … `screen.h8` | each three hours of today, `h1` from midnight, from 0 to 1: how much of those three hours the screen was on. Hours still to come are 0 |

Screen time and `away` need usage access (type `allow usage`). Without it they have no value. Unlocks are counted
without it too, but only those Kolahal sees while it runs. So without usage access, a shader that follows any of these
says "Needs usage access" on the **Shaders** tab and has an **Allow usage access** button in its details, and the
**Follow** menu says "needs usage access" beside these values. The shader still draws, on the values it was set to. A `usual` value needs a day before today. Kolahal works these
values out only while a shader on home or the live wallpaper follows one, or while the **Shaders** tab is open for the
shaders it lists: after you unlock the phone, when home comes back after five minutes or more, and every five minutes
while home stays in front.

Scripts give more, with [`shader.give`](/scripting/api/#shadergivename-value). The library's `weather` gives `weather.rain` and
`weather.snow`: let the amount of `rain` follow `weather.rain`, and it rains on home as hard as it rains outside.

- A number is spread over the parameter's range: a full battery is its most and an empty one its least. A script's number
  from 0 to 1 is spread the same way. The parameter glides there in two seconds.
- Or add a **formula** for it, with **Add formula** in its **Follow** menu: with `battery / 20`, a caravan has 5 camels at 100% and 2 at 40%. The result is the
  parameter's value, taken into its range. The value is in it by its name, and the rest is what the
  [calculator](/guides/web-and-calculator/) knows: `+ - * /`, parentheses, `min`, `max`, `round` and more.
- A word picks the parameter's word of that name.
- While there is no value (the script is off or hasn't run yet, or there is no usage access), the value it was set to counts.
- A parameter can suggest a value: end its line with `@follow`, as in
  `// @param amount 0.0..2.0 = 1.0 Amount of rain @follow weather.rain`. A formula can be there as well. It follows that until you pick
  something else.
- A shader needs no permission for this. It can't send or save anything: it only draws.

### Uniforms

Kolahal sets the uniforms below when your shader declares them, and skips those it doesn't. A uniform
of another name stays 0. `shader check` lists both.

| Uniform | Type | What it is |
| --- | --- | --- |
| `content` | `shader` | what is on the screen. Required |
| `resolution` | `float2` | the size of the surface, in pixels |
| `cell` | `float2` | the size of one character cell, in pixels |
| `grid` | `float2` | how many columns and rows of cells fit |
| `origin` | `float2` | where the first column starts, in pixels from the left, and 0 |
| `time` | `float` | seconds since the shader was turned on. It moves only in an `@animated` shader |
| `progress` | `float` | how far a [one-off shader](#one-off-shaders) has come, from 0 to 1 |
| `touch` | `float2` | the last touch, in pixels. `-1, -1` before the first one |
| `touchAge` | `float` | seconds since that touch, only in an `@animated` shader. 1000 before the first touch |
| `pressed` | `float` | 1 while a finger is down on the surface, otherwise 0. A shader that isn't `@animated` is drawn again when it changes |
| `tilt` | `float2` | how far the phone leans, from about -1 to 1 each way: `x` is positive when the right side is lower, `y` when the bottom edge is. `0, 0` when the phone is level or has no motion sensor. See [Tilt and power](#tilt-and-power) |
| `battery` | `float` | how full the battery is, from 0 to 1 |
| `charging` | `float` | 1 while the phone is plugged in, otherwise 0 |
| `corner` | `float` | the radius of the screen's rounded corners, in pixels: the largest, where they differ. 0 when they are square, and where the surface isn't the whole screen, as in a shader's details. Use it to keep what you draw at the edges inside the screen |
| `cutout` | `float4` | the camera's cutout at the top of the screen, in pixels: left, top, right, bottom. All 0 when there is none, in landscape (where the camera is at a side), and where the surface isn't the whole screen. Use it to keep what you draw at the top clear of the camera |
| `wallpaper` | `float` | 1 as a [live wallpaper](#as-a-live-wallpaper) and in a [picture](#a-picture-from-a-shader), where `content` is the theme's background alone, otherwise 0 |
| `seed` | `float` | the scene's arrangement: 0 on home and as the live wallpaper, another number after **Shuffle** in a [picture](#a-picture-from-a-shader). Declaring it is what offers Shuffle. Add it to where your noise is read (`noise(x, 17.0 + seed)`), so 0 keeps the scene as it is |
| `dark` | `float` | 1 in a dark theme, 0 in a light one |
| `date` | `float4` | the phone's date and time: the year, the month (1 to 12), the day of the month, and the seconds since midnight, in local time. A shader that isn't `@animated` is drawn again once a minute when it declares it |
| `bg` `surface` `fg` `dim` `accent` `error` `warn` `good` `info` `link` | `half4` | the theme's colors. Declare them with `layout(color)`, so they match the screen's colors |

`half` and `float` are both fine for the numbers; `half2` and `float2` as well. The types have to match:
a `time` declared as `float2` is an error, and so is an array.

A shader that isn't `@animated` and declares `battery` or `charging` is drawn again when they change. `corner`
stays the same while the screen does.

### Tilt and power

`tilt` is measured from how the phone is usually held: what counts as level follows the phone within
some seconds. So a phone in a stand, or in your hand while you lie down, leans by 0. It is smoothed, and 30
degrees is 1.

Kolahal reads the phone's motion sensor only while a shader that declares `tilt` shows: with home (or the
shader's preview on the **Shaders** tab) in front, or as your live wallpaper while the wallpaper shows. It stops in battery saver, with **Remove animations** on, when the phone is hot or nearly out of
battery, and after a minute without a touch unless the phone is charging. `tilt` then goes back to `0, 0`.

A shader that isn't `@animated` is drawn again only when the lean changes, at most 30 times a second. On a
table it isn't drawn at all.

- Leave text where it is: use `tilt` for what is behind it or for its color. Text that moves with the
  phone is hard to read, and a tap misses it.
- Use `tilt` as a place, such as an offset. `time * tilt` jumps when the phone moves.
- A shader with `tilt` must look right at `0, 0`.
