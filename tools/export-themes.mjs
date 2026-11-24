#!/usr/bin/env node
// Writes site/src/data/themes.json: every built-in theme of the app, its dark and its light palette
// by role, read from the app's sources. Every picture drawn off the phone takes its colors from that
// file (docs/pictures.org); ThemeExportTest fails when it no longer matches Palettes.
//
//   node tools/export-themes.mjs           writes the file
//   node tools/export-themes.mjs --check   exits with 1 when the file is not what it would write
//
// Where each part comes from:
// - the themes and their order: Keys.BUILT_IN_THEMES (data/Keys.kt), without the variations of
//   material-you, which the app works out from material-you's colors;
// - the roles and their order: CustomThemes.ROLES (data/Themes.kt), the names a theme file uses;
// - each theme's two palettes: Palettes.BUILT_IN and the palettes it names (ui/theme/Theme.kt), each
//   field under its role as Palettes.colorsOf names it;
// - material-you: Material's standard colors, which the app shows on Android 9 to 11 and the
//   website shows for it. They come from the Compose Material 3 library, not from Kolahal's sources,
//   so they are written here (MATERIAL below), with good and warn from ui/theme/MaterialYouPalettes.kt.
// Needs Node.js only.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = 'app/src/main/java/com/mtwabpin/kolahal';
const read = (path) => readFileSync(join(root, path), 'utf8');
const OUT = 'site/src/data/themes.json';

/** The quoted strings of `name = listOf(...)` in [text]. */
function list(text, name) {
	const m = text.match(new RegExp(`val ${name} = listOf\\(([^)]*)\\)`));
	if (!m) throw new Error(`no ${name}`);
	return [...m[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
}

const keys = read(`${src}/data/Keys.kt`);
const themesKt = read(`${src}/data/Themes.kt`);
const themeKt = read(`${src}/ui/theme/Theme.kt`);
const materialKt = read(`${src}/ui/theme/MaterialYouPalettes.kt`);

// material-you (MaterialYou.NAME) comes after the others, as in Keys.BUILT_IN_THEMES.
const materialYou = themesKt.match(/const val NAME = "([a-z-]+)"/)[1];
const names = [...list(keys, 'BUILT_IN_THEMES'), materialYou];
const roles = list(themesKt, 'ROLES');

// Palettes: `val name = Palette(field = Color(0xFFRRGGBB), ...)`.
const palettes = {};
for (const m of themeKt.matchAll(/val (\w+) = Palette\(([\s\S]*?)\n {4}\)/g)) {
	palettes[m[1]] = Object.fromEntries([...m[2].matchAll(/(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)/g)].map((x) => [x[1], `#${x[2].toUpperCase()}`]));
}
// BUILT_IN: `"name" to (dark to light)`.
const pairs = Object.fromEntries([...themeKt.matchAll(/"([a-z-]+)" to \((\w+) to (\w+)\)/g)].map((m) => [m[1], [m[2], m[3]]]));
// colorsOf: `"role" to p.field`.
const fieldOf = Object.fromEntries([...themeKt.matchAll(/"(\w+)" to p\.(\w+)/g)].map((m) => [m[1], m[2]]));
for (const r of roles) if (!fieldOf[r]) throw new Error(`Palettes.colorsOf has no role ${r}`);

const byRole = (palette, what) => Object.fromEntries(roles.map((r) => {
	const c = palette[fieldOf[r]];
	if (!c) throw new Error(`${what} has no ${fieldOf[r]}`);
	return [r, c];
}));

// Material's standard schemes (darkColorScheme() and lightColorScheme() of Compose Material 3), the
// roles paletteFrom reads, and the plain material-you palette it makes of them.
const MATERIAL = {
	dark: { surface: '#141218', surfaceContainer: '#211F26', onSurface: '#E6E0E9', onSurfaceVariant: '#CAC4D0', outline: '#938F99', primary: '#D0BCFF', secondary: '#CCC2DC', tertiary: '#EFB8C8', error: '#F2B8B5' },
	light: { surface: '#FEF7FF', surfaceContainer: '#F3EDF7', onSurface: '#1D1B20', onSurfaceVariant: '#49454F', outline: '#79747E', primary: '#6750A4', secondary: '#625B71', tertiary: '#7D5260', error: '#B3261E' },
};
function fixed(name) {
	const m = materialKt.match(new RegExp(`val ${name} = Color\\(0xFF([0-9A-Fa-f]{6})\\) to Color\\(0xFF([0-9A-Fa-f]{6})\\)`));
	if (!m) throw new Error(`no ${name} in MaterialYouPalettes.kt`);
	return { dark: `#${m[1].toUpperCase()}`, light: `#${m[2].toUpperCase()}` };
}
const GOOD = fixed('GOOD');
const WARN = fixed('WARN');
const material = (mode) => {
	const s = MATERIAL[mode];
	return byRole({
		bg: s.surface, surface: s.surfaceContainer, fg: s.onSurface, dim: s.outline, prompt: s.primary,
		error: s.error, info: s.secondary, link: s.tertiary, script: s.onSurfaceVariant,
		personal: s.primary, work: s.tertiary, private: s.secondary, good: GOOD[mode], warn: WARN[mode],
	}, `material-you ${mode}`);
};

const themes = {};
for (const name of names) {
	if (name === materialYou) {
		themes[name] = { dark: material('dark'), light: material('light') };
		continue;
	}
	const pair = pairs[name];
	if (!pair) throw new Error(`Palettes has no built-in theme ${name}`);
	themes[name] = { dark: byRole(palettes[pair[0]], pair[0]), light: byRole(palettes[pair[1]], pair[1]) };
}

const out = {
	about: 'The app\'s built-in themes, dark and light, by role (ui/theme/Theme.kt, Palettes). Made by tools/export-themes.mjs; ThemeExportTest checks it. material-you is Material\'s standard colors, as on Android 9 to 11; its variations are left out.',
	default: list(keys, 'BUILT_IN_THEMES')[0],
	roles,
	themes,
};
const text = JSON.stringify(out, null, '\t') + '\n';

if (process.argv.includes('--check')) {
	let now = '';
	try { now = read(OUT); } catch { /* missing */ }
	if (now !== text) {
		console.error(`${OUT} is not up to date: run node tools/export-themes.mjs`);
		process.exit(1);
	}
	console.log(`${OUT} is up to date`);
} else {
	writeFileSync(join(root, OUT), text);
	console.log(`${OUT}: ${Object.keys(themes).join(', ')}`);
}
