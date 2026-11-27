## Shaders

A shader is an AGSL program run for every pixel of home (or of the live wallpaper). It gets what is on the screen as `content` and returns each pixel's color. It has no other input.

### The smallest shader

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

`xy` is the pixel, in pixels from the top left corner. `content.eval(xy)` gives the color there. Colors are **premultiplied**: red, green and blue are already multiplied by alpha, so mix a color in as `color * alpha`. Over a wallpaper the page can be transparent: check `a`.

### Shader header

Comments at the top, all optional:

| Line | What it does |
| --- | --- |
| `// @name Tint` | Its name for people (`@title` also works). |
| `// @description …` | What it looks like (`@desc` also works). |
| `// @param amount 0.0..2.0 = 1.0 Amount` | Something people change: see "Parameters". |
| `// @suits dark good, light fair, wallpaper poor` | How it looks over a dark theme, a light theme and a wallpaper behind the log: `good`, `fair` or `poor`. A one-off adds `apps good` to play over other apps, on a clear `content`. |
| `// @text` | It works on text, so it can't be a live wallpaper. |
| `// @animated` | It changes by itself: drawn up to 30 times a second, and `time` moves. Without it, it is drawn only when the screen changes, and costs nothing while it is still. |
| `// @fps 10` | An animated shader's frames a second, 1 to 30. Use it for slow scenes. |
| `// @settles 4` | Seconds a touch changes the picture, 0.5 to 60 (60). A shader whose only clock is `touchAge` stops after that until the next touch. |
| `// @once 3` | A one-off: plays for this many seconds (1 to 10) over the shader that is on, then goes. Start and end with nothing. |
| `// @author …` | Who made it. |
| `// @version 1.0` | Its version. |
| `// @license MIT` | Its license. |
| `// @tags glow, rain` | Search words. |
| `// @requires 0.5.17` | The oldest Kolahal it works with. |

### Uniforms

Declare only those you use; Kolahal sets them. A uniform of another name stays 0. Types must match (`half` for `float` and `half2` for `float2` are fine); no arrays.

| Uniform | Type | What it is |
| --- | --- | --- |
| `content` | `shader` | what is on the screen. Required, and the only shader input |
| `resolution` | `float2` | the surface's size in pixels |
| `cell` | `float2` | one character cell's size in pixels, to size things like the text |
| `grid` | `float2` | columns and rows of cells that fit |
| `origin` | `float2` | where the first column starts, in pixels from the left, and 0 |
| `time` | `float` | Kolahal's clock in seconds, from 0 when turned on or saved; moves only with `@animated` while home is in front, and pauses to save power. Not the time of day (`date`). Wrap it: `mod(time, 3141.59265)` |
| `progress` | `float` | how far a one-off has come, 0 to 1 |
| `touch` | `float2` | the last touch, in pixels; `-1, -1` before the first |
| `touchAge` | `float` | seconds since that touch, only with `@animated`; 1000 before the first |
| `pressed` | `float` | 1 while a finger is down, else 0 |
| `tilt` | `float2` | how far the phone leans, about -1 to 1 (x: right side lower, y: bottom edge lower); `0, 0` when level. An offset for what is behind text, never for text |
| `battery` | `float` | how full the battery is, 0 to 1 |
| `charging` | `float` | 1 while plugged in, else 0 |
| `corner` | `float` | the radius of the screen's rounded corners in pixels; 0 when square or in a preview |
| `cutout` | `float4` | the camera cutout at the top: left, top, right, bottom in pixels; all 0 without one |
| `wallpaper` | `float` | 1 as the live wallpaper (then `content` is the theme's background alone), else 0 |
| `dark` | `float` | 1 in a dark theme, 0 in a light one |
| `date` | `float4` | year, month (1 to 12), day of the month, seconds since midnight, local time. A still shader that declares it is drawn again each minute |
| `bg` `surface` `fg` `dim` `accent` `error` `warn` `good` `info` `link` | `half4` | the theme's colors; declare each as `layout(color) uniform half4 accent;` |

A still shader (no `@animated`) is drawn again when `pressed`, `battery`, `charging`, `tilt` or a parameter changes.

### Parameters

```text
// @param amount 0.0..2.0 = 1.0 Amount of snow
// @param shape round|star = round Shape of the flakes
uniform float amount;
uniform float shape;
```

- `@param <name> <min>..<max> = <default> <title>`: a slider; with no decimal point anywhere in it (`1..8 = 3`), whole numbers. The default must be in the range.
- `@param <name> a|b|c = a <title>`: 2 to 8 words (letters, digits, `-`), shown as buttons; the uniform gets the word's place, 0 for the first.
- Each needs `uniform float <name>;`. Up to 8 parameters; a name can't be a uniform Kolahal sets.
- People change them in the shader's details or with `shader set <name> <param> <value>`.
- **Following a value**: end the line with `@follow <value>` (or a formula such as `@follow battery / 20`), and the parameter follows it until the user picks another. A number from Kolahal is spread over the range (a full battery is `max`); a formula's result is the value itself, kept in the range.

| Value | What it is |
| --- | --- |
| `battery` | 0 to 100 |
| `charging` | 0, or 1 while plugged in |
| `day` | the time of day in hours, 0 to 24 |
| `night` | 0 by day, 1 at night, with a dusk and a dawn |
| `unlocks` | today's unlocks (100 is a parameter's most) |
| `unlocks.usual` | today's unlocks against a usual day: 0 none, 1 as many, 2 at most |
| `screen` | today's screen time in minutes (480 is the most) |
| `screen.usual` | today's screen time against a usual day |
| `away` | minutes the phone was locked before the last unlock (240 is the most) |
| `unlocks.d1` … `unlocks.d7` | each of the last 7 days' unlocks, `d7` today, 0 to 1 (1: the busiest day) |
| `screen.d1` … `screen.d7` | the same for screen time |
| `screen.h1` … `screen.h8` | each three hours of today from midnight, 0 to 1: how much the screen was on |
| `<script>.<name>` | a value a script gives with `shader.give` (`weather.rain`) |

Screen time and `away` need usage access (`allow usage`).

### Writing AGSL that compiles

Write it the way Kolahal's own shaders are written; they use only these forms:

- The entry point is exactly `half4 main(float2 xy)`. Helper functions and `const` values come before it.
- Every number in float math has a decimal point: `2.0`, `0.5`, never `2`.
- Convert types explicitly: `float4 here = float4(content.eval(xy));`, `half4(here)`, `float3(accent.rgb)`, `float(i)`.
- Loops are `for (int i = 0; i < 5; i++)` with a fixed number as the bound; `break` may leave early. No `while`, `switch`, arrays or `%`; use `mod()` for remainders.
- Built-ins used: `mix`, `smoothstep`, `clamp`, `min`, `max`, `abs`, `fract`, `floor`, `mod`, `length`, `dot`, `normalize`, `sin`, `cos`, `atan`, `exp`, `exp2`, `pow`, `sqrt`, `step`. For noise, Kolahal's shaders use this hash: `float hash(float2 p) { float3 q = fract(float3(p.xyx) * 0.1031); q += dot(q, q.yzx + 33.33); return fract((q.x + q.y) * q.z); }`.
- Before the compiler, Kolahal refuses a file without `uniform shader content;`, another `uniform shader`, a known uniform of the wrong type or as an array, a `@param` without its `uniform float`, more than 8 parameters, and a file over 64 KB.
- A compile error reads `line 12: unknown identifier 'x'`: fix that line.

### Behind the text, and cost

Most shaders draw only where the page shows; the bare page is a step under opaque, so text, icons and pictures are what is unlike `bg` and `surface` or fully opaque:

```text
float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
float open = (1.0 - clamp(d * 5.0, 0.0, 1.0)) * (1.0 - step(0.999, here.a));   // 1 on the page
```

- Use the theme's color uniforms, never fixed colors, so it fits every theme; take less on a light theme (`mix(0.75, 1.0, dark)`), and check `wallpaper`.
- Return early for pixels you don't change. Look at a few pixels around each one at most, never hundreds.
- An `@animated` shader works out every pixel every frame: use `@fps 10` for slow motion, and `@settles` with `touchAge` for touch effects instead of `time`.
- Don't move pixels far from where they are (under half a cell): a tap would miss what it shows.
