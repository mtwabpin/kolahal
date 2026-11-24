"""Renders a Kolahal AGSL shader off-screen, as the app would draw it.

usage: agsl.py <shader.agsl> <out.png> [--size WxH] [--hour 17.5] [--dark] [--wallpaper]
               [--time 12.0] [--set name=value ...] [--text] [--touch x,y,age] [--theme rose-pine]
               [--themes file.json] [--bench N] [--cell W,H] [--cutout L,T,R,B]

AGSL is close to GLSL: the types and `content.eval` are rewritten, and `main` is called for each
pixel with its place from the top left, as Android does. Colors are not converted: Android runs the
shader in the screen's color space.

--theme takes a built-in theme's dark or light palette (--dark) from the export of the app's themes,
site/src/data/themes.json (tools/export-themes.mjs), or from the file --themes names. The default
is the export's default theme. The date is 23 November 2026 and the hour 14:00 unless --hour says.
--text draws a few lines of text-colored bars as the page, to see what the shader keeps clear.
--bench N draws N frames and prints the time for one, as a rough cost per frame on this GPU.

Run it through tools/scenes/run.sh, which finds Python with moderngl, pillow and numpy.
"""
import json
import os
import re
import sys
import time as clock

import moderngl
import numpy as np
from PIL import Image

THEMES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'site', 'src', 'data', 'themes.json')

# The shader's color uniforms (ShaderMeta.COLORS) and the role of a theme each one takes
# (WallpaperWriter.colors).
UNIFORMS = dict(bg='background', surface='surface', fg='text', dim='muted', accent='accent', error='error',
                warn='warn', good='good', info='info', link='link')


def theme_colors(path, name, dark):
    """The theme's dark or light palette as the shader's color uniforms, each as 0xRRGGBB."""
    with open(path) as f:
        export = json.load(f)
    themes = export['themes']
    name = name or export['default']
    if name not in themes:
        sys.exit('no theme %s in %s: %s' % (name, path, ', '.join(themes)))
    palette = themes[name]['dark' if dark else 'light']
    return {u: int(palette[role].lstrip('#'), 16) for u, role in UNIFORMS.items()}


TYPES = [
    (r'\bfloat2x2\b', 'mat2'), (r'\bfloat3x3\b', 'mat3'), (r'\bfloat4x4\b', 'mat4'),
    (r'\bhalf2x2\b', 'mat2'), (r'\bhalf3x3\b', 'mat3'), (r'\bhalf4x4\b', 'mat4'),
    (r'\bfloat2\b', 'vec2'), (r'\bfloat3\b', 'vec3'), (r'\bfloat4\b', 'vec4'),
    (r'\bhalf2\b', 'vec2'), (r'\bhalf3\b', 'vec3'), (r'\bhalf4\b', 'vec4'), (r'\bhalf\b', 'float'),
    (r'\bint2\b', 'ivec2'), (r'\bint3\b', 'ivec3'), (r'\bint4\b', 'ivec4'),
]
# Names that are fine in AGSL and reserved in GLSL.
RESERVED = ['sample', 'input', 'output', 'filter', 'active', 'common', 'partition', 'smooth', 'flat', 'patch',
            'texture', 'packed', 'this', 'using', 'long', 'short', 'fixed', 'unsigned', 'cast', 'namespace']


def to_glsl(src):
    body = re.sub(r'layout\s*\(\s*color\s*\)\s*', '', src)
    shaders = re.findall(r'\buniform\s+shader\s+(\w+)\s*;', body)
    body = re.sub(r'\buniform\s+shader\s+(\w+)\s*;', '', body)
    for name in shaders:
        body = re.sub(r'\b%s\s*\.\s*eval\s*\(' % name, 'content_eval(', body)
    for pat, rep in TYPES:
        body = re.sub(pat, rep, body)
    for word in RESERVED:
        body = re.sub(r'\b%s\b(?!\s*\()' % word, word + '_', body) if re.search(r'\b(float|vec\d|int|bool)\s+%s\b' % word, body) else body
    body = re.sub(r'\b(vec4)\s+main\s*\(', r'\1 agsl_main(', body)
    head = '''#version 330 core
uniform sampler2D content_tex;
out vec4 frag_color;
uniform vec2 content_size;
vec4 content_eval(vec2 p) { return texture(content_tex, p / content_size); }
#line 1
'''
    tail = '''
void main() {
    frag_color = agsl_main(vec2(gl_FragCoord.x, content_size.y - gl_FragCoord.y));
}
'''
    return head + body + tail


def params(src):
    out = {}
    for m in re.finditer(r'^\s*//\s*@param\s+(\w+)\s+(\S+)\s*=\s*(\S+)', src, re.M):
        name, span, default = m.groups()
        if '|' in span:
            out[name] = float(span.split('|').index(default))
        else:
            out[name] = float(default)
    return out


def rgba(c, a=1.0):
    return ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0, a


def page(w, h, colors, text):
    img = np.zeros((h, w, 4), dtype=np.uint8)
    img[:, :] = [int(v * 255 + 0.5) for v in rgba(colors['bg'])]
    if text:
        fg = [int(v * 255 + 0.5) for v in rgba(colors['fg'])]
        dim = [int(v * 255 + 0.5) for v in rgba(colors['dim'])]
        row = max(8, h // 52)
        y = h - row * 4
        rng = np.random.default_rng(7)
        n = 0
        while y > h * 0.25:
            length = int(w * (0.25 + 0.6 * rng.random()))
            x = int(w * 0.04)
            while x < int(w * 0.04) + length:
                word = int(row * (1.5 + 4 * rng.random()))
                img[y:y + int(row * 0.55), x:min(x + word, w - 8)] = fg if n % 5 else dim
                x += word + row // 2
            y -= int(row * 1.6)
            n += 1
    return img


def main(argv):
    src_path, out_path = argv[0], argv[1]
    opt = dict(size='1080x2400', hour=None, dark=False, wallpaper=False, time=12.0, sets={}, text=False,
               touch=None, theme=None, themes=THEMES_FILE, bench=0, tilt='0,0')
    i = 2
    while i < len(argv):
        a = argv[i]
        if a == '--dark': opt['dark'] = True
        elif a == '--wallpaper': opt['wallpaper'] = True
        elif a == '--text': opt['text'] = True
        elif a == '--set':
            i += 1; k, v = argv[i].split('='); opt['sets'][k] = float(v)
        else:
            i += 1; opt[a[2:]] = argv[i]
        i += 1
    w, h = (int(v) for v in opt['size'].split('x'))
    src = open(src_path).read()
    glsl = to_glsl(src)
    ctx = moderngl.create_standalone_context(backend='egl')
    try:
        prog = ctx.program(
            vertex_shader='#version 330 core\nin vec2 at; void main() { gl_Position = vec4(at, 0.0, 1.0); }',
            fragment_shader=glsl)
    except Exception as e:
        print(str(e)[:3000]); sys.exit(1)
    dark = 1 if opt['dark'] else 0
    colors = theme_colors(opt['themes'], opt['theme'], dark)
    vals = dict(resolution=(float(w), float(h)), content_size=(float(w), float(h)), time=float(opt['time']),
                dark=float(dark), wallpaper=1.0 if opt['wallpaper'] else 0.0,
                touch=(-1.0, -1.0), touchAge=1e6, pressed=0.0, battery=0.8, charging=0.0, progress=0.5,
                tilt=tuple(float(v) for v in opt['tilt'].split(',')),
                cell=(w / 48.0, w / 48.0 * 2.13), grid=(48.0, float(int(h / (w / 48.0 * 2.13)))), origin=(0.0, 0.0))
    if opt.get('cell'):
        vals['cell'] = tuple(float(v) for v in opt['cell'].split(','))
    if opt.get('cutout'):
        vals['cutout'] = tuple(float(v) for v in opt['cutout'].split(','))
    hour = 14.0 if opt['hour'] is None else float(opt['hour'])
    vals['date'] = (2026.0, 11.0, 23.0, hour * 3600.0)
    if opt['touch']:
        x, y, age = (float(v) for v in opt['touch'].split(','))
        vals['touch'] = (x, y); vals['touchAge'] = age
    for k, v in colors.items():
        vals[k] = rgba(v)
    vals.update(params(src))
    vals.update(opt['sets'])
    for name, value in vals.items():
        if name in prog:
            prog[name].value = value
    tex = ctx.texture((w, h), 4, page(w, h, colors, opt['text'])[::-1].tobytes())
    tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
    tex.use(0)
    if 'content_tex' in prog:
        prog['content_tex'].value = 0
    quad = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype='f4').tobytes())
    vao = ctx.vertex_array(prog, [(quad, '2f', 'at')])
    fbo = ctx.framebuffer(color_attachments=[ctx.texture((w, h), 4)])
    fbo.use()
    vao.render(moderngl.TRIANGLE_STRIP)
    data = fbo.read(components=4)
    Image.frombytes('RGBA', (w, h), data).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB').save(out_path)
    n = int(opt['bench'])
    if n:
        ctx.finish()
        t = clock.perf_counter()
        for k in range(n):
            if 'time' in prog:
                prog['time'].value = float(opt['time']) + k / 30.0
            vao.render(moderngl.TRIANGLE_STRIP)
        ctx.finish()
        print('%s  %dx%d  %.3f ms a frame' % (src_path.split('/')[-1], w, h, (clock.perf_counter() - t) * 1000 / n))


if __name__ == '__main__':
    main(sys.argv[1:])
