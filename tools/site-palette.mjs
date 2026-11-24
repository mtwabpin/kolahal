#!/usr/bin/env node
// Prints the website's copy of the app's palette, the --k-* block at the end of
// site/src/styles/site.css, for a theme of site/src/data/themes.json (tools/export-themes.mjs).
//
//   node tools/site-palette.mjs [theme]           prints the block (the export's default theme without one)
//   node tools/site-palette.mjs [theme] --write   puts it in site.css in place of the one there
//
// The theme cards (site/src/components/ThemeCards.astro) read every theme from the same file.
// Needs Node.js only.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const CSS = join(root, 'site/src/styles/site.css');
const HEAD = "/* ---- The app's palette (--k-*) ------------------------------------------------------------ */";

const args = process.argv.slice(2);
const write = args.includes('--write');
const export_ = JSON.parse(readFileSync(join(root, 'site/src/data/themes.json'), 'utf8'));
const name = args.find((a) => !a.startsWith('--')) ?? export_.default;
const theme = export_.themes[name];
if (!theme) {
	console.error(`No theme ${name} in site/src/data/themes.json: ${Object.keys(export_.themes).join(', ')}`);
	process.exit(1);
}

// Each --k-* color and the role it takes. Personal apps are drawn in the accent, as in the app.
const VARS = [
	['bg', 'background'], ['surface', 'surface'], ['fg', 'text'], ['dim', 'muted'], ['prompt', 'accent'],
	['error', 'error'], ['info', 'info'], ['link', 'link'], ['script', 'script'], ['work', 'work'],
	['private', 'private'], ['good', 'good'], ['warn', 'warn'],
];
const colors = (palette) => VARS.map(([v, role]) => `\t--k-${v}: ${palette[role].toLowerCase()};`).join('\n');

const block = `${HEAD}

/*
 * Kolahal's ${name} theme in its dark and light versions (ui/theme/Theme.kt, Palettes), from
 * src/data/themes.json by tools/site-palette.mjs: the theme the site's captures of the app are in.
 * Only pictures of the app read these: the drawn phone, the gesture map, the selector's drawings and
 * the like. Nothing in the site's own chrome uses them, and the theme cards have each theme's own
 * colors, from the same file.
 */
:root {
${colors(theme.dark)}
	/* How strong a tag's chip or a pill's shade is (RichText.drawChips: 24 % dark, 14 % light). */
	--k-chip: 24%;
	--k-rule: color-mix(in srgb, var(--k-dim) 25%, transparent);
	--k-gutter: color-mix(in srgb, var(--k-prompt) 45%, transparent);
}

/* The light version. */
:root[data-theme='light'] {
${colors(theme.light)}
	--k-chip: 14%;
}
`;

if (write) {
	const css = readFileSync(CSS, 'utf8');
	const at = css.indexOf(HEAD);
	if (at < 0) {
		console.error(`site.css has no line ${HEAD}`);
		process.exit(1);
	}
	writeFileSync(CSS, css.slice(0, at) + block);
	console.log(`site/src/styles/site.css: the app's palette is ${name}`);
} else {
	process.stdout.write(block);
}
