#!/usr/bin/env node
// Copies a folder of picture sources, with a theme's colors filled in, for a script that renders them
// (tools/discord-graphics.sh).
//
//   node tools/fill-theme.mjs <theme> <from> <to>
//
// In every .svg, .html and .css file, a placeholder becomes a color of the theme in
// site/src/data/themes.json (tools/export-themes.mjs):
//   {{dark.accent}}           the role's color in the dark version, #RRGGBB (light.… the light one)
//   {{light.background:rgb}}  the same as three numbers, "250 244 237", for rgb(… / 0.72)
//   {{extra.sky}}             a color the app has no role for (EXTRA below)
// Other files are copied as they are. A placeholder that names no color stops the copy.
// Needs Node.js only.
import { copyFileSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const [name, from, to] = process.argv.slice(2);
if (!to) {
	console.error('usage: node tools/fill-theme.mjs <theme> <from> <to>');
	process.exit(2);
}
const { themes } = JSON.parse(readFileSync(join(root, 'site/src/data/themes.json'), 'utf8'));
const theme = themes[name];
if (!theme) {
	console.error(`No theme ${name} in site/src/data/themes.json: ${Object.keys(themes).join(', ')}`);
	process.exit(1);
}

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const hex = (c) => '#' + c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();
/** [a] moved toward [b] by [t], from 0 (a) to 1 (b). */
const mix = (a, b, t) => hex(rgb(a).map((v, i) => v + (rgb(b)[i] - v) * t));

// Colors the pictures use that the app has no role for. rose-pine keeps the colors they were drawn
// with, from Rosé Pine's wider palette; another theme gets one worked out from its own roles.
const EXTRA = {
	// The sky behind the server's icon and the logo emoji: Rosé Pine Moon's overlay; elsewhere the
	// dark surface a tenth of the way to the text.
	sky: name === 'rose-pine' ? '#393552' : mix(theme.dark.surface, theme.dark.text, 0.1),
	// The outline of the widget emoji's card: Rosé Pine's highlight high; elsewhere the dark surface
	// two fifths of the way to the muted text.
	rule: name === 'rose-pine' ? '#524F67' : mix(theme.dark.surface, theme.dark.muted, 0.4),
};

function fill(text, file) {
	return text.replace(/\{\{(\w+)\.([\w-]+)(:rgb)?\}\}/g, (all, group, role, asRgb) => {
		const color = group === 'extra' ? EXTRA[role] : theme[group]?.[role];
		if (!color) throw new Error(`${file}: ${all} names no color`);
		return asRgb ? rgb(color).join(' ') : color;
	});
}

function copy(src, dst) {
	if (statSync(src).isDirectory()) {
		mkdirSync(dst, { recursive: true });
		for (const entry of readdirSync(src)) copy(join(src, entry), join(dst, entry));
	} else if (/\.(svg|html|css)$/.test(src)) {
		writeFileSync(dst, fill(readFileSync(src, 'utf8'), src));
	} else {
		copyFileSync(src, dst);
	}
}
copy(from, to);
