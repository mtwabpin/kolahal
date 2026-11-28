// What a shader file says about itself, and what Kolahal says of it: the app's ShaderMeta, ShaderParam and
// ShaderClock.Pace, and the lines of `shader check` (ShaderCommands.checkLines), written again in JavaScript.
// The Shader preview page (components/ShaderPreview.astro) and scripts/check-shaders.mjs use it.
// It follows app/src/main/java/com/mtwabpin/kolahal/shader/: change it with them.

/** The shader's input: the surface it is drawn over. */
export const INPUT = 'content';
/** The alpha of the page a shader reads as its input: a step under opaque (ShaderMeta.PAGE_ALPHA). */
export const PAGE_ALPHA = 254 / 255;
/** The most a file may have, in bytes. */
export const MAX_BYTES = 64 * 1024;
/** Frames a second at most, and without `@fps` (ShaderClock.FPS). */
export const FPS = 30;
/** Without a touch for this long a shader stands still (ShaderClock.IDLE_MS); the most `@settles` says. */
export const IDLE_MS = 60_000;
/** The longest step of the clock from one frame to the next, unless the frames are further apart (ShaderClock.PAUSE_MS). */
export const PAUSE_MS = 250;
/** `touchAge` before any touch (ShaderFrame.NEVER). */
export const NEVER = 1000;
/** The shortest and longest one-off, in seconds (ShaderPlay). */
const ONCE_MIN = 1;
const ONCE_MAX = 10;
const MIN_SETTLES = 0.5;
const MAX_SETTLES = IDLE_MS / 1000;
const MAX_STILL = 120;
/** The most parameters a shader can have, and the most words one can choose from. */
export const MAX_PARAMS = 8;
const MAX_CHOICES = 8;

const FLOATS = {
	float: 1, half: 1,
	float2: 2, half2: 2, vec2: 2,
	float3: 3, half3: 3, vec3: 3,
	float4: 4, half4: 4, vec4: 4,
};

/** The theme's colors, by the names of their roles. Each is four numbers, with its alpha. */
export const COLORS = ['bg', 'surface', 'fg', 'dim', 'accent', 'error', 'warn', 'good', 'info', 'link'];

/** The uniforms the host sets, and how many numbers each may hold. */
export const KNOWN = {
	resolution: [2], cell: [2], grid: [2], origin: [2], time: [1], touch: [2], touchAge: [1], dark: [1],
	date: [4], tilt: [2], pressed: [1], battery: [1], charging: [1], progress: [1], wallpaper: [1],
	corner: [1], cutout: [4], seed: [1],
	...Object.fromEntries(COLORS.map((c) => [c, [4]])),
};

const COMMENTS = /\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
const UNIFORM = /(layout\s*\(\s*color\s*\)\s*)?\buniform\s+([A-Za-z_]\w*)\s+([^;]+);/g;
const DIRECTIVE = /^\s*\/\/\s*@([A-Za-z]+)\b[ \t]*(.*)$/;
const ERROR = /error:\s*(\d+):\s*([^\n]*)/;

/** A number as Kotlin's toFloatOrNull reads one; null when it isn't. */
function float(text) {
	const t = text.trim();
	if (!/^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?[fFdD]?$/.test(t) && !/^[+-]?(?:NaN|Infinity)$/.test(t)) return null;
	return Number(t.replace(/[fFdD]$/, ''));
}
const int = (text) => (/^[+-]?\d+$/.test(text) ? Number(text) : null);
const finite = (x) => x !== null && Number.isFinite(x);
const clamp = (x, lo, hi) => Math.min(Math.max(x, lo), hi);

/** [x] as people write it: 60, not 60.0. */
const number = (x) => String(x);

/** Each directive and what follows it, by its name in lower case: the first of two wins. */
function directives(source) {
	const tags = new Map();
	for (const line of source.split(/\r\n|\r|\n/)) {
		const m = DIRECTIVE.exec(line);
		if (!m) continue;
		const name = m[1].toLowerCase();
		if (!tags.has(name)) tags.set(name, m[2].trim());
	}
	return tags;
}

/** What follows each `@param` in the header. */
function paramLines(source) {
	const out = [];
	for (const line of source.split(/\r\n|\r|\n/)) {
		const m = DIRECTIVE.exec(line);
		if (m && m[1].toLowerCase() === 'param') out.push(m[2].trim());
	}
	return out;
}

const fps = (said) => { const n = int(said); return n !== null && n >= 1 && n <= FPS ? n : null; };
const settles = (said) => { const n = float(said); return n !== null && n >= MIN_SETTLES && n <= MAX_SETTLES ? n : null; };
const still = (said) => { const n = float(said); return n !== null && n >= 0 && n <= MAX_STILL ? n : null; };

/** The uniforms [source] declares, in order: { name, type, color, array, floats }. */
export function uniforms(source) {
	const out = [];
	const code = source.replace(COMMENTS, ' ');
	for (const m of code.matchAll(UNIFORM)) {
		const color = !!m[1];
		const type = m[2];
		for (const raw of m[3].split(',').map((s) => s.trim()).filter(Boolean)) {
			const array = raw.includes('[');
			const name = (array ? raw.slice(0, raw.indexOf('[')) : raw).trim();
			out.push({ name, type, color, array, floats: array ? 0 : FLOATS[type] ?? 0 });
		}
	}
	return out;
}

// ---- ShaderParam ----------------------------------------------------------------------------

const LINE = /^([A-Za-z_]\w{0,39})\s+(\S+)\s*=\s*(\S+)\s*(.*)$/;
const FOLLOW = /(?:^|\s+)@follow\s+(\S.*?)\s*$/;
const RANGE = /^(-?\d+(?:\.\d+)?)\.\.(-?\d+(?:\.\d+)?)$/;
const CHOICE = /^[A-Za-z0-9][A-Za-z0-9-]{0,19}$/;

/** Reads what follows `@param`: { name, title, min, max, default, whole, choices }. Null when it can't be read. */
export function readParam(line) {
	const m = LINE.exec(line.trim());
	if (!m) return null;
	const [, name, values, first, rest] = m;
	const words = rest.replace(FOLLOW, '');
	const title = words.trim() || name.replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
	const r = RANGE.exec(values);
	if (r) {
		const min = Number(r[1]);
		const max = Number(r[2]);
		const def = float(first);
		if (def === null || min >= max || !(def >= min && def <= max)) return null;
		const whole = !values.replaceAll('..', '').includes('.') && !first.includes('.');
		return param({ name, title, min, max, default: def, whole, choices: [] });
	}
	const choices = values.split('|');
	if (choices.length < 2 || choices.length > MAX_CHOICES) return null;
	if (new Set(choices.map((c) => c.toLowerCase())).size !== choices.length) return null;
	if (!choices.every((c) => CHOICE.test(c))) return null;
	const def = choices.indexOf(first);
	if (def < 0) return null;
	return param({ name, title, min: 0, max: choices.length - 1, default: def, whole: true, choices });
}

function param(p) {
	p.clean = (v) => {
		const c = clamp(v, p.min, p.max);
		return p.whole || p.choices.length ? Math.round(c) : c;
	};
	/** [value] in words: at most two decimals. */
	p.text = (v) => {
		const c = p.clean(v);
		if (p.choices.length) return p.choices[Math.round(c)];
		if (p.whole) return String(Math.round(c));
		const s = c.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
		return s === '-0' ? '0' : s;
	};
	/** What it can be: `0..2`, or its words. */
	p.range = p.choices.length ? p.choices.join(' · ') : `${p.text(p.min)}..${p.text(p.max)}`;
	return p;
}

// ---- ShaderMeta -----------------------------------------------------------------------------

/** What the file says about itself, read from its text without compiling it. */
export function parse(source) {
	const tags = directives(source);
	const said = (...names) => {
		for (const n of names) if (tags.has(n)) return tags.get(n) || null;
		return null;
	};
	const onceSaid = said('once');
	const onceN = onceSaid === null ? null : float(onceSaid);
	const once = finite(onceN) ? clamp(onceN, ONCE_MIN, ONCE_MAX) : null;
	const seen = new Set();
	const params = paramLines(source)
		.map(readParam)
		.filter((p) => p && !seen.has(p.name) && seen.add(p.name))
		.slice(0, MAX_PARAMS);
	const all = uniforms(source);
	const meta = {
		title: said('name', 'title'),
		description: said('description', 'desc'),
		animated: tags.has('animated') || once !== null,
		uniforms: all,
		params,
		once,
		text: tags.has('text'),
		fps: tags.has('fps') ? fps(tags.get('fps')) : null,
		settles: tags.has('settles') ? settles(tags.get('settles')) : null,
		still: said('still') === null ? null : still(said('still')),
	};
	meta.uses = (name) => all.some((u) => u.name === name);
	meta.declared = (name) => all.find((u) => u.name === name);
	/** The uniforms Kolahal sets for it, in its order. */
	meta.sets = all.map((u) => u.name).filter((n) => n in KNOWN);
	/** The uniforms it declares that nothing sets: each stays 0. */
	meta.unset = all.map((u) => u.name).filter((n) => n !== INPUT && !(n in KNOWN) && !params.some((p) => p.name === n));
	meta.pace = pace(meta);
	return meta;
}

/** The header's lines that can't be read, each with what to write instead. The shader works without them. */
export function warnings(source) {
	const tags = directives(source);
	const quoted = (name, said) => (said ? `'@${name} ${said}'` : `'@${name}'`);
	const out = [];
	if (tags.has('fps') && fps(tags.get('fps')) === null) out.push(`${quoted('fps', tags.get('fps'))}: write a whole number from 1 to ${FPS}`);
	if (tags.has('settles') && settles(tags.get('settles')) === null)
		out.push(`${quoted('settles', tags.get('settles'))}: write the seconds, from ${number(MIN_SETTLES)} to ${number(MAX_SETTLES)}`);
	if (tags.has('still') && still(tags.get('still')) === null) out.push(`${quoted('still', tags.get('still'))}: write the seconds, from 0 to ${number(MAX_STILL)}`);
	return out;
}

const typeNames = (counts) => counts.map((n) => (n === 1 ? 'float' : `float${n}`)).join(' or ');

/** What keeps [source] from being used, checked before it is compiled. Empty when it can be tried. */
export function problems(source) {
	const out = [];
	if (new TextEncoder().encode(source).length > MAX_BYTES) out.push(`the file is larger than ${MAX_BYTES / 1024} KB`);
	const all = uniforms(source);
	if (!all.some((u) => u.name === INPUT && u.type === 'shader' && !u.array)) out.push(`it needs 'uniform shader ${INPUT};', the surface it draws over`);
	for (const u of all) {
		if (u.name === INPUT) continue;
		const counts = KNOWN[u.name];
		if (u.type === 'shader') out.push(`'${u.name}': the only shader input is ${INPUT}`);
		else if (counts && !counts.includes(u.floats)) out.push(`'${u.name}' must be a ${typeNames(counts)}, not ${u.type}${u.array ? '[]' : ''}`);
	}
	const lines = paramLines(source);
	if (lines.length > MAX_PARAMS) out.push(`it has ${lines.length} parameters, and can have ${MAX_PARAMS}`);
	const seen = new Set();
	for (const line of lines) {
		const p = readParam(line);
		if (!p) {
			out.push(`'@param ${line}' can't be read: write '@param amount 0.0..2.0 = 1.0 Title' or '@param shape a|b = a Title'`);
			continue;
		}
		if (p.name in KNOWN || p.name === INPUT) out.push(`'${p.name}' can't be a parameter: Kolahal sets it`);
		else if (seen.has(p.name)) out.push(`the parameter '${p.name}' is there twice`);
		else if (!all.some((u) => u.name === p.name && u.floats === 1)) out.push(`the parameter '${p.name}' needs 'uniform float ${p.name};'`);
		seen.add(p.name);
	}
	return out;
}

/** A compiler's message ("error: 12: unknown identifier 'x'", with more lines after): { line, text }. */
export function compileError(message) {
	const m = ERROR.exec(message);
	if (!m) {
		const first = message.split('\n').map((s) => s.trim()).find(Boolean);
		return { line: null, text: first ?? "it doesn't compile" };
	}
	return { line: Number(m[1]), text: m[2].trim() };
}

/** The problem as the log says it: "line 37: unknown identifier 'bgg'". */
export const problemText = (e) => (e.line === null ? e.text : `line ${e.line}: ${e.text}`);
/** The log's first letter in capitals (Shaders.problemLine). */
export const capital = (s) => s.replace(/^./, (c) => c.toUpperCase());

/** Whether a shader can be the live wallpaper; why not, in the app's words (WallpaperChoice.problem), or null. */
export function wallpaperProblem(name, meta) {
	if (meta.once !== null) return `${name} is a one-off: it plays once`;
	if (meta.text) return `${name} works on text, and a wallpaper has none`;
	return null;
}

// ---- ShaderClock ----------------------------------------------------------------------------

/** How often a shader that moves is drawn (ShaderClock.Pace.of). */
export function pace(meta) {
	if (meta.once !== null) return { fps: FPS, settlesMs: IDLE_MS, readsTouch: false, onlyAfterTouch: false };
	const reads = meta.uses('touch') || meta.uses('touchAge') || meta.uses('pressed');
	const touchClock = meta.animated && meta.uses('touchAge') && !meta.uses('time') && !meta.uses('progress');
	return {
		fps: meta.fps ?? FPS,
		settlesMs: meta.settles !== null ? Math.trunc(meta.settles * 1000) : IDLE_MS,
		readsTouch: reads,
		onlyAfterTouch: touchClock && meta.settles !== null,
	};
}

/** The time from one frame to the next, in ms; null when it stands still until the next touch. [sinceTouchMs]: null before one. */
export function frameMs(p, sinceTouchMs) {
	const settling = sinceTouchMs !== null && sinceTouchMs < p.settlesMs;
	if (p.onlyAfterTouch && !settling) return null;
	return Math.trunc(1000 / (p.readsTouch && settling ? FPS : p.fps));
}

/** How far the clock moves for [gapMs] between two frames [frame] ms apart: a long gap is one step. */
export const step = (gapMs, frame) => clamp(gapMs, 0, Math.max(PAUSE_MS, 2 * frame));

/** How often a shader that moves is drawn, as `shader check` says it. */
export function redrawn(p) {
	const s = p.settlesMs / 1000;
	const seconds = Number.isInteger(s) ? String(s) : String(s);
	if (p.onlyAfterTouch) return `redrawn ${FPS} times a second for ${seconds} s after a touch`;
	if (p.readsTouch && p.fps < FPS) return `redrawn ${p.fps} times a second, ${FPS} for ${seconds} s after a touch`;
	return `redrawn ${p.fps} times a second`;
}

/** What `shader check` says of one without problems, after "Shader <name> ": "compiles", then a line each. */
export function checkLines(meta) {
	return [
		'compiles',
		meta.sets.length ? `Kolahal sets: ${meta.sets.join(', ')}` : null,
		meta.unset.length ? `Not set by Kolahal, so 0: ${meta.unset.join(', ')}` : null,
		meta.params.length ? `Parameters: ${meta.params.map((p) => `${p.name} ${p.range}`).join(', ')}` : null,
		meta.animated ? `Animated: ${redrawn(meta.pace)}` : null,
	].filter(Boolean);
}
