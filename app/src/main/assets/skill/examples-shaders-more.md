### Shader: a scene that follows the time of day

Still: it declares `date`, so it is drawn again each minute, and costs nothing between. It leaves text, icons and pictures as they are.

```glsl
// @name Daylight
// @description A glow at the top of the page that follows the sun: warm at dawn and dusk, faint at night
// @suits dark good, light fair, wallpaper good

uniform shader content;
uniform float2 resolution;
uniform float4 date;
uniform float dark;
uniform float wallpaper;
layout(color) uniform half4 bg;
layout(color) uniform half4 surface;
layout(color) uniform half4 info;
layout(color) uniform half4 warn;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
    float open = (1.0 - clamp(d * 5.0, 0.0, 1.0)) * (1.0 - step(0.999, here.a));
    if (open <= 0.0) return half4(here);
    float hour = date.w / 3600.0;
    float sun = 0.5 - 0.5 * cos(hour / 24.0 * 6.2831853);   // 0 at midnight, 1 at noon
    float a = (hour - 6.0) / 1.5;
    float b = (hour - 18.0) / 1.5;
    float edge = clamp(exp(-a * a) + exp(-b * b), 0.0, 1.0);  // 1 at 6:00 and 18:00
    float3 color = mix(float3(info.rgb), float3(warn.rgb), edge);
    float fade = 1.0 - smoothstep(0.0, 0.6, xy.y / resolution.y);
    float amount = (0.05 + 0.15 * max(sun, edge)) * fade;
    amount *= mix(0.75, 1.0, dark) * mix(1.0, 1.4, wallpaper) * open;
    return half4(here * (1.0 - amount) + float4(color * amount, amount));
}
```

### Shader: a touch that ripples

It moves only for 2 seconds after a touch (`@settles 2`), then stops until the next one.

```glsl
// @name Touch ring
// @description A ring spreads from where you touch, behind the text
// @suits dark good, light good, wallpaper good
// @animated
// @settles 2

uniform shader content;
uniform float2 touch;
uniform float touchAge;
uniform float2 cell;
uniform float dark;
layout(color) uniform half4 bg;
layout(color) uniform half4 surface;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    if (touch.x < 0.0 || touchAge >= 2.0) return half4(here);
    float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
    float open = (1.0 - clamp(d * 5.0, 0.0, 1.0)) * (1.0 - step(0.999, here.a));
    if (open <= 0.0) return half4(here);
    float r = (length(xy - touch) - touchAge * cell.y * 30.0) / cell.y;
    float amount = exp(-r * r) * (1.0 - touchAge / 2.0) * 0.3 * mix(0.75, 1.0, dark) * open;
    return half4(here * (1.0 - amount) + float4(float3(accent.rgb) * amount, amount));
}
```

### Shader: over the text too

`@text`: it changes text as well, so it can't be the wallpaper. A whole-number slider.

```glsl
// @name Lines
// @description Faint lines across the whole screen, text included, as on an old monitor
// @suits dark good, light fair, wallpaper fair
// @text
// @param gap 2..6 = 3 Pixels from line to line

uniform shader content;
uniform float gap;
uniform float dark;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float line = 1.0 - step(1.0, mod(xy.y, gap));   // 1 on the first row of pixels of each gap
    float shade = 1.0 - line * mix(0.12, 0.2, dark);
    return half4(here.rgb * shade, here.a);
}
```

### Shader: a parameter that follows a value

Saved as `night-shade`: `shader set night-shade shade 0.2` sets it by hand, and `shader follow night-shade shade night` follows again.

```glsl
// @name Night shade
// @description Dims the screen after dark, and lifts it at dawn
// @suits dark good, light good, wallpaper good
// @param shade 0.0..1.0 = 0.0 How dim @follow night

uniform shader content;
uniform float shade;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    return half4(here.rgb * (1.0 - shade * 0.35), here.a);
}
```

### Shader: a one-off that can play over other apps

Over another app `content` is clear, so it returns only its own light.

```glsl
// @name Edge glow
// @description A one-off: the screen's edge glows in the accent color and fades
// @suits dark good, light good, wallpaper good, apps good
// @once 2

uniform shader content;
uniform float2 resolution;
uniform float progress;
uniform float corner;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    if (progress <= 0.0 || progress >= 1.0) return half4(here);
    // How far the pixel is inside the screen's edge, with corners as round as the screen's.
    float2 mid = resolution * 0.5;
    float2 q = abs(xy - mid) - (mid - corner);
    float edge = corner - (length(max(q, 0.0)) + min(max(q.x, q.y), 0.0));
    float amount = exp(-edge / 12.0) * sin(3.14159265 * progress) * 0.6;
    if (amount <= 0.002) return half4(here);
    return half4(here * (1.0 - amount) + float4(float3(accent.rgb) * amount, amount));
}
```
