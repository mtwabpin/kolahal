// Puts the preview video together from tools/video/video.json: run it through compose.sh, whose head
// says how. What it does, in order:
//
//  1. Reads the data file and checks it: each chapter's length against its cuts, the short cut's
//     segments against the full cut, and each caption's time on screen against its length (at
//     least 3 s, and about 15 characters a second).
//  2. Works out each chapter of the cut: the pieces of recordings it is made of, its captions, its
//     ground, and whether it joins the next with a crossfade (where the ground changes). A
//     crossfade overlaps the two chapters by half its length on each side, so every chapter
//     starts in the joined video where the plan says, whatever the form.
//  3. Shoots the still layers it needs from cards.html with headless Chrome (cached by what they
//     show, under <out>/work/layers/).
//  4. Cuts each chapter's phone clip from its recordings (1080 × 2400, 30 frames a second), with a
//     plain stand-in screen for a recording not yet made.
//  5. Builds the form asked for, one chapter at a time, each into files of its own under work/:
//       whole    ground, phone in its frame, captions (H.264 in MP4; the default)
//       phone    the phone clips alone, upright at the recording's size (H.264 in MP4)
//       framed   the phone in its frame with round corners and shadow, on a transparent ground
//                (VP9 with alpha in WebM)
//     Each chapter's file is cut in up to three: its first second (head) and last second (tail)
//     where it crossfades with its neighbour, and the rest (body). A crossfade is a small job of
//     its own on a tail and the next head; the join reads the bodies and crossfades in order
//     (ffmpeg's concat demuxer) and writes the video.
//  6. Writes the captions as an SRT file beside it, the same for every form.
//
// Memory: no job ever holds more than one chapter. ffmpeg runs one at a time, with a bounded number
// of threads, and still pictures are looped only for the seconds they show. Where systemd-run is
// there, each ffmpeg and Chrome runs in a scope with a memory cap (VIDEO_MEMORY_MAX, 6G by
// default), so a job that grows is stopped alone; VIDEO_THREADS (4 by default) bounds the threads.
// The run prints the largest memory an ffmpeg job used.
//
// Seconds in the data file:
//  - full.cuts: [{in, out}, …] in the recording's own seconds; their lengths add up to the
//    chapter's seconds. Empty: the chapter's seconds from recording.lead (the still lead-in) on.
//  - short.segments: [{chapter?, from, to}, …] in the full cut's seconds of that chapter (the
//    plan's "s" column), so the short cut follows when the full cut's cuts change. Without
//    chapter: the same chapter.
//  - captions: [{at, text, note?}, …] from the chapter's start; each shows until the next one, the
//    last until the chapter's end. `word` shows as code. head: the label and heading over all of
//    a chapter's captions; card: the opening's and the closing's words instead.
//  - close: [[from, to], …] where the phone is shown 1.5 times larger, with its top off the frame.
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');

// ---------------------------------------------------------------------------------------------
// Arguments and places.

const usage = `usage: tools/video/compose.sh [full|short] [theme] [--4k] [--only phone|framed] [--chapter <id>] [--check]`;
const argv = process.argv.slice(2);
let cut = 'full';
let theme = null;
let only = 'whole';
let k4 = false;
let checkOnly = false;
let onlyChapter = null;
for (let i = 0; i < argv.length; i++) {
	const a = argv[i];
	if (a === 'full' || a === 'short') cut = a;
	else if (a === '--4k') k4 = true;
	else if (a === '--check') checkOnly = true;
	else if (a === '--only') only = argv[++i];
	else if (a === '--chapter') onlyChapter = argv[++i];
	else if (a.startsWith('--only=')) only = a.slice(7);
	else if (a === '-h' || a === '--help') { console.log(usage); process.exit(0); }
	else if (!a.startsWith('-') && theme === null) theme = a;
	else fail(`Unknown argument: ${a}\n${usage}`);
}
if (!['whole', 'phone', 'framed'].includes(only)) fail(`--only takes phone or framed.\n${usage}`);

const themes = JSON.parse(fs.readFileSync(path.join(root, 'site/src/data/themes.json'), 'utf8'));
theme ??= themes.default;
if (!themes.themes[theme]) fail(`No theme ${theme} in site/src/data/themes.json.`);
const own = theme === themes.default;

const videoDir = path.resolve(process.env.VIDEO_DIR || path.join(root, 'build/video'));
const outDir = own ? videoDir : path.join(videoDir, theme);
const recDir = path.resolve(process.env.RECORDINGS || (own ? path.join(videoDir, 'recordings') : path.join(videoDir, 'recordings', theme)));
// Scenes drawn for another theme (tools/site-scenes.sh <theme>) where there are any.
const themeScenes = path.join(root, 'build/pictures', theme, 'site/src/assets/scenes');
const sceneDir = !own && fs.existsSync(themeScenes) ? themeScenes : path.join(root, 'site/src/assets/scenes');
const work = path.join(videoDir, 'work');

const data = JSON.parse(fs.readFileSync(path.join(here, 'video.json'), 'utf8'));
const FPS = data.fps;
const CF = data.crossfade;
const REC = data.recording;

// The frame: 1920 × 1080, or 3840 × 2160 with --4k. Layout in 1920 × 1080 pixels, times U.
const U = k4 ? 2 : 1;
const W = 1920 * U;
const H = 1080 * U;
// The phone's layer from cards.html (kind frame): the frame at the recording's scale, with room
// for its shadow around it.
const PH = { w: 1554, h: 2854, frameX: 200, frameY: 40, frameW: 1154, frameH: 2474, screenX: 237, screenY: 77 };
// The frame stands at the frame's full height less 80 px above and below (1840 of 2160 at 4K),
// its left edge a ninth of the way in.
const phoneH = H * 1840 / 2160;
const K = phoneH / PH.frameH;
const phoneCX = W / 9 + (PH.frameW * K) / 2;
const phoneBottom = (H + phoneH) / 2;
// The captions' column, right of the phone (even at 1.5 times), and its two layers.
const COL = { x: 880, w: 960 };
const HEAD = { y: 0, h: 520 };
const TEXT = { y: 548, h: 492 };
const ZOOM = 0.8; // seconds the phone takes to grow or shrink
const FADE = 0.3; // seconds a caption takes to fade in or out

function fail(msg) {
	console.error(msg);
	process.exit(1);
}
const r3 = (x) => Math.round(x * 1000) / 1000;

// ---------------------------------------------------------------------------------------------
// 1 and 2. The chapters of the cut.

const byId = new Map(data.chapters.map((c) => [c.id, c]));
const warnings = new Set();

// The full cut's pieces of a chapter: [{in, out}] in its recording's seconds.
function fullCuts(ch) {
	const cuts = ch.full.cuts?.length ? ch.full.cuts : [{ in: REC.lead, out: REC.lead + ch.full.seconds }];
	const sum = cuts.reduce((s, c) => s + (c.out - c.in), 0);
	if (Math.abs(sum - ch.full.seconds) > 0.01)
		warnings.add(`${ch.id}: its cuts add up to ${r3(sum)} s, not ${ch.full.seconds}: the last piece is ${sum > ch.full.seconds ? 'cut short' : 'held on its last frame'}.`);
	return cuts;
}

// The recording's seconds for the full cut's seconds [from, to] of a chapter, as pieces.
function mapFull(ch, from, to) {
	const pieces = [];
	let t = 0;
	const cuts = fullCuts(ch);
	cuts.forEach((c, i) => {
		const len = c.out - c.in;
		const end = i === cuts.length - 1 ? Infinity : t + len;
		const a = Math.max(from, t);
		const b = Math.min(to, end);
		if (b > a) pieces.push({ id: ch.id, in: c.in + (a - t), out: c.in + (b - t) });
		t += len;
	});
	return pieces;
}

function resolve(ch) {
	const part = ch[cut];
	let pieces;
	if (cut === 'full') {
		pieces = fullCuts(ch).map((c) => ({ id: ch.id, in: c.in, out: c.out }));
		const sum = pieces.reduce((s, p) => s + p.out - p.in, 0);
		if (sum > part.seconds) {
			// Cut the last piece short.
			pieces.at(-1).out -= sum - part.seconds;
		}
	} else {
		pieces = [];
		for (const s of part.segments) {
			const src = byId.get(s.chapter || ch.id);
			if (!src) fail(`${ch.id}: short.segments names ${s.chapter}, which isn't a chapter.`);
			pieces.push(...mapFull(src, s.from, s.to));
		}
		const sum = pieces.reduce((s, p) => s + p.out - p.in, 0);
		if (Math.abs(sum - part.seconds) > 0.01)
			warnings.add(`${ch.id}: its short segments add up to ${r3(sum)} s, not ${part.seconds}.`);
	}
	const captions = (part.captions || ch.full.captions || []).map((c, i, all) => ({
		...c,
		to: i + 1 < all.length ? all[i + 1].at : part.seconds,
	}));
	return {
		ch,
		id: ch.id,
		seconds: part.seconds,
		pieces,
		captions,
		close: part.close || [],
		ground: ch.ground,
	};
}

const chapters = data.chapters.filter((c) => c[cut]).map(resolve);
// Where the ground changes, a crossfade: half of it before the chapter's start, half after.
chapters.forEach((c, i) => {
	c.xfIn = i > 0 && chapters[i - 1].ground !== c.ground;
	c.pre = c.xfIn ? CF / 2 : 0;
});
chapters.forEach((c, i) => {
	c.xfOut = i + 1 < chapters.length && chapters[i + 1].xfIn;
	c.post = c.xfOut ? CF / 2 : 0;
	c.dur = c.pre + c.seconds + c.post;
});
let clock = 0;
for (const c of chapters) {
	c.start = clock;
	clock += c.seconds;
}
const total = clock;

// The ground's slow pan: one movement across each run of chapters on the same ground, so it goes
// on over a plain cut. 0.1% of the frame's width a second, at least 3% over a run.
const runs = [];
for (const c of chapters) {
	if (runs.length && runs.at(-1).ground === c.ground) runs.at(-1).chapters.push(c);
	else runs.push({ ground: c.ground, chapters: [c] });
}
for (const run of runs) {
	const first = run.chapters[0];
	const last = run.chapters.at(-1);
	const t0 = first.start - first.pre;
	const t1 = last.start + last.seconds + last.post;
	run.extra = Math.max(0.03, 0.001 * (t1 - t0));
	for (const c of run.chapters) {
		c.run = run;
		c.p0 = (c.start - c.pre - t0) / (t1 - t0);
		c.p1 = (c.start - c.pre + c.dur - t0) / (t1 - t0);
	}
}

// The captions' time on screen.
const plain = (s) => s.replace(/`/g, '');
for (const c of chapters) {
	for (const cap of c.captions) {
		const shown = cap.to - cap.at;
		const need = Math.max(3, plain(cap.text).length / 15);
		if (shown + 0.05 < need)
			warnings.add(`${c.id} (${cut}): "${plain(cap.text)}" shows ${r3(shown)} s; ${r3(need)} s would read at 15 characters a second.`);
	}
}

// ---------------------------------------------------------------------------------------------
// 6. The captions as SRT (written first: it needs nothing else).

function srtTime(t) {
	const ms = Math.round(t * 1000);
	const p = (n, w = 2) => String(n).padStart(w, '0');
	return `${p(Math.floor(ms / 3600000))}:${p(Math.floor(ms / 60000) % 60)}:${p(Math.floor(ms / 1000) % 60)},${p(ms % 1000, 3)}`;
}
function srt() {
	const entries = [];
	for (const c of chapters) {
		if (c.ch.card) {
			const k = c.ch.card;
			entries.push([c.start, c.start + c.seconds, [k.heading, k.text, k.line].filter(Boolean).join('\n')]);
		}
		c.captions.forEach((cap, i) => {
			const lines = [];
			if (i === 0 && c.ch.head) lines.push(c.ch.head.heading);
			lines.push(plain(cap.text));
			if (cap.note) lines.push(plain(cap.note));
			entries.push([c.start + cap.at, c.start + cap.to, lines.join('\n')]);
		});
	}
	return entries.map(([a, b, text], i) => `${i + 1}\n${srtTime(a)} --> ${srtTime(b - 0.001)}\n${text}\n`).join('\n');
}


// ---------------------------------------------------------------------------------------------
// Running things: one at a time, each in a scope with a memory cap where systemd-run is there.

const CAP = process.env.VIDEO_MEMORY_MAX || '6G';
const THREADS = String(process.env.VIDEO_THREADS || 4);
const capped = spawnSync('sh', ['-c', 'command -v systemd-run']).status === 0 && process.env.VIDEO_NO_CAP !== '1';
const scope = capped ? ['systemd-run', '--user', '--scope', '-q', '-p', `MemoryMax=${CAP}`, '-p', 'MemorySwapMax=1G', '--'] : [];
let peakKiB = 0;
let peakJob = '';

// job(cmd, args, what): runs cmd under the cap and waits for it. For ffmpeg, notes its peak memory.
function job(cmd, args, what) {
	return new Promise((ok, no) => {
		const all = [...scope, cmd, ...args];
		const p = spawn(all[0], all.slice(1), { stdio: ['ignore', 'ignore', 'pipe'] });
		let err = '';
		p.stderr.on('data', (d) => (err = (err + d).slice(-20000)));
		p.on('close', (code, signal) => {
			const rss = /maxrss=(\d+)KiB/.exec(err);
			if (rss && +rss[1] > peakKiB) {
				peakKiB = +rss[1];
				peakJob = what;
			}
			if (code === 0) return ok();
			const lines = err.split('\n').filter((l) => /error|failed|invalid|cannot|no such/i.test(l)).slice(-12).join('\n');
			const killed = code === 137 || signal === 'SIGKILL' ? ` (killed: it went over the memory cap of ${CAP}?)` : '';
			no(new Error(`${what}: ${cmd} failed${killed}\n${lines || err.slice(-3000)}`));
		});
	});
}
const ffmpeg = (args, what) => job('ffmpeg', ['-hide_banner', '-nostdin', '-nostats', '-loglevel', 'info', '-benchmark', '-y',
	'-filter_complex_threads', THREADS, ...args], what);
// Decoders and encoders get the same bound.
const IN = ['-threads', THREADS];
const OUT = ['-threads', THREADS];

function probeDuration(file) {
	const r = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]);
	return parseFloat(r.stdout.toString()) || 0;
}
// fresh(file, sig): whether file was made from the same things as now (sig) by an earlier run.
const fresh = (file, sig) => fs.existsSync(file) && fs.existsSync(file + '.json') && fs.readFileSync(file + '.json', 'utf8') === sig;
const mark = (file, sig) => fs.writeFileSync(file + '.json', sig);
const mtime = (f) => fs.statSync(f).mtimeMs;

// ---------------------------------------------------------------------------------------------
// 3. The still layers.

const chrome = process.env.CHROME || 'google-chrome';
const page = path.join(here, 'cards.html');
const pageHash = createHash('sha1')
	.update(fs.readFileSync(page))
	.update(fs.readFileSync(path.join(root, 'site/src/styles/tokens.css')))
	.digest('hex')
	.slice(0, 10);
const layerDir = path.join(work, 'layers');
const wanted = new Map();

// layer(spec, w, h, scale): the PNG that cards.html draws for spec at w × h CSS pixels, shot at
// scale pixels each. Shot later, all at once, by shootLayers().
function layer(spec, w, h, scale = 1) {
	const key = createHash('sha1').update(JSON.stringify([spec, w, h, scale, pageHash])).digest('hex').slice(0, 16);
	const file = path.join(layerDir, `${spec.kind}-${key}.png`);
	if (!wanted.has(file)) wanted.set(file, { spec, w, h, scale });
	return file;
}
async function shootLayers() {
	fs.mkdirSync(layerDir, { recursive: true });
	const todo = [...wanted].filter(([file]) => !fs.existsSync(file));
	for (const [file, { spec, w, h, scale }] of todo) {
		const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'kolahal-video-'));
		const tmp = file + '.tmp.png';
		try {
			await job(chrome, [
				'--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', '--no-default-browser-check',
				`--user-data-dir=${profile}`, `--force-device-scale-factor=${scale}`, `--window-size=${w},${h}`,
				'--default-background-color=00000000', '--virtual-time-budget=3000', '--allow-file-access-from-files',
				`--screenshot=${tmp}`, `file://${page}#${encodeURIComponent(JSON.stringify(spec))}`,
			], `layer ${spec.kind}`);
			fs.renameSync(tmp, file);
		} finally {
			fs.rmSync(profile, { recursive: true, force: true });
		}
	}
	if (todo.length) console.log(`Shot ${todo.length} layers (${wanted.size - todo.length} from before).`);
}

// ---------------------------------------------------------------------------------------------
// Pieces: a chapter's video cut where its crossfades begin and end.

// The parts of chapter c: [name, from, to] in its own seconds.
function parts(c) {
	const p = [];
	if (c.xfIn) p.push(['head', 0, CF]);
	p.push(['body', c.xfIn ? CF : 0, c.xfOut ? c.dur - CF : c.dur]);
	if (c.xfOut) p.push(['tail', c.dur - CF, c.dur]);
	return p;
}
// split(c, label, base, codec, ext): the filters and outputs that write the stream `label` of
// chapter c as base.head.ext, base.body.ext and base.tail.ext (those it has).
function split(c, label, base, codec, ext) {
	const ps = parts(c);
	const graph = [`${label}split=${ps.length}${ps.map((_, i) => `[o${i}]`).join('')}`,
		...ps.map(([, a, b], i) => `[o${i}]trim=start=${r3(a)}:end=${r3(b)},setpts=PTS-STARTPTS[p${i}]`)].join(';');
	const files = Object.fromEntries(ps.map(([n]) => [n, `${base}.${n}.${ext}`]));
	const args = ps.flatMap(([n], i) => ['-map', `[p${i}]`, ...codec, files[n]]);
	return { graph, args, files };
}
// A list for the concat demuxer: the files one after the other.
function concatList(file, files) {
	fs.writeFileSync(file, files.map((f) => `file '${f.replace(/'/g, "'\\''")}'\n`).join(''));
	return file;
}
// The codecs of the pieces: near-lossless H.264, or lossless FFV1 where there is transparency.
const X264_WORK = ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', String(FPS), ...OUT];
const FFV1_WORK = ['-c:v', 'ffv1', '-level', '3', '-slices', '4', '-pix_fmt', 'yuva420p', '-r', String(FPS), ...OUT];

// ---------------------------------------------------------------------------------------------
// 4. The phone clips.

const EXT = ['.mp4', '.webm', '.mkv', '.mov'];
const missing = [];
const sources = new Map();
function source(id) {
	if (sources.has(id)) return sources.get(id);
	const ch = byId.get(id);
	const stem = path.join(recDir, path.parse(ch.recording).name);
	const found = [path.join(recDir, ch.recording), ...EXT.map((e) => stem + e)].find((f) => fs.existsSync(f));
	let s;
	if (found) s = { file: found, real: true };
	else {
		missing.push(ch.recording);
		// A plain stand-in screen, as long as a recording of the chapter would be.
		const colors = themes.themes[theme].dark;
		const still = layer({ kind: 'missing', title: ch.title, file: ch.recording, colors }, REC.width, REC.height);
		s = { file: null, still, real: false };
	}
	sources.set(id, s);
	return s;
}

// The phone clip of chapter c, as pieces; returns {head?, body, tail?, list}.
async function phoneClip(c) {
	const base = path.join(work, cut, `${c.id}.phone`);
	// Half a crossfade more at either end, from the recording around the cut where there is any.
	const pieces = c.pieces.map((p) => ({ ...p }));
	pieces[0].in -= c.pre;
	pieces.at(-1).out += c.post;
	const sig = JSON.stringify([pieces, parts(c), pieces.map((p) => {
		const s = source(p.id);
		return s.real ? [s.file, mtime(s.file)] : [s.still];
	})]);
	const out = split(c, '[v]', base, X264_WORK, 'mp4');
	fs.mkdirSync(path.dirname(base), { recursive: true });
	out.list = concatList(base + '.txt', Object.values(out.files));
	if (fresh(out.files.body, sig)) return out;
	const args = [];
	const filters = [];
	pieces.forEach((p, i) => {
		const s = source(p.id);
		const lead = Math.max(0, -p.in); // a piece before the recording's start holds its first frame
		const a = Math.max(0, p.in);
		const len = p.out - a;
		if (s.real) args.push(...IN, '-ss', r3(a), '-t', r3(len + 0.5), '-i', s.file);
		else args.push('-i', s.still);
		const src = s.real ? `[${i}:v]fps=${FPS}` : `[${i}:v]loop=loop=-1:size=1,setpts=N/${FPS}/TB,trim=duration=${r3(len + 0.5)}`;
		filters.push(
			`${src},scale=${REC.width}:${REC.height}:flags=lanczos,setsar=1,format=yuv420p,` +
				`trim=duration=${r3(len)},setpts=PTS-STARTPTS,` +
				`tpad=start_mode=clone:start_duration=${r3(lead)}:stop_mode=clone:stop_duration=${r3(len + 1)},` +
				`trim=duration=${r3(lead + len)},setpts=PTS-STARTPTS[p${i}x]`,
		);
	});
	filters.push(`${pieces.map((_, i) => `[p${i}x]`).join('')}concat=n=${pieces.length}:v=1:a=0,` +
		`tpad=stop_mode=clone:stop_duration=${r3(c.dur)},trim=duration=${r3(c.dur)}[v]`);
	fs.mkdirSync(path.dirname(base), { recursive: true });
	await ffmpeg([...args, '-filter_complex', `${filters.join(';')};${out.graph}`, ...out.args], `${c.id} phone clip`);
	mark(out.files.body, sig);
	return out;
}

// ---------------------------------------------------------------------------------------------
// 5. The forms, one chapter at a time.

// The phone's growth for the close-ups: 0 at full size, 1 at closeScale, eased, at time t of
// the chapter's clip (which starts `pre` seconds before the chapter).
function zoomExpr(c) {
	if (!c.close.length) return null;
	const ps = c.close.map(([a, b]) => {
		const A = r3(a + c.pre);
		const B = r3(b + c.pre);
		const P = `min(clip((t-${A})/${ZOOM},0,1),clip((${B}-t)/${ZOOM},0,1))`;
		return `(${P})*(${P})*(3-2*(${P}))`;
	});
	return ps.reduce((acc, p) => `max(${acc},${p})`);
}

// The layers of the whole picture of chapter c: registered first, shot all at once.
function wholeLayers(c) {
	const g = data.grounds[c.ground];
	const scene = g.scene ? `file://${path.join(sceneDir, g.scene)}` : null;
	const gw = Math.round((W * (1 + c.run.extra)) / U / 2) * 2; // CSS pixels
	const L = {
		ground: layer({ kind: 'ground', tokens: g.tokens, scene }, gw, 1080, U),
		wash: layer({ kind: 'wash', tokens: g.tokens, scene }, 1920, 1080, U),
		frame: layer({ kind: 'frame', tokens: g.tokens }, PH.w, PH.h),
		mask: layer({ kind: 'mask' }, REC.width, REC.height),
		// The captions: [png, x, y, from, to] in the clip's seconds, never during a crossfade
		// (words of two chapters would lie on each other).
		over: [],
	};
	const add = (png, x, y, a, b) =>
		L.over.push([png, x, y, r3(Math.max(c.xfIn ? CF : 0, a)), r3(Math.min(c.xfOut ? c.dur - CF : c.dur, b))]);
	if (c.ch.card) add(layer({ kind: 'card', tokens: g.tokens, ...c.ch.card }, COL.w, 1080, U), COL.x, 0, 0, c.dur);
	if (c.ch.head) add(layer({ kind: 'head', tokens: g.tokens, ...c.ch.head }, COL.w, HEAD.h, U), COL.x, HEAD.y, c.pre, c.pre + c.seconds);
	for (const cap of c.captions) {
		const png = layer({ kind: 'text', tokens: g.tokens, text: cap.text, note: cap.note }, COL.w, TEXT.h, U);
		add(png, COL.x, TEXT.y, c.pre + cap.at, c.pre + cap.to);
	}
	return L;
}

async function wholeChapter(c, clip, L) {
	const base = path.join(work, `${cut}-${H}p`, c.id);
	const out = split(c, '[out]', base, X264_WORK, 'mp4');
	const sig = JSON.stringify([L, c.p0, c.p1, c.close, c.dur, mtime(clip.files.body), W]);
	if (fresh(out.files.body, sig)) return out;
	fs.mkdirSync(path.dirname(base), { recursive: true });
	const D = r3(c.dur);
	// A still picture as a stream, for `len` seconds only.
	const still = (i, len) => `[${i}:v]loop=loop=-1:size=1,setpts=N/${FPS}/TB,trim=duration=${r3(len)}`;
	const inputs = [...IN, '-f', 'concat', '-safe', '0', '-i', clip.list, '-i', L.ground, '-i', L.wash, '-i', L.frame, '-i', L.mask,
		...L.over.flatMap((o) => ['-i', o[0]])];
	const f = [];
	// The ground, panning from p0 to p1 of its extra width over the chapter, and the wash.
	f.push(`${still(1, D)},format=rgb24,crop=${W}:${H}:x='(iw-ow)*(${r3(c.p0)}+${r3(c.p1 - c.p0)}*t/${D})':y=0[g0]`);
	f.push(`[2:v]format=rgba[wash]`);
	f.push(`[g0][wash]overlay=0:0:format=rgb:eof_action=repeat[g]`);
	// The phone: the recording with round corners in its frame, at the recording's scale.
	f.push(`[4:v]format=gray[mask]`);
	f.push(`[0:v]format=yuv420p,setpts=PTS-STARTPTS[rec]`);
	f.push(`[rec][mask]alphamerge[screen]`);
	f.push(`${still(3, D)},format=rgba[frame]`);
	f.push(`[frame][screen]overlay=${PH.screenX}:${PH.screenY}:format=rgb:eof_action=endall[phone]`);
	const zoom = zoomExpr(c);
	const s = data.closeScale - 1;
	const ex = (e) => e.replace(/,/g, '\\,');
	if (zoom) {
		const scale = `${r3(K)}*(1+${s}*(${zoom}))`;
		f.push(`[phone]scale=w='trunc(${PH.w}*${ex(scale)}/2)*2':h='trunc(${PH.h}*${ex(scale)}/2)*2':eval=frame:flags=bicubic,format=yuva420p[ph]`);
	} else {
		f.push(`[phone]scale=${Math.round((PH.w * K) / 2) * 2}:${Math.round((PH.h * K) / 2) * 2}:flags=bicubic,format=yuva420p[ph]`);
	}
	// Placed by the frame's middle and its bottom edge, which stay put as it grows.
	const fx = `${r3(phoneCX)}-${PH.frameX + PH.frameW / 2}*w/${PH.w}`;
	const fy = `${r3(phoneBottom)}-${PH.frameY + PH.frameH}*h/${PH.h}`;
	f.push(`[g][ph]overlay=x='${fx}':y='${fy}':eval=${zoom ? 'frame' : 'init'}:format=yuv420[v0]`);
	// The captions, each looped only for its seconds, faded in and out.
	L.over.forEach(([, x, y, A, B], i) => {
		const len = r3(B - A);
		f.push(`${still(5 + i, len)},format=yuva420p,fade=t=in:st=0:d=${FADE}:alpha=1,` +
			`fade=t=out:st=${r3(len - FADE)}:d=${FADE}:alpha=1,setpts=PTS+${A}/TB[c${i}]`);
		f.push(`[v${i}][c${i}]overlay=${x * U}:${y * U}:eof_action=pass:format=yuv420[v${i + 1}]`);
	});
	f.push(`[v${L.over.length}]format=yuv420p,trim=duration=${D}[out]`);
	await ffmpeg([...inputs, '-filter_complex', `${f.join(';')};${out.graph}`, ...out.args], `${c.id} at ${W} × ${H}`);
	mark(out.files.body, sig);
	return out;
}

async function framedChapter(c, clip, frame, mask) {
	const base = path.join(work, `${cut}-framed`, c.id);
	const out = split(c, '[out]', base, FFV1_WORK, 'mkv');
	const sig = JSON.stringify([frame, mask, c.dur, mtime(clip.files.body)]);
	if (fresh(out.files.body, sig)) return out;
	fs.mkdirSync(path.dirname(base), { recursive: true });
	const D = r3(c.dur);
	const f = [
		`[1:v]loop=loop=-1:size=1,setpts=N/${FPS}/TB,trim=duration=${D},format=rgba[frame]`,
		`[2:v]format=gray[mask]`,
		`[0:v]format=yuv420p,setpts=PTS-STARTPTS[rec]`,
		`[rec][mask]alphamerge[screen]`,
		`[frame][screen]overlay=${PH.screenX}:${PH.screenY}:format=rgb:eof_action=endall,format=yuva420p,trim=duration=${D}[out]`,
	];
	await ffmpeg([...IN, '-f', 'concat', '-safe', '0', '-i', clip.list, '-i', frame, '-i', mask,
		'-filter_complex', `${f.join(';')};${out.graph}`, ...out.args], `${c.id} framed`);
	mark(out.files.body, sig);
	return out;
}

// The crossfade between chapter a's tail and chapter b's head: a job of one second.
async function crossfade(a, b, pa, pb, codec, ext) {
	const file = path.join(path.dirname(pa.files.tail), `${a.id}+${b.id}.${ext}`);
	const sig = JSON.stringify([mtime(pa.files.tail), mtime(pb.files.head)]);
	if (fresh(file, sig)) return file;
	await ffmpeg([...IN, '-i', pa.files.tail, ...IN, '-i', pb.files.head, '-filter_complex',
		`[0:v]settb=AVTB[a];[1:v]settb=AVTB[b];[a][b]xfade=transition=fade:duration=${CF}:offset=0[v]`,
		'-map', '[v]', ...codec, file], `${a.id} to ${b.id} crossfade`);
	mark(file, sig);
	return file;
}

// The join: bodies and crossfades in order, read one after the other.
async function join(pieces, codec, ext, listFile) {
	const order = [];
	for (let i = 0; i < chapters.length; i++) {
		order.push(pieces[i].files.body);
		if (chapters[i].xfOut) order.push(await crossfade(chapters[i], chapters[i + 1], pieces[i], pieces[i + 1], codec, ext));
	}
	return concatList(listFile, order);
}

const X264_FINAL = ['-c:v', 'libx264', '-profile:v', 'high', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), ...OUT];
const SILENCE = ['-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo'];
const AAC = ['-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart'];

// ---------------------------------------------------------------------------------------------

async function main() {
	const name = `kolahal-${cut}`;
	fs.mkdirSync(outDir, { recursive: true });
	const srtFile = path.join(outDir, `${name}.srt`);
	fs.writeFileSync(srtFile, srt());

	console.log(`${cut} cut, ${theme}, ${only === 'whole' ? `${W} × ${H}` : only}: ${chapters.length} chapters, ${total} s.`);
	for (const w of warnings) console.log(`  note: ${w}`);
	if (checkOnly) {
		console.log(`Captions: ${srtFile}`);
		return;
	}
	console.log(capped ? `Each job capped at ${CAP} of memory, ${THREADS} threads.` : `No systemd-run: jobs run without a memory cap, ${THREADS} threads.`);

	const t0 = Date.now();
	const secs = () => ((Date.now() - t0) / 1000).toFixed(0);
	const todo = onlyChapter ? chapters.filter((c) => c.id === onlyChapter) : chapters;
	if (!todo.length) fail(`No chapter ${onlyChapter} in the ${cut} cut.`);
	// Sources and layers first, so everything Chrome draws is shot in one go.
	for (const c of todo) for (const p of c.pieces) source(p.id);
	const frameDark = layer({ kind: 'frame', tokens: 'dark' }, PH.w, PH.h);
	const mask = layer({ kind: 'mask' }, REC.width, REC.height);
	const layers = only === 'whole' ? todo.map(wholeLayers) : [];
	await shootLayers();
	if (missing.length) console.log(`Not recorded yet, so a plain stand-in: ${missing.join(', ')} (looked in ${recDir}).`);

	const clips = [];
	for (const c of todo) clips.push(await phoneClip(c));
	console.log(`Phone clips: ${secs()} s.`);

	let pieces = clips;
	let codec = X264_WORK;
	let ext = 'mp4';
	if (only === 'whole') {
		pieces = [];
		for (let i = 0; i < todo.length; i++) pieces.push(await wholeChapter(todo[i], clips[i], layers[i]));
	} else if (only === 'framed') {
		pieces = [];
		for (let i = 0; i < todo.length; i++) pieces.push(await framedChapter(todo[i], clips[i], frameDark, mask));
		codec = FFV1_WORK;
		ext = 'mkv';
	}
	console.log(`Chapters: ${secs()} s.`);
	const peak = () => `largest memory of a job ${(peakKiB / 1048576).toFixed(2)} GiB (${peakJob})`;
	if (onlyChapter) {
		console.log(`${onlyChapter}: ${Object.values(pieces[0].files).join(', ')}; ${peak()}.`);
		return;
	}

	const form = only === 'whole' ? `${k4 ? '-2160p' : ''}` : `-${only}`;
	const list = await join(pieces, codec, ext, path.join(work, `${name}${form}.txt`));
	let out;
	const input = [...IN, '-f', 'concat', '-safe', '0', '-i', list];
	if (only === 'framed') {
		out = path.join(outDir, `${name}-framed.webm`);
		await ffmpeg([...input, '-map', '0:v', '-t', r3(total), '-r', String(FPS),
			'-c:v', 'libvpx-vp9', '-pix_fmt', 'yuva420p', '-crf', '30', '-b:v', '0', '-row-mt', '1',
			'-deadline', 'good', '-cpu-used', '4', '-auto-alt-ref', '0', ...OUT, out], 'the framed video');
	} else {
		out = path.join(outDir, `${name}${form}.mp4`);
		await ffmpeg([...input, ...SILENCE, '-map', '0:v', '-map', '1:a', '-t', r3(total), ...X264_FINAL, ...AAC, out], 'the video');
	}
	const mb = fs.statSync(out).size / 1048576;
	console.log(`${out}: ${probeDuration(out).toFixed(2)} s, ${mb.toFixed(1)} MB, made in ${secs()} s; ${peak()}.`);
	console.log(`Captions: ${srtFile}`);
	if (missing.length) console.log(`Stand-ins for: ${missing.join(', ')}`);
}

main().catch((e) => fail(e.message));
