---
title: Shaders
description: AGSL shaders over home and the full-screen screens, the ones that come with Kolahal, how to write and add your own, and what turns a slow one off.
---

A shader is a small program that the phone's graphics chip runs for every pixel. Kolahal can draw
one over home and the [full-screen screens](/reference/commands/#screens), such as `matrix` and
`screen clock`. Settings, the widgets page, menus and the keyboard stay as they are. Shaders are
written in AGSL, Android's version of GLSL.

Shaders need **Android 13 or newer**.

## Turning a shader on

| Command | What it does |
| --- | --- |
| `shader` | lists the shaders; a tap on one turns it on |
| `shader <name>` | turns it on |
| `shader off` | turns shaders off |
| `shader on` | turns the last one on again |

The setting behind them is [`screen.shader`](/reference/config/#screens). Three shaders come with
Kolahal:

| Name | What it looks like |
| --- | --- |
| `crt` | an old CRT monitor: curved glass, scanlines, a glow and a little color fringe |
| `bloom` | a soft halo around text, in its own color |
| `rain` | drops of the theme's accent color fall behind the text. It moves |

## If something goes wrong

A shader can make the screen hard to read. These always work:

- Type `shader off`. The keyboard and the prompt work, even when you can't see them.
- Hold two fingers still on the screen for a second.
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

There are two ways to add one:

- **The script folder.** Put the file in the folder you picked with `script dir`, at the top or in its
  `shaders` folder, and run [`script import`](/scripting/overview/#moving-scripts).
- **Import a file.** In the Scripts screen, **Add script** → **Import a file**, as you would a script.

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
| `bg` `surface` `fg` `dim` `accent` `error` `warn` `good` `info` `link` | `half4` | the theme's colors. Declare them with `layout(color)`, so they match the screen's colors |

`half` and `float` are both fine for the numbers; `half2` and `float2` as well. The types have to match:
a `time` declared as `float2` is an error, and so is an array.
