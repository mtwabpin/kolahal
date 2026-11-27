### Where a shader runs

- **Over home**: `shader <name>`. It covers the whole home page in both home styles, cards and background included, and the full-screen screens (`screen matrix`, a script's screen, the screensaver). Without `@text`, home's app selector is drawn over its picture; with `@text` it covers the selector too. A shader that is neither `@text` nor `@once` is a scene: while it is on, the phone's wallpaper doesn't show behind home, so its `wallpaper` rating in `@suits` counts only for `@text` shaders and one-offs. `shader <name>` refuses a one-off.
- **The live wallpaper**: `wallpaper shader <name>`. Refused for a one-off and for a `@text` shader. `content` is one flat color, the theme's `bg`, with no text. A compile error there shows the plain background and logs nothing: try a shader over home first.
- **A one-off**: `shader play <name> [seconds]`, a script's `shader.play`, the Shaders screen, or a notification with words set by `shader words <name> Alex, picnic`. One plays at a time. It is drawn over the shader that is on, so its `content` already holds that shader's picture. It draws at the display's rate, and `@fps` and `@settles` don't count for it. It doesn't play in battery saver, with Remove animations on, on a hot phone, or under 15 % battery.
- **Over other apps**: a one-off rated `@suits apps good` (or `fair`) plays over the app in front when a notification's words start it, if the user turned that on and allowed Display over other apps, in a build that can. There `content` is clear (all 0): return only what you draw, premultiplied. `touch`, `pressed` and `tilt` stay as before a touch. `shader play` and `shader.play` play over home only.
- Not the screensaver: it is one of Kolahal's screens, and the shader over home draws over it.

`shader save template-tint` makes a shader to start from; the other templates are `template-scene`, `template-moving`, `template-touch`, `template-value` and `template-once`. `shader check <name>` says whether it compiles, the uniforms Kolahal sets, its parameters, how often it draws, and any uniform that stays 0.

### How Kolahal sets the uniforms

- `time` pauses in battery saver, with Remove animations on, on a hot phone, under 15 % battery and after 60 s without a touch (these two not while charging).
- `progress` is 0 in anything but a one-off.
- `touchAge` counts up only with `@animated`; a still shader sees 0 after a touch.
- `cell`, `grid` and `origin` match the text only in the TTY style. In the GUI style, as the wallpaper and on screens, they come from the TTY font's cell, with the grid centered: use them for sizes, not to line things up with text.
- `dark` over home is 1 when the theme's background is darker than its text. `accent` is the prompt's color.
- `tilt` is smoothed, and "level" follows how the phone is held over about 6 seconds: a lean held still goes back to 0. It is 0 while the clock is stopped.
- `corner` and `cutout` are 0 on anything smaller than the screen, such as a preview.
- A still shader is drawn again on a touch, too.

### Parameters, more

- A name is a letter or `_`, then up to 39 letters, digits or `_`. Without a title, the name is the title, `_` as spaces.
- A range needs its minimum under its maximum.
- A choice word starts with a letter or digit and has at most 20 characters; no two are the same in any case; the default is one of them written exactly.
- A `@follow` that can't be read is left out without a word. A formula can say `value` for the value it follows; its result is whole for a whole-number slider. A followed parameter glides to a new value over 2 seconds.

### What Kolahal refuses, and what it says

Before the compiler, `problems` stop a shader. The editor shows **Saved · can't be used:** and the problem, with its line; home logs "Shader <name> can't be used" once. The texts:

- `the file is larger than 64 KB`
- `it needs 'uniform shader content;', the surface it draws over`
- `'img': the only shader input is content` (another `uniform shader`)
- `'resolution' must be a float2, not float` (a known uniform of another type, or an array)
- `it has 9 parameters, and can have 8`
- `'@param …' can't be read: write '@param amount 0.0..2.0 = 1.0 Title' or '@param shape a|b = a Title'`
- `'time' can't be a parameter: Kolahal sets it`
- `the parameter 'amount' is there twice`
- `the parameter 'amount' needs 'uniform float amount;'`

These lines are left out, and the shader still works: `@fps` that isn't a whole number from 1 to 30, `@settles` that isn't 0.5 to 60, `@still` that isn't 0 to 120. A `@once` that isn't a number makes no one-off; one outside 1 to 10 is brought into that range. `@once` makes it animated by itself. Words in `@suits` it can't read are left out.

Kolahal turns a shader off at the next start if Kolahal stopped or hung while it was on in its first 10 seconds, and after three such times turns `screen.shader` off. Two fingers held still for a second turn a shader off.

### Mistakes to avoid

- Writing GLSL or Shadertoy code: `void main()`, `gl_FragCoord`, `fragColor`, `iTime`, `iResolution`, `texture(...)`, `sampler2D`. The entry point is `half4 main(float2 xy)`, the input is `content.eval(xy)`, and the uniforms are the ones in the table: any other name stays 0.
- Forms other than those under "Writing AGSL that compiles", above all a whole number in float math (`2` for `2.0`) and `%` for `mod()`.
- Using `time` without `@animated` (it stands still), or for the time of day (use `date`).
- A one-off that doesn't give back `here` when `progress` is 0 and 1: it jumps on and off.
- Forgetting premultiplied alpha: mix a color in as `color * amount` with `amount` as its alpha, as the examples do; returning alpha 1 makes a wallpaper or the page behind it opaque.
- Fixed colors such as `half4(1.0, 0.0, 0.0, 1.0)` instead of the theme's color uniforms, or the same strength on a light theme as on a dark one.
- Painting over text, icons and pictures in a scene: use the whole test under "Behind the text".
- Moving pixels far from where they are, or looking at hundreds of pixels around each one.
- Declaring `@text` for a scene that draws only on the page: it then can't be the wallpaper.
