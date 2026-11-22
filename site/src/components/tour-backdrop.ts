/**
 * The home tour's background: Kolahal's log as faint text in the free space around the hero, the
 * beat cards and the phone, moving slowly with the scroll (parallax).
 *
 * - Lanes: the log sits in lanes placed where the page is empty (the gutters, the gap between the
 *   cards and the phone, the copy column). Lines that pass behind the hero, a card or the phone
 *   thin out, so foreground text stays easy to read.
 * - Blocks: each beat has a block of lines in each lane, placed so that it passes the screen while
 *   the beat is read. The current beat's block is soft in the accent.
 * - Decoding: a line that comes into view scrambles through random glyphs and locks into its real
 *   text, left to right. A new beat decodes its block as a cluster; on large screens a line now
 *   and then decodes again. Only a few lines decode at a time.
 * - Phone widths (under 860 px) do not load this file: HomeTour draws a few lines of the log before
 *   each beat instead. Reduced motion: no parallax,
 *   no drift, no decoding.
 *
 * Everything is driven from the tour's one animation frame loop ([Backdrop.tick]); a line is one
 * element whose text is set, never one element per character.
 */
import { BG_BLOCKS, type BgGroup } from './tour-background';

/** Letters, digits, the terminal's signs and a few katakana. */
const GLYPHS = 'abcdefghijklmnopqrstuvwxyz0123456789$=/|{}[]<>*#%+-ｱｳｶｸｻｼﾀﾂﾅﾆﾊﾐﾑﾓﾗﾘﾜ';
/** A glyph changes this often (ms). */
const STEP_MS = 50;
/** All glyphs scramble this long before the first one locks (ms). */
const SCRAMBLE_MS = 120;
/** How much slower than its own depth a lane moves: the parallax and the sway, for distance. */
const SLOW = 0.62;
/** The whole background leans by this much, and is drawn this much smaller: farther away. */
const TILT = (-4 * Math.PI) / 180;
const FAR = 0.94;

type Decode = { start: number; dur: number; key: string; pal: string };
/** The two themes a glitch borrows (the classes that set their colors). */
const PALETTES = ['g-amber', 'g-rose'];
type Line = {
	el: HTMLElement;
	text: string;
	id: number;
	lane: Lane;
	cmd: boolean;
	y: number;
	beat: number;
	seen: boolean;
	under: boolean;
	dec: Decode | null;
};
type Lane = { li: number; el: HTMLElement; x: number; w: number; f: number; fs: number; lh: number; amp: number; phase: number; off: number; lines: Line[] };

export type Backdrop = {
	/** Builds the lanes for the page as it is laid out now. */
	layout(small: boolean, navH: number): void;
	/** One frame. Returns true while it wants more frames (lines decoding or waiting to). */
	tick(now: number, sy: number, ch: number, rm: boolean): boolean;
};

export function createBackdrop(T: HTMLElement, host: HTMLElement): Backdrop {
	let lanes: Lane[] = [];
	/** The tilted layer: its place in the host, its scale, and the tilt's cosine and sine. */
	const geo = { lx: 0, ly: 0, cx: 0, cy: 0, s: 1, cos: 1, sin: 0 };
	/** A point of the layer, in the host's coordinates (after the tilt and the scale). */
	const toHost = (x: number, y: number) => {
		const px = geo.lx + x - geo.cx;
		const py = geo.ly + y - geo.cy;
		return [geo.cx + geo.s * (px * geo.cos - py * geo.sin), geo.cy + geo.s * (px * geo.sin + py * geo.cos)];
	};
	let all: Line[] = [];
	/** For each lane and beat, the output lines that didn't fit: an idle decode brings one in. */
	let spares: string[][][] = [];
	let small = false;
	let queue: Line[] = [];
	const active: Line[] = [];
	let lastCh = -1;
	let lastSy = -1;
	let lastScroll = 0;
	let lastNow = 0;
	let nextIdle = 0;
	/** The next glitch of a group or two (ms), and the glitches showing now. */
	let nextGlitch = 0;
	const glitches: { els: Element[]; cls: string[]; until: number }[] = [];
	let seed = 3;
	const rnd = () => ((seed = (seed * 16807) % 2147483647), seed / 2147483647);

	/** Where each beat starts to be read: the scroll position at which it becomes the current beat. */
	function beatStarts(navH: number) {
		const vh = innerHeight;
		const line = navH + (vh - navH) * (small ? 0.8 : 0.6);
		const beats = [...T.querySelectorAll<HTMLElement>('.t-beat')];
		const starts = [0, ...beats.map((b) => b.getBoundingClientRect().top + scrollY - line)];
		const last = beats[beats.length - 1].getBoundingClientRect();
		starts.push(last.bottom + scrollY - line);
		return starts;
	}

	function layout(isSmall: boolean, navH: number) {
		small = isSmall;
		host.textContent = '';
		lanes = [];
		all = [];
		queue = [];
		active.length = 0;
		const W = host.clientWidth;
		const H = host.clientHeight || innerHeight;
		if (!W) return;
		const hr = host.getBoundingClientRect();
		const rel = (r: DOMRect) => ({ l: r.left - hr.left, r: r.right - hr.left });
		const story = rel(T.querySelector('.t-story')!.getBoundingClientRect());
		const card = rel(T.querySelector('.t-card')!.getBoundingClientRect());
		const phone = rel(T.querySelector('.t-phone')!.getBoundingClientRect());
		const rail = innerWidth >= 1400 ? 120 : 16;

		// The layer: tilted and a little smaller, and large enough to cover the host at every corner.
		const sc = small ? 0.97 : FAR;
		const cos = Math.cos(TILT);
		const sin = Math.sin(Math.abs(TILT));
		const Wn = (W * cos + H * sin) / sc + 8;
		const Hn = (W * sin + H * cos) / sc + 8;
		Object.assign(geo, { lx: (W - Wn) / 2, ly: (H - Hn) / 2, cx: W / 2, cy: H / 2, s: sc, cos: Math.cos(TILT), sin: Math.sin(TILT) });
		const layer = document.createElement('div');
		layer.className = 't-bg-rot';
		layer.style.cssText = `left:${geo.lx}px;top:${geo.ly}px;width:${Wn}px;height:${Hn}px;transform:rotate(${TILT}rad) scale(${sc})`;
		host.appendChild(layer);
		// Host coordinates to the layer's, ignoring the small tilt: where to put a lane to see it at x.
		const lxOf = (x: number) => geo.cx + (x - geo.cx) / sc - geo.lx;
		const lyOf = (y: number) => geo.cy + (y - geo.cy) / sc - geo.ly;

		// The lanes: [left, right, parallax, font size]. Each keeps to the room it has.
		const specs: [number, number, number, number][] = small
			? [[12, W - 12, 0.08, 12]]
			: [
					[16, story.l - 28, 0.1, 12],
					[story.l, card.r, 0.12, 13],
					[card.r + 28, phone.l - 28, 0.14, 14],
					[phone.r + 28, W - rail, 0.09, 12],
				];
		const room = specs.filter(([l, r]) => r - l >= 110);

		// Each beat's groups, dealt to the lanes in turn, so no line shows twice.
		const pools: BgGroup[][][] = room.map(() => BG_BLOCKS.map(() => []));
		BG_BLOCKS.forEach((b, k) => b.groups.forEach((g, i) => pools[i % room.length][k].push(g)));

		const starts = beatStarts(navH);
		spares = room.map(() => BG_BLOCKS.map(() => []));
		let id = 0;
		room.forEach(([hl, hr2, depth, fs], li) => {
			const f = depth * SLOW;
			const l = lxOf(hl);
			const r = lxOf(hr2);
			const lh = Math.round(fs * 1.75);
			const cols = Math.floor((r - l) / (fs * 0.6)) - 1;
			const el = document.createElement('div');
			// The farthest lanes (the gutters) are a little soft on large screens.
			el.className = !small && depth <= 0.1 ? 't-lane t-far' : 't-lane';
			el.style.cssText = `left:${l}px;width:${r - l}px;font-size:${fs}px;line-height:${lh}px`;
			// Depth: a lane that moves less is farther away, so a little dimmer.
			el.style.setProperty('--lane', small ? '1' : (0.3 + depth * 5).toFixed(2));
			const lane: Lane = { li, el, x: l, w: r - l, f, fs, lh, amp: (10 + li * 6) * SLOW, phase: li * 1.7, off: 0, lines: [] };
			let y = Math.round(lyOf(H * 0.08));
			const base = lyOf(H * 0.3);
			let html = '';
			const placed: { g: BgGroup; y: number; beat: number }[] = [];
			const gap = Math.round(lh * 0.6);
			// Groups that had no room in their own beat's block: they fill a later block's free room.
			const left: BgGroup[] = [];
			BG_BLOCKS.forEach((_, k) => {
				// Beat k's block: from where its reading starts to where the next one's does. It is
				// filled to its end; the last group may be cut short, and its other lines wait for an
				// idle decode. The last block runs past the tour's end.
				const end = (k < BG_BLOCKS.length - 1 ? f * starts[k + 1] : f * starts[starts.length - 1] + H / sc) + base;
				const spare: string[] = [];
				const put = (g: BgGroup, beat: number) => {
					const rows = g.o.length + (g.c ? 1 : 0);
					const fit = Math.floor((end - y - gap) / lh);
					if (fit < Math.min(rows, 2)) return false;
					const o = rows <= fit ? g.o : g.o.slice(0, fit - (g.c ? 1 : 0));
					placed.push({ g: { ...g, o }, y, beat });
					y += (o.length + (g.c ? 1 : 0)) * lh + gap;
					if (beat === k) spare.push(...g.o.slice(o.length));
					return true;
				};
				const own = pools[li][k];
				let i = 0;
				for (; i < own.length && put(own[i], k); i++);
				left.push(...own.slice(i));
				while (left.length && end - y > lh * 2 && put(left[0], -1)) left.shift();
				spares[li][k] = spare;
				y = Math.max(y, end);
			});
			// One session down each lane: the times rise from group to group.
			let minute = 12 * 60 + 10 + li * 7;
			for (const { g, y: gy, beat } of placed) {
				html += `<div class="t-lg k-${g.k}" data-b="${beat}" style="top:${gy}px">`;
				if (g.c) {
					minute += 1 + (gy % 3 === 0 ? 1 : 0);
					const head = `$ ${g.c}`;
					const t = `${Math.floor(minute / 60) % 12 || 12}:${String(minute % 60).padStart(2, '0')}`;
					const text = head.length + t.length + 2 <= cols ? head.padEnd(cols - t.length) + t : head;
					html += `<span class="ln c">${esc(text)}</span>`;
				}
				for (const o of g.o) html += `<span class="ln">${esc(o)}</span>`;
				html += '</div>';
			}
			el.innerHTML = html;
			el.style.height = `${Math.ceil(y + Hn)}px`;
			layer.appendChild(el);
			// The lines and where they are in the lane (no measuring: every line is one line high).
			[...el.querySelectorAll<HTMLElement>('.t-lg')].forEach((gEl, gi) => {
				const { y: gy, beat } = placed[gi];
				[...gEl.children].forEach((ln, i) => {
					const line: Line = { el: ln as HTMLElement, text: ln.textContent || '', id: id++, lane, cmd: ln.classList.contains('c'), y: gy + i * lh, beat, seen: false, under: false, dec: null };
					lane.lines.push(line);
					all.push(line);
				});
			});
			lanes.push(lane);
		});
		lastCh = -1;
		glitches.length = 0;
	}

	/** The foreground that background lines thin out behind: the hero, the cards, the phone. */
	function obstacles(): DOMRect[] {
		const vh = innerHeight;
		const els = [T.querySelector('.t-intro'), ...T.querySelectorAll('.t-card'), T.querySelector(small ? '.t-stage' : '.t-phone')];
		return els
			.filter((e): e is Element => !!e)
			.map((e) => e.getBoundingClientRect())
			.filter((r) => r.bottom > 0 && r.top < vh && r.width > 0);
	}

	function start(l: Line, now: number) {
		// Now and then a decoding line flickers in amber or rose-pine before it settles in its own color.
		const pal = !small && rnd() < 0.2 ? PALETTES[Math.floor(rnd() * 2)] : '';
		l.dec = { start: now, dur: Math.min(900, Math.max(500, 420 + l.text.length * 9)), key: '', pal };
		l.el.classList.add('dc');
		if (pal) l.el.classList.add(pal);
		active.push(l);
	}

	function tick(now: number, sy: number, ch: number, rm: boolean): boolean {
		if (!lanes.length) return false;
		const dt = lastNow ? Math.min(0.05, (now - lastNow) / 1000) : 0;
		lastNow = now;
		if (sy !== lastSy) {
			lastSy = sy;
			lastScroll = now;
		}
		const vh = innerHeight;
		const hr = host.getBoundingClientRect();
		// The drift: a slow sway on large screens, paused while the page scrolls.
		const drifting = !small && !rm && now - lastScroll > 400;
		const obs = obstacles();
		const pad = 12;
		const fresh = ch !== lastCh;
		lastCh = ch;

		for (const lane of lanes) {
			if (drifting) lane.phase += dt * 0.16 * SLOW;
			const off = rm ? 0 : lane.f * sy - lane.amp * Math.sin(lane.phase);
			if (Math.abs(off - lane.off) > 0.05 || !lane.el.style.transform) {
				lane.off = off;
				lane.el.style.transform = `translate3d(0,${(-off).toFixed(1)}px,0)`;
			}
			const cw = lane.fs * 0.6;
			const half = (lane.lh * geo.s) / 2;
			for (const l of lane.lines) {
				// Where the line is on screen, through the tilt: its start and its end.
				const ym = l.y - lane.off + lane.lh / 2;
				const [ax, ay] = toHost(lane.x, ym);
				const [bx, by] = toHost(lane.x + Math.min(lane.w, l.text.length * cw), ym);
				const mid = hr.top + (ay + by) / 2;
				const shown = mid > hr.top - lane.lh && mid < Math.min(vh, hr.bottom) + lane.lh;
				if (!shown) {
					if (mid < hr.top - 140 || mid > vh + 140) l.seen = false;
					continue;
				}
				// Behind the foreground: thinner.
				const x0 = hr.left + Math.min(ax, bx);
				const x1 = hr.left + Math.max(ax, bx);
				const y0 = hr.top + Math.min(ay, by) - half;
				const y1 = hr.top + Math.max(ay, by) + half;
				const under = obs.some((r) => x1 > r.left - pad && x0 < r.right + pad && y1 > r.top - pad && y0 < r.bottom + pad);
				if (under !== l.under) {
					l.under = under;
					l.el.classList.toggle('u', under);
				}
				if (rm) continue;
				if (!l.seen) {
					l.seen = true;
					// A line that comes into view decodes; on phones, only some of them.
					if (!l.under && rnd() < (small ? 0.35 : 0.7)) queue.push(l);
				}
				// A new beat: its block decodes as a cluster.
				if (fresh && l.beat === ch && !l.dec && !l.under) queue.unshift(l);
			}
		}
		if (rm) return false;

		// Now and then, on large screens, a line decodes again.
		if (!small && now >= nextIdle) {
			nextIdle = now + 800 + rnd() * 700;
			const idle = all.filter((l) => l.seen && !l.under && !l.dec);
			if (idle.length) {
				// It decodes into its own text, or into a line of its beat that had no room yet.
				const l = idle[Math.floor(rnd() * idle.length)];
				const spare = spares[l.lane.li]?.[l.beat];
				if (!l.cmd && spare?.length && rnd() < 0.6) {
					spare.push(l.text);
					l.text = spare.shift()!;
				}
				queue.push(l);
			}
		}

		// Rarely, on large screens: a group or two glitches into amber or rose-pine. A burst jitters for
		// 150-300 ms; a wash lasts 1-2 s and fades back. Never behind the hero, a card or the phone.
		for (let i = glitches.length - 1; i >= 0; i--) {
			if (now < glitches[i].until) continue;
			for (const el of glitches[i].els) el.classList.remove(...glitches[i].cls);
			glitches.splice(i, 1);
		}
		if (!nextGlitch) nextGlitch = now + 4000 + rnd() * 4000;
		if (!small && now >= nextGlitch && !glitches.length) {
			nextGlitch = now + 6000 + rnd() * 6000;
			const clear = new Map<Element, boolean>();
			for (const l of all) {
				const g = l.el.parentElement!;
				clear.set(g, (clear.get(g) ?? true) && l.seen && !l.under);
			}
			const ok = [...clear].filter(([, v]) => v).map(([g]) => g);
			if (ok.length) {
				const first = ok[Math.floor(rnd() * ok.length)];
				const els = [first];
				for (let n = first.nextElementSibling; n && els.length < 3 && clear.get(n) && rnd() < 0.6; n = n.nextElementSibling) els.push(n);
				const pal = PALETTES[Math.floor(rnd() * 2)];
				const burst = rnd() < 0.55;
				const cls = burst ? [pal, 'jit'] : [pal];
				for (const el of els) el.classList.add(...cls);
				glitches.push({ els, cls, until: now + (burst ? 150 + rnd() * 150 : 1000 + rnd() * 1000) });
			}
		}

		const max = small ? 4 : 7;
		while (active.length < max && queue.length) {
			const l = queue.shift()!;
			if (!l.dec && l.seen && !l.under) start(l, now);
		}
		if (queue.length > 40) queue = queue.slice(0, 40);

		for (let i = active.length - 1; i >= 0; i--) {
			const l = active[i];
			const d = l.dec!;
			const t = now - d.start;
			if (t >= d.dur) {
				l.el.textContent = l.text;
				l.el.classList.remove('dc');
				if (d.pal) l.el.classList.remove(d.pal);
				l.dec = null;
				active.splice(i, 1);
				continue;
			}
			const step = Math.floor(t / STEP_MS);
			const locked = t < SCRAMBLE_MS ? 0 : Math.floor((l.text.length * (t - SCRAMBLE_MS)) / (d.dur - SCRAMBLE_MS));
			const key = `${step}:${locked}`;
			if (key === d.key) continue;
			d.key = key;
			let s = l.text.slice(0, locked);
			for (let j = locked; j < l.text.length; j++) {
				const c = l.text[j];
				const h = (Math.imul(j + 1, 2654435761) ^ Math.imul(step + 7, 40503) ^ Math.imul(l.id + 3, 9973)) >>> 0;
				s += c === ' ' ? ' ' : GLYPHS[h % GLYPHS.length];
			}
			l.el.textContent = s;
		}
		return active.length > 0 || queue.length > 0 || glitches.length > 0;
	}

	return { layout, tick };
}

function esc(s: string) {
	return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
