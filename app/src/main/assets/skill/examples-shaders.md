### Shader: still, with a parameter

```glsl
// @name Vignette
// @description Darker corners, the middle as it is
// @suits dark good, light fair, wallpaper good
// @param strength 0.0..1.0 = 0.4 Strength

uniform shader content;
uniform float2 resolution;
uniform float strength;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float far = length(xy / resolution - 0.5) * 1.4;
    float shade = 1.0 - strength * smoothstep(0.3, 1.0, far);
    return half4(here.rgb * shade, here.a);
}
```

### Shader: moving, behind the text

```glsl
// @name Mist
// @description Slow bands of mist drift across the lower half
// @suits dark good, light good, wallpaper good
// @animated
// @fps 10

uniform shader content;
uniform float2 resolution;
uniform float time;
uniform float dark;
layout(color) uniform half4 bg;
layout(color) uniform half4 surface;
layout(color) uniform half4 info;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float2 p = xy / resolution;
    if (p.y < 0.5) return half4(here);
    float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
    float open = (1.0 - clamp(d * 5.0, 0.0, 1.0)) * (1.0 - step(0.999, here.a));
    if (open <= 0.0) return half4(here);
    float t = mod(time, 3141.59265);
    float band = 0.5 + 0.5 * sin(p.x * 6.0 + t * 0.3 + 2.0 * sin(p.y * 9.0 - t * 0.2));
    float amount = band * smoothstep(0.5, 1.0, p.y) * 0.18 * mix(0.75, 1.0, dark) * open;
    return half4(here * (1.0 - amount) + float4(float3(info.rgb) * amount, amount));
}
```

### Shader: a one-off

```glsl
// @name Flash
// @description A one-off: the accent color washes over the page once and fades
// @suits dark good, light good, wallpaper good
// @once 2

uniform shader content;
uniform float progress;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    if (progress <= 0.0 || progress >= 1.0) return half4(here);
    float amount = sin(3.14159265 * progress) * 0.25;
    return half4(here * (1.0 - amount) + float4(float3(accent.rgb) * amount, amount));
}
```
