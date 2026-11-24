// Puts the preview video together from tools/video/video.json: run it through compose.sh, whose head
// says how. What it does, in order:
//
//  1. Reads the data file and checks it: each chapter's length against its cuts, the short cut's
//     segments against the full cut, and each caption's time on screen against its length (at
//     least 3 s, and about 15 characters a second).
//  2. Works out each chapter of the cut: the pieces of recordings it is made of, its captions and
//     its ground. Then the motion, in the video's own seconds (motion.mjs, numbers from
//     video.json → motion): where the phone grows for a close-up, where it lifts and lands, where
//     the ground drifts and slides, when each caption comes and goes.
//  3. Shoots the still layers it needs from cards.html with headless Chrome (cached by what they
//     show, under <out>/work/layers/).
//  4. Cuts each chapter's phone clip from its recordings (1080 × 2400, 30 frames a second), with a
//     plain stand-in screen for a recording not yet made. Each clip runs half a change (the
//     seconds of motion.change) past the chapter at either end, and is cut in up to three: the
//     change at its start (head), the rest (body), the change at its end (tail).
//  5. Builds the form asked for, one piece at a time, each a job of its own: a chapter's body, or
//     the change between two chapters (one second, centred on the cut, that reads the tail of one
//     clip and the head of the next):
//       whole    ground, phone in its frame, captions (H.264 in MP4; the default)
//       phone    the phone clips alone, upright at the recording's size, with plain cuts
//       framed   the phone in its frame with round corners and shadow, on a transparent ground,
//                with the phone's lift and landing but no close-ups (VP9 with alpha in WebM)
//     The join reads the pieces in order (ffmpeg's concat demuxer) and writes the video.
//  6. Writes the captions as an SRT file beside it, the same for every form.
//
// Memory: no job ever holds more than one chapter, or the one second where two meet. ffmpeg runs
// one at a time, with a bounded number of threads, and still pictures are looped only for the
// seconds they show. Where systemd-run is there, each ffmpeg and Chrome runs in a scope with a
// memory cap (VIDEO_MEMORY_MAX, 6G by default), so a job that grows is stopped alone; VIDEO_THREADS
// (4 by default) bounds the threads. The run prints the largest memory an ffmpeg job used.
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
//  - close: [[from, to], …] where the phone is shown motion.close.scale times larger: it starts
//    growing at from and is back at full size by to.
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spring, track, valueAt, exprOf, moves, range, n } from './motion.mjs';

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
const REC = data.recording;
const M = data.motion;
const springs = Object.fromEntries(Object.entries(M.springs).map(([k, v]) => [k, spring(v)]));
const CHANGE = M.change.seconds; // the seconds of a change between two chapters, centred on the cut

// The frame: 1920 × 1080, or 3840 × 2160 with --4k. Layout and motion in 1920 × 1080 pixels, times U.
const U = k4 ? 2 : 1;
const W = 1920 * U;
const H = 1080 * U;
// The phone's layers from cards.html (kinds frame and shadow): the frame at the recording's scale,
// with room for its shadow around it.
const PH = { w: 1554, h: 2854, frameX: 200, frameY: 40, frameW: 1154, frameH: 2474, screenX: 237, screenY: 77 };
const FCX = PH.frameX + PH.frameW / 2; // the frame's middle on its layer
const FCY = PH.frameY + PH.frameH / 2;
// The frame stands at the frame's full height less 80 px above and below (1840 of 2160 at 4K),
// its left edge a ninth of the way in.
const phoneH = H * 1840 / 2160;
const K = phoneH / PH.frameH;
const phoneCX = W / 9 + (PH.frameW * K) / 2;
// The captions' column, right of the phone (even at 1.5 times), and its two layers.
const COL = { x: 880, w: 960 };
const HEAD = { y: 0, h: 520 };
const TEXT = { y: 548, h: 492 };

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
// Every cut between two chapters is a change: half of it before the cut, half after, so each
// chapter's clip runs that far past it at either end.
chapters.forEach((c, i) => {
	c.pre = i > 0 ? CHANGE / 2 : 0;
	c.post = i + 1 < chapters.length ? CHANGE / 2 : 0;
	c.dur = c.pre + c.seconds + c.post;
	// A change where the ground changes too is a change of scene.
	c.newScene = i > 0 && chapters[i - 1].ground !== c.ground;
	if (c.seconds < 2 * CHANGE) warnings.add(`${c.id}: ${c.seconds} s is too short for a change of ${CHANGE} s at either end.`);
});
let clock = 0;
for (const c of chapters) {
	c.start = clock;
	clock += c.seconds;
	c.end = clock;
}
const total = clock;

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
// 2. The motion, in the video's seconds. Distances are pixels of a 1920 × 1080 frame.

const SP = springs.phone;
const EXIT = M.exit;

// The phone: zoom goes from 0 (full size) to 1 (a close-up); lift is 0 where the phone rests and 1
// where it is lifted off the ground (lower, a little larger, its shadow wider and further down).
const zoom = track(0);
const lift = track(1);
lift.moves.push({ kind: 'spring', s: SP, t: M.phone.start, d: -1 });
for (const c of chapters) {
	for (const [from, to] of c.close) {
		const a = c.start + from;
		const b = Math.max(a + SP.settle, c.start + to - SP.settle);
		zoom.moves.push({ kind: 'spring', s: SP, t: a, d: 1 }, { kind: 'spring', s: SP, t: b, d: -1 });
	}
	const kick = c.newScene ? M.change.sceneKick : c.pre ? M.change.kick : 0;
	// Lifted most on the cut itself, where the screen changes.
	if (kick) lift.moves.push({ kind: 'kick', s: SP, t: c.start - SP.peakTime, d: kick });
}
// The close-up grows the phone about one point of its screen (motion.close.anchor, as shares of
// the screen's width and height), which stays where it is.
const CS = M.close.scale;
const anchor = { x: PH.screenX + M.close.anchor[0] * REC.width, y: PH.screenY + M.close.anchor[1] * REC.height };

// The ground: one track for each run of chapters on the same ground, the left edge of what shows
// of its wide picture. It drifts slowly over the run (easing in and out), nudges on at each cut
// inside the run, and at a change of scene the old ground slides on as the new one slides in, both
// on the same spring, while the new one fades in over the old.
const runs = [];
for (const c of chapters) {
	if (runs.length && runs.at(-1).ground === c.ground) runs.at(-1).chapters.push(c);
	else runs.push({ ground: c.ground, chapters: [c] });
}
const G = M.ground;
runs.forEach((run, r) => {
	const first = run.chapters[0];
	const last = run.chapters.at(-1);
	const x = track(0);
	if (r > 0) x.moves.push({ kind: 'spring', s: springs.ground, t: first.start - G.lead, d: G.slide });
	x.moves.push({ kind: 'wave', t: first.start, secs: last.end - first.start, d: G.pan * 1920 * (last.end - first.start) });
	for (const c of run.chapters.slice(1)) x.moves.push({ kind: 'spring', s: springs.ground, t: c.start - G.lead, d: G.nudge });
	if (r + 1 < runs.length) x.moves.push({ kind: 'spring', s: springs.ground, t: last.end - G.lead, d: G.slide });
	run.x = x;
	[run.lo, run.hi] = range(x, first.start - first.pre, last.end + last.post, FPS);
	for (const c of run.chapters) c.run = run;
});

// The words of chapter c: each part of each caption with the seconds it comes in (tin) and starts
// to leave (tout; null for the closing card, which stays to the end). The label, the heading and
// the first line come one after the other, a stagger apart, once the chapter has started (at the
// opening, once the phone is on its way); all of them leave together before the chapter ends.
function wordsOf(c, i) {
	const g = data.grounds[c.ground];
	const st = M.stagger;
	const a0 = c.start + (i === 0 ? M.phone.start + M.words.opening : M.words.delay);
	const end = i + 1 < chapters.length ? c.end - EXIT.seconds : null;
	const out = [];
	const add = (spec, w, h, y, tin, tout) => out.push({ png: layer({ tokens: g.tokens, ...spec }, w, h, U), y, tin, tout });
	if (c.ch.card) {
		const parts = c.ch.card.line ? ['heading', 'text', 'line'] : ['heading', 'text'];
		parts.forEach((part, k) => add({ kind: 'card', ...c.ch.card, part }, COL.w, 1080, 0, a0 + k * st, end));
	}
	if (c.ch.head) ['label', 'heading'].forEach((part, k) => add({ kind: 'head', ...c.ch.head, part }, COL.w, HEAD.h, HEAD.y, a0 + k * st, end));
	c.captions.forEach((cap, k) => {
		const tin = k === 0 ? a0 + (c.ch.head ? 2 * st : 0) : c.start + cap.at;
		const tout = k + 1 < c.captions.length ? c.start + cap.to - EXIT.seconds : end;
		const spec = { kind: 'text', text: cap.text, note: cap.note };
		add({ ...spec, part: cap.note ? 'text' : undefined }, COL.w, TEXT.h, TEXT.y, tin, tout);
		if (cap.note) add({ ...spec, part: 'note' }, COL.w, TEXT.h, TEXT.y, tin + st, tout);
	});
	for (const w of out) {
		// From motion.words.rise below its place, up into it on the words' spring, and away
		// upwards by exitRise as it leaves.
		w.y0 = w.y;
		w.dy = track(M.words.rise);
		w.dy.moves.push({ kind: 'spring', s: springs.words, t: w.tin, d: -M.words.rise });
		if (w.tout !== null) w.dy.moves.push({ kind: 'exit', t: w.tout, secs: EXIT.seconds, power: EXIT.power, d: -M.words.exitRise });
		w.from = w.tin;
		w.to = w.tout === null ? total : w.tout + EXIT.seconds;
	}
	return out;
}

// What --check prints about the motion.
function motionNotes() {
	const lines = Object.entries(springs).map(([k, s]) =>
		`  spring ${k}: overshoot ${(s.overshoot * 100).toFixed(1)}%, settles in ${s.settle} s ` +
		`(stiffness ${s.stiffness.toFixed(1)}, damping ${s.damping.toFixed(2)}, mass 1; ratio ${s.ratio.toFixed(3)})`);
	lines.push(`  exit: ${EXIT.seconds} s, eased in to the power ${EXIT.power}; stagger ${M.stagger} s; a change ${CHANGE} s`);
	return lines.join('\n');
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
// Pieces: a chapter's clip cut where its changes begin and end.

// The parts of chapter c's clip: [name, from, to] in its own seconds.
function parts(c) {
	const p = [];
	if (c.pre) p.push(['head', 0, 2 * c.pre]);
	p.push(['body', 2 * c.pre, c.dur - 2 * c.post]);
	if (c.post) p.push(['tail', c.dur - 2 * c.post, c.dur]);
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

// The phone clip of chapter c, as pieces; returns {files: {head?, body, tail?}}.
async function phoneClip(c) {
	const base = path.join(work, cut, `${c.id}.phone`);
	// Half a change more at either end, from the recording around the cut where there is any.
	const pieces = c.pieces.map((p) => ({ ...p }));
	pieces[0].in -= c.pre;
	pieces.at(-1).out += c.post;
	const sig = JSON.stringify([pieces, parts(c), pieces.map((p) => {
		const s = source(p.id);
		return s.real ? [s.file, mtime(s.file)] : [s.still];
	})]);
	const out = split(c, '[v]', base, X264_WORK, 'mp4');
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
// 5. The forms, one piece at a time.

// The pieces of the joined video in order: each chapter's body, and between two chapters their
// change. w0 and w1 are its seconds in the video.
function pieceList() {
	const list = [];
	chapters.forEach((c, i) => {
		list.push({ kind: 'body', c, w0: c.start + c.pre, w1: c.end - c.post, name: c.id });
		const next = chapters[i + 1];
		if (next) list.push({ kind: 'change', a: c, c: next, w0: c.end - CHANGE / 2, w1: c.end + CHANGE / 2, name: `${c.id}+${next.id}` });
	});
	return list;
}

// Filters with expressions: commas escaped for the filter graph, the whole in quotes.
const q = (e) => `'${String(e).replace(/,/g, '\\,')}'`;
// A still picture as a stream, for `len` seconds only.
const still = (i, len) => `[${i}:v]loop=loop=-1:size=1,setpts=N/${FPS}/TB,trim=duration=${r3(len)}`;

// A graph under construction: its inputs and filters.
function graph() {
	const g = { args: [], f: [], n: 0 };
	g.input = (...a) => {
		g.args.push(...a);
		return g.n++;
	};
	return g;
}

// The phone for piece p into graph g: its frame with the clip in it, and its shadow, at the
// recording's scale ([phone] and [shadow]). In a change, the next chapter's phone fades in over
// the one before it for motion.change.screen seconds about the cut. frames(c) and shadows(c)
// name each chapter's layers.
function phoneLayers(g, p, clips, frameOf, shadowOf) {
	const D = r3(p.w1 - p.w0);
	const sides = p.kind === 'body'
		? [{ c: p.c, file: clips.get(p.c.id).files.body }]
		: [{ c: p.a, file: clips.get(p.a.id).files.tail }, { c: p.c, file: clips.get(p.c.id).files.head }];
	const mask = g.input('-i', layerMask);
	g.f.push(sides.length > 1 ? `[${mask}:v]format=gray,split=2[m0][m1]` : `[${mask}:v]format=gray[m0]`);
	sides.forEach((s, k) => {
		const clip = g.input(...IN, '-i', s.file);
		const frame = g.input('-i', frameOf(s.c));
		const shadow = g.input('-i', shadowOf(s.c));
		g.f.push(`[${clip}:v]format=yuv420p,setpts=PTS-STARTPTS[rec${k}]`);
		g.f.push(`[rec${k}][m${k}]alphamerge[scr${k}]`);
		g.f.push(`${still(frame, D)},format=rgba[fr${k}]`);
		g.f.push(`[fr${k}][scr${k}]overlay=${PH.screenX}:${PH.screenY}:format=rgb:eof_action=endall[ph${k}]`);
		g.f.push(`${still(shadow, D)},format=rgba[sh${k}]`);
	});
	let phone = '[ph0]';
	let shadow = '[sh0]';
	if (sides.length > 1) {
		const fade = `fade=t=in:st=${r3(p.c.start - M.change.screen / 2 - p.w0)}:d=${M.change.screen}:alpha=1`;
		g.f.push(`[ph1]${fade}[ph1f]`, `[ph0][ph1f]overlay=0:0:format=rgb[phx]`);
		g.f.push(`[sh1]${fade}[sh1f]`, `[sh0][sh1f]overlay=0:0:format=rgb[shx]`);
		phone = '[phx]';
		shadow = '[shx]';
	}
	// At the very start the phone fades in as it rises.
	if (p.w0 <= M.phone.start && M.phone.start < p.w1) {
		const fade = `fade=t=in:st=${r3(M.phone.start - p.w0)}:d=${M.phone.fade}:alpha=1`;
		g.f.push(`${phone}${fade}[phy]`, `${shadow}${fade}[shy]`);
		phone = '[phy]';
		shadow = '[shy]';
	}
	g.f.push(`${phone}null[phone]`, `${shadow}null[shadow]`);
}

// The phone's place for piece p: expressions of t (the piece's own seconds) for its scale (of the
// layer's pixels), the middle of its frame (cx, cy), and its shadow's scale and middle; or numbers
// where nothing moves within the piece. base: the scale at rest; mid: the frame's middle at rest;
// px: pixels of the picture per pixel of a 1920 × 1080 frame; close: whether close-ups show.
function phonePlace(p, { base, mid, px, close }) {
	const z = close && moves(zoom, p.w0, p.w1);
	const l = moves(lift, p.w0, p.w1);
	const Z = !close ? '0' : z ? `(${exprOf(zoom, p.w0, p.w1)})` : n(valueAt(zoom, p.w0));
	const L = l ? `(${exprOf(lift, p.w0, p.w1)})` : n(valueAt(lift, p.w0));
	const P = M.phone;
	const s = `${n(base)}*(1+${n(CS - 1)}*${Z})*(1+${n(P.grow)}*${L})`;
	// Grown about the anchor: the frame's middle moves so the anchor stays.
	const cx = `${n(mid.x)}+${n((anchor.x - FCX) * base * (1 - CS))}*${Z}`;
	const cy = `${n(mid.y)}+${n((anchor.y - FCY) * base * (1 - CS))}*${Z}+${n(P.rise * px)}*${L}`;
	const ss = `(${s})*(1+${n(P.shadowGrow)}*${L})`;
	const scy = `${cy}+${n(P.shadowDrop * px)}*${L}`;
	return { moving: z || l, s, cx, cy, ss, scy };
}
// The filters that scale the layer `inp` by `s` and lay it over `under` with its frame's middle
// at (cx, cy), into `out`.
function placed(g, inp, under, out, s, cx, cy, moving, fmt) {
	const sw = moving ? `w=${q(`trunc(${PH.w}*${s}/2)*2`)}:h=${q(`trunc(${PH.h}*${s}/2)*2`)}:eval=frame`
		: `${Math.round((PH.w * eval0(s)) / 2) * 2}:${Math.round((PH.h * eval0(s)) / 2) * 2}`;
	g.f.push(`${inp}scale=${sw}:flags=bicubic,format=${fmt === 'rgb' ? 'rgba' : 'yuva420p'}${inp.replace(']', 's]')}`);
	const pos = moving ? `x=${q(`${cx}-${FCX}*w/${PH.w}`)}:y=${q(`${cy}-${FCY}*h/${PH.h}`)}:eval=frame`
		: `x=${Math.round(eval0(cx) - (FCX * Math.round((PH.w * eval0(s)) / 2) * 2) / PH.w)}:y=${Math.round(eval0(cy) - (FCY * Math.round((PH.h * eval0(s)) / 2) * 2) / PH.h)}`;
	g.f.push(`${under}${inp.replace(']', 's]')}overlay=${pos}:format=${fmt}${out}`);
}
// A constant expression (no t) worked out.
function eval0(e) {
	if (/\bt\b/.test(e)) throw new Error(`Not a constant: ${e}`);
	return Function(`return (${e});`)();
}

let layerMask;

// The whole picture of piece p: ground, phone, captions.
async function wholePiece(p, clips, words) {
	const dir = path.join(work, `${cut}-${H}p`);
	const file = path.join(dir, `${p.name}.mp4`);
	const D = r3(p.w1 - p.w0);
	const g = graph();
	// The grounds: one, or the two of a change of scene.
	const rs = p.kind === 'body' ? [p.c.run] : p.a.run === p.c.run ? [p.a.run] : [p.a.run, p.c.run];
	rs.forEach((run, k) => {
		const gi = g.input('-i', run.layer);
		const wi = g.input('-i', run.wash);
		const x = moves(run.x, p.w0, p.w1) ? exprOf(run.x, p.w0, p.w1) : n(valueAt(run.x, p.w0));
		g.f.push(`${still(gi, D)},format=rgb24,crop=${W}:${H}:x=${q(`clip(${U}*(${x}-${n(run.lo)}),0,iw-ow)`)}:y=0[gr${k}]`);
		g.f.push(`${still(wi, D)},format=rgba[wa${k}]`);
		g.f.push(`[gr${k}][wa${k}]overlay=0:0:format=rgb[gw${k}]`);
	});
	if (rs.length > 1) {
		g.f.push(`[gw1]format=rgba,fade=t=in:st=${r3(p.c.start - G.lead - p.w0)}:d=${G.fade}:alpha=1[gf1]`);
		g.f.push(`[gw0][gf1]overlay=0:0:format=rgb[g]`);
	} else g.f.push(`[gw0]null[g]`);
	// The phone.
	phoneLayers(g, p, clips, (c) => c.frame, (c) => c.shadow);
	const at = phonePlace(p, { base: K, mid: { x: phoneCX, y: H / 2 }, px: U, close: true });
	placed(g, '[shadow]', '[g]', '[v0]', at.ss, at.cx, at.scy, at.moving, 'yuv420');
	placed(g, '[phone]', '[v0]', '[v1]', at.s, at.cx, at.cy, at.moving, 'yuv420');
	// The captions that show within the piece, each looped only for its seconds, faded in and
	// out and moved on its track.
	let v = 1;
	for (const w of words) {
		if (w.to <= p.w0 || w.from >= p.w1) continue;
		const i = g.input('-i', w.png);
		const len = r3(w.to - w.from);
		const fades = [`fade=t=in:st=0:d=${M.words.fade}:alpha=1`];
		if (w.tout !== null) fades.push(`fade=t=out:st=${r3(w.tout - w.from)}:d=${EXIT.seconds}:alpha=1`);
		const a = r3(Math.max(0, p.w0 - w.from));
		const b = r3(Math.min(w.to, p.w1) - w.from);
		g.f.push(`${still(i, len)},format=yuva420p,${fades.join(',')},trim=start=${a}:end=${b},` +
			`setpts=PTS-STARTPTS+${r3(Math.max(0, w.from - p.w0))}/TB[w${v}]`);
		const y = moves(w.dy, p.w0, p.w1)
			? `y=${q(`${w.y0 * U}+${U}*(${exprOf(w.dy, p.w0, p.w1)})`)}:eval=frame`
			: `y=${Math.round(w.y0 * U + U * valueAt(w.dy, p.w0))}`;
		g.f.push(`[v${v}][w${v}]overlay=x=${COL.x * U}:${y}:eof_action=pass:format=yuv420[v${v + 1}]`);
		v++;
	}
	g.f.push(`[v${v}]format=yuv420p,trim=duration=${D}[out]`);
	return render(g, file, X264_WORK, p, clips, `${p.name} at ${W} × ${H}`);
}

// The phone alone in its frame on a transparent ground of the frame layer's size, with its lift
// and landing (no close-ups: they are a matter of the 16:9 picture).
async function framedPiece(p, clips) {
	const dir = path.join(work, `${cut}-framed`);
	const file = path.join(dir, `${p.name}.mkv`);
	const D = r3(p.w1 - p.w0);
	const g = graph();
	g.f.push(`color=c=black@0:s=${PH.w}x${PH.h}:r=${FPS}:d=${D},format=rgba[g]`);
	phoneLayers(g, p, clips, () => frameDark, () => shadowDark);
	const at = phonePlace(p, { base: 1, mid: { x: FCX, y: FCY }, px: PH.frameH / (1080 * 1840 / 2160), close: false });
	placed(g, '[shadow]', '[g]', '[v0]', at.ss, at.cx, at.scy, at.moving, 'rgb');
	placed(g, '[phone]', '[v0]', '[v1]', at.s, at.cx, at.cy, at.moving, 'rgb');
	g.f.push(`[v1]format=yuva420p,trim=duration=${D}[out]`);
	return render(g, file, FFV1_WORK, p, clips, `${p.name} framed`);
}

// The phone clips alone: a body is the clip's own body; a change is a plain cut on the cut.
async function phonePiece(p, clips) {
	if (p.kind === 'body') return clips.get(p.c.id).files.body;
	const file = path.join(work, cut, `${p.name}.phone.mp4`);
	const g = graph();
	const a = g.input(...IN, '-i', clips.get(p.a.id).files.tail);
	const b = g.input(...IN, '-i', clips.get(p.c.id).files.head);
	const h = r3(CHANGE / 2);
	g.f.push(`[${a}:v]trim=end=${h},setpts=PTS-STARTPTS[a]`, `[${b}:v]trim=start=${h},setpts=PTS-STARTPTS[b]`, `[a][b]concat=n=2:v=1:a=0[out]`);
	return render(g, file, X264_WORK, p, clips, `${p.name} cut`);
}

// Runs graph g into file unless an earlier run made it from the same things.
async function render(g, file, codec, p, clips, what) {
	const ids = p.kind === 'body' ? [p.c.id] : [p.a.id, p.c.id];
	const sig = JSON.stringify([g.args, g.f, ids.map((id) => mtime(clips.get(id).files.body))]);
	if (fresh(file, sig)) return file;
	fs.mkdirSync(path.dirname(file), { recursive: true });
	await ffmpeg([...g.args, '-filter_complex', g.f.join(';'), '-map', '[out]', ...codec, file], what);
	mark(file, sig);
	return file;
}

const X264_FINAL = ['-c:v', 'libx264', '-profile:v', 'high', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), ...OUT];
const SILENCE = ['-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo'];
const AAC = ['-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart'];

let frameDark;
let shadowDark;

// ---------------------------------------------------------------------------------------------

async function main() {
	const name = `kolahal-${cut}`;
	fs.mkdirSync(outDir, { recursive: true });
	const srtFile = path.join(outDir, `${name}.srt`);
	fs.writeFileSync(srtFile, srt());

	console.log(`${cut} cut, ${theme}, ${only === 'whole' ? `${W} × ${H}` : only}: ${chapters.length} chapters, ${total} s.`);
	for (const w of warnings) console.log(`  note: ${w}`);
	if (checkOnly) {
		console.log(`Motion (video.json → motion):\n${motionNotes()}`);
		console.log(`Captions: ${srtFile}`);
		return;
	}
	console.log(capped ? `Each job capped at ${CAP} of memory, ${THREADS} threads.` : `No systemd-run: jobs run without a memory cap, ${THREADS} threads.`);

	const t0 = Date.now();
	const secs = () => ((Date.now() - t0) / 1000).toFixed(0);
	let pieces = pieceList();
	if (onlyChapter) {
		pieces = pieces.filter((p) => p.kind === 'body' && p.c.id === onlyChapter);
		if (!pieces.length) fail(`No chapter ${onlyChapter} in the ${cut} cut.`);
	}
	const todo = chapters.filter((c) => pieces.some((p) => p.c === c || p.a === c));
	// Sources and layers first, so everything Chrome draws is shot in one go.
	for (const c of todo) for (const p of c.pieces) source(p.id);
	layerMask = layer({ kind: 'mask' }, REC.width, REC.height);
	frameDark = layer({ kind: 'frame', tokens: 'dark' }, PH.w, PH.h);
	shadowDark = layer({ kind: 'shadow', tokens: 'dark' }, PH.w, PH.h);
	const words = new Map();
	if (only === 'whole') {
		for (const c of todo) {
			const g = data.grounds[c.ground];
			c.frame = layer({ kind: 'frame', tokens: g.tokens }, PH.w, PH.h);
			c.shadow = layer({ kind: 'shadow', tokens: g.tokens }, PH.w, PH.h);
			const run = c.run;
			if (!run.layer) {
				const scene = g.scene ? `file://${path.join(sceneDir, g.scene)}` : null;
				const gw = Math.round((1920 + (run.hi - run.lo) + 4) / 2) * 2; // CSS pixels
				run.layer = layer({ kind: 'ground', tokens: g.tokens, scene }, gw, 1080, U);
				run.wash = layer({ kind: 'wash', tokens: g.tokens, scene }, 1920, 1080, U);
			}
			words.set(c.id, wordsOf(c, chapters.indexOf(c)));
		}
	}
	await shootLayers();
	if (missing.length) console.log(`Not recorded yet, so a plain stand-in: ${missing.join(', ')} (looked in ${recDir}).`);

	const clips = new Map();
	for (const c of todo) clips.set(c.id, await phoneClip(c));
	console.log(`Phone clips: ${secs()} s.`);

	const files = [];
	for (const p of pieces) {
		if (only === 'whole') {
			const ws = p.kind === 'body' ? words.get(p.c.id) : [...words.get(p.a.id), ...words.get(p.c.id)];
			files.push(await wholePiece(p, clips, ws));
		} else if (only === 'framed') files.push(await framedPiece(p, clips));
		else files.push(await phonePiece(p, clips));
	}
	console.log(`Pieces: ${secs()} s.`);
	const peak = () => `largest memory of a job ${(peakKiB / 1048576).toFixed(2)} GiB (${peakJob})`;
	if (onlyChapter) {
		console.log(`${onlyChapter}: ${files.join(', ')}; ${peak()}.`);
		return;
	}

	const form = only === 'whole' ? `${k4 ? '-2160p' : ''}` : `-${only}`;
	const list = concatList(path.join(work, `${name}${form}.txt`), files);
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
