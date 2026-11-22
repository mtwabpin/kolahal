---
title: Shaders
description: AGSL shaders over home and the full-screen screens, the ones that come with Kolahal, shared ones, how to write and add your own, and what turns a slow one off.
---

A shader is a small program that the phone's graphics chip runs for every pixel. Kolahal can draw
one over home and the [full-screen screens](/reference/commands/#screens), such as `matrix` and
`screen clock`. Settings, the widgets page, menus and the keyboard stay as they are. Shaders are
written in AGSL, Android's version of GLSL.

Shaders need **Android 13 or newer**.

## Turning a shader on

Open **Settings → Shaders** and tap a shader. It is checked first: if it doesn't compile, the one that is
on stays, and its row says why. **None** turns shaders off.

Each row shows what the shader looks like and what it is: in use, yours or from a
[shared list](#getting-shaders-from-others), animated, edited, turned off (and why), or with an update. Tap
ⓘ for its details: a small preview, its version and author, its code, and what you can do with it.

- **Check** compiles it and says which [uniforms](#uniforms) it uses.
- **Copy to script folder** and **Share** give you the file or its code, to edit it or pass it on.
- **Update** brings a newer version of a shared shader.
- **Remove** deletes one of yours. **Undo** brings it back.

Or type:

| Command | What it does |
| --- | --- |
| `shader` | lists the shaders; a tap on one turns it on |
| `shader <name>` | turns it on |
| `shader off` | turns shaders off |
| `shader on` | turns the last one on again |

The setting behind them is [`screen.shader`](/reference/config/#screens). These come with Kolahal. The ones marked "moves" are drawn again and again; the others cost
nothing while the screen is still.

| Name | What it looks like |
| --- | --- |
| `crt` | an old CRT monitor: curved glass, scanlines, a glow and a little color fringe |
| `bloom` | a soft halo around text, in its own color |
| `lcd` | an old handheld's display: small square pixels, and text casts a shadow on the back |
| `eink` | an e-reader's page: matte, nearly without color, with flat areas in a dither pattern |
| `paper` | uncoated paper: a fine grain and fibres, and ink that soaks in a little |
| `riso` | a stencil print: a second impression of the text in the accent color, a little off register |
| `graph` | squared paper behind the text |
| `foil` | plain text stamped in foil: bands of the theme's colors that slide with your finger |
| `lamp` | a soft pool of light where you last touched, and the rest of the page a little dimmer |
| `dusk` | a sky behind the text that follows the phone's clock: night, dawn, daylight and dusk |
| `frost` | ice grows in from the edges and corners; a touch melts a clear spot |
| `rain` | drops of the theme's accent color fall behind the text. It moves |
| `snowfall` | snow falls behind the text, and a touch blows it away. It moves |
| `aurora` | curtains of northern lights in the top of the screen, behind the text. It moves |
| `night` | stars that twinkle slowly, and a meteor now and then. It moves |
| `fireflies` | small lights wander behind the text, and a touch draws them near. It moves |
| `pond` | light ripples as on shallow water, and a touch sends rings out. It moves |
| `contour` | the contour lines of a map, slowly changing; a touch raises a hill. It moves |
| `ink` | a touch drops a faint cloud of ink that spreads behind the text and fades. It moves |

## If something goes wrong

A shader can make the screen hard to read. These always work:

- Type `shader off`. The keyboard and the prompt work, even when you can't see them.
- Hold two fingers still on the screen for a second.
- **Settings → Shaders → None**. Settings has no shader over it.
- `config set screen.shader off`.

Shaders also stop on their own:

- A shader that doesn't compile isn't used. A line in the log says why, with the line of the file
  where the compiler stopped. Tap it to check the shader again.
- A shader that stays too slow for the phone is turned off: one frame in ten takes the GPU more than
  33 ms, for a second or two on end. The log says how long its frames took. On a phone that doesn't
  measure the GPU's time, only a shader that hangs is turned off.
- If Kolahal crashes or freezes while a shader that hasn't proven itself is on, the shader is turned
  off at the next start.

A shader that was turned off stays off until its file changes or you turn it on again. After three
times in a row, `screen.shader` is set to `off`.

Shaders are off while TalkBack or another screen reader is on. Moving shaders stand still in battery
saver, with **Remove animations** on, when the phone is hot or nearly out of battery, and after a
minute without a touch unless the phone is charging.

## Adding your own

A shader is a text file that ends in `.agsl`. Its name is the file's name in lowercase: `My Glow.agsl`
is `my-glow`. A file can have at most 64 KB. A few words that `shader` uses itself, such as `off` and
`list`, can't be a shader's name.

There are two ways to add one, both under **Settings → Shaders → Add shader**:

- **Import a file.** Pick one or more `.agsl` files. (**Add script → Import a file** in the Scripts screen
  takes them too.)
- **Read the script folder again.** Put the file in the folder you picked with `script dir`, at the top or
  in its `shaders` folder. [`script import`](/scripting/overview/#moving-scripts) reads it as well.

Your shaders are listed under **Settings → Shaders → Your shaders**, and are part of the
[backup file](/guides/backup/).

`script export` also writes your shaders into the folder's `shaders` folder, so you can keep them in
git or edit them on a computer. `shader copy <name>` writes a copy of any shader there. After editing
a file, run `shader reload` to use it.

`shader remove <name>` deletes a shader of yours. A file of yours with the name of one that comes with
Kolahal is used instead of it, until you remove it.

| Command | What it does |
| --- | --- |
| `shader check <name>` | compiles the shader and says which uniforms it uses |
| `shader reload` | reads the `.agsl` files in the script folder again |
| `shader copy <name>` | writes a shader into the script folder's `shaders` folder, to edit it there |
| `shader remove <name>` | deletes your shader |

## Getting shaders from others

**Settings → Shaders → Add shader → Get more shaders** lists the shaders that people share in
[kolahal-scripts](https://github.com/mtwabpin/kolahal-scripts) on GitHub, beside its scripts. Each is
reviewed before it is added.

1. Tap a shader to see what it looks like, its license and its code.
2. Tap **Install**. The file must match the checksum in its list.
3. Tap **Use**. Installing doesn't turn it on.

It is then one of your shaders. When the list has a newer version, its row says **Update to …**. One you
edited is replaced only when you ask, and **Undo** puts your copy back.

The sources are the same as for scripts: [add your own](/scripting/overview/#your-own-scripts-repository)
with **Sources**, at the top of the page. Kolahal doesn't check the shaders of a source you add.

| Command | What it does |
| --- | --- |
| `shader get` | lists the shared shaders |
| `shader get <name>` | installs or updates one. `shader get <source>/<name>` picks the source |
| `shader update` | updates every shader you installed this way |
| `shader update <name>` | updates one |

The list is kept for an hour. `--refresh` reads it again, and `--replace` replaces a copy you edited.

To share a shader from [a repository of your own](/scripting/overview/#your-own-scripts-repository), put it
in `shaders/<name>/<name>.agsl` with a full [header](#the-header).

## Writing one

This one tints the screen with the theme's accent color:

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
- Look at a few pixels around each one, not hundreds. A shader that is too slow is turned off.
- If the screen shows your wallpaper ([`log.wallpaper`](/reference/config/#log)), the pixels behind the
  text can be transparent: check `c.a`.
- Moving pixels far from where they are makes a tap miss what it shows. Keep it under half a cell,
  except near the corners.

### The header

Comments at the top say what the shader is. All are optional:

| Comment | Meaning |
| --- | --- |
| `// @name Tint` | its name for people |
| `// @description …` | what it looks like. `shader` shows it |
| `// @animated` | it changes on its own, so it is drawn up to 30 times a second. Without it a shader is only drawn when the screen changes, so it uses no power while the screen is still |
| `// @author …` | who made it. A shared shader needs it, as it does `@version` and `@license` |
| `// @version 1.0` | its version |
| `// @license MIT` | its license |
| `// @tags glow, retro` | words that find it in Get more shaders |
| `// @requires 0.5.17` | the oldest Kolahal it works with |

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
| `touch` | `float2` | the last touch, in pixels. `-1, -1` before the first one |
| `touchAge` | `float` | seconds since that touch, only in an `@animated` shader. 1000 before the first touch |
| `dark` | `float` | 1 in a dark theme, 0 in a light one |
| `date` | `float4` | the phone's date and time: the year, the month (1 to 12), the day of the month, and the seconds since midnight, in local time. A shader that isn't `@animated` is drawn again once a minute when it declares it |
| `bg` `surface` `fg` `dim` `accent` `error` `warn` `good` `info` `link` | `half4` | the theme's colors. Declare them with `layout(color)`, so they match the screen's colors |

`half` and `float` are both fine for the numbers; `half2` and `float2` as well. The types have to match:
a `time` declared as `float2` is an error, and so is an array.
