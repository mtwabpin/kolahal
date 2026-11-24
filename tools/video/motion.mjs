// The motion of the preview video: springs, the exit ease, and the expressions ffmpeg evaluates for
// them on each frame. compose.mjs takes the numbers from video.json → motion and builds every move
// out of the few kinds here, so a change to a spring changes every move made with it.
//
// A spring is a weight of mass 1 on a spring (stiffness k) with damping (c). Moved to a new place
// at once, it gets there along a closed formula, a damped cosine, so ffmpeg needs no simulation:
//
//   x(t) = 1 − e^(−ζω·t) · (cos(ω_d·t) + ζω/ω_d · sin(ω_d·t)),   ω = √k, ζ = c / 2ω, ω_d = ω·√(1 − ζ²)
//
// A spring is written down by what a person sees: how far it goes past its place (overshoot, a share
// of the move) and how long it takes to come within 1% of the move (settle, in seconds). Stiffness
// and damping follow from those two; `compose.sh --check` prints them.
//
// Moves add up: a value is its base plus every move made so far, each starting from 0. A spring
// moved again before it settles is the sum of both moves, which is what a real spring does.

const TOLERANCE = 0.01;

// The settle time of a spring of damping ratio z and ω = 1: when it last is more than TOLERANCE
// away from its place.
function unitSettle(z) {
	let last = 0;
	const dt = 0.001;
	for (let t = 0; t < 60; t += dt) if (Math.abs(stepUnit(z, t) - 1) > TOLERANCE) last = t;
	return last + dt;
}
function stepUnit(z, t) {
	if (z < 1) {
		const wd = Math.sqrt(1 - z * z);
		return 1 - Math.exp(-z * t) * (Math.cos(wd * t) + (z / wd) * Math.sin(wd * t));
	}
	return 1 - Math.exp(-t) * (1 + t);
}

// spring({overshoot, settle}): the spring with that overshoot (0 to 0.5; 0 is critical damping, no
// overshoot) and settle time in seconds.
export function spring({ overshoot, settle }) {
	if (!(overshoot >= 0 && overshoot < 0.5)) throw new Error(`A spring's overshoot is between 0 and 0.5, not ${overshoot}.`);
	if (!(settle > 0)) throw new Error(`A spring's settle time is more than 0 s, not ${settle}.`);
	const ln = Math.log(overshoot);
	const z = overshoot > 0 ? -ln / Math.sqrt(Math.PI ** 2 + ln * ln) : 1;
	const w = unitSettle(z) / settle;
	const a = z * w;
	const wd = z < 1 ? w * Math.sqrt(1 - z * z) : 0;
	// The kick (see below) is highest at tp.
	const tp = z < 1 ? Math.atan(wd / a) / wd : 1 / w;
	const impulse = (t) => (z < 1 ? (Math.exp(-a * t) * Math.sin(wd * t)) / wd : t * Math.exp(-w * t));
	const peak = impulse(tp);
	return {
		overshoot,
		settle,
		stiffness: w * w,
		damping: 2 * a,
		ratio: z,
		peakTime: tp,
		// Where a move of 1 is after t seconds.
		step: (t) => (t <= 0 ? 0 : z < 1 ? 1 - Math.exp(-a * t) * (Math.cos(wd * t) + (a / wd) * Math.sin(wd * t)) : 1 - Math.exp(-w * t) * (1 + w * t)),
		// A kick: the spring pushed from its place and coming back, at its farthest (1) after peakTime.
		kick: (t) => (t <= 0 ? 0 : impulse(t) / peak),
		// The same as ffmpeg expressions of T, which is never below 0.
		stepExpr: (T) =>
			z < 1
				? `(1-exp(-${n(a)}*${T})*(cos(${n(wd)}*${T})+${n(a / wd)}*sin(${n(wd)}*${T})))`
				: `(1-exp(-${n(w)}*${T})*(1+${n(w)}*${T}))`,
		kickExpr: (T) =>
			z < 1 ? `(exp(-${n(a)}*${T})*sin(${n(wd)}*${T})*${n(1 / (wd * peak))})` : `(${T}*exp(-${n(w)}*${T})*${n(1 / peak)})`,
	};
}

// A number as an ffmpeg expression reads it: no exponent, negatives in brackets.
export function n(x) {
	const s = String(Math.round(x * 1e5) / 1e5);
	return x < 0 ? `(${s})` : s;
}

// A value over time: { base, moves }. Each move starts at its time t and adds d:
//   spring  goes to d along a spring (s)
//   kick    goes out to d along a spring (s) and comes back to 0
//   exit    goes to d in `secs` seconds, slow at first and faster to the end (power `power`)
//   wave    goes to d in `secs` seconds, easing in and out (half a cosine): the ground's drift
export const track = (base = 0) => ({ base, moves: [] });

function moveAt(m, t) {
	const T = t - m.t;
	if (T <= 0) return 0;
	switch (m.kind) {
		case 'spring': return m.d * m.s.step(T);
		case 'kick': return m.d * m.s.kick(T);
		case 'exit': return m.d * Math.min(1, T / m.secs) ** m.power;
		case 'wave': return (m.d * (1 - Math.cos(Math.PI * Math.min(1, T / m.secs)))) / 2;
	}
	throw new Error(`No move of kind ${m.kind}.`);
}
// When a move is over: from then on it is d (or 0 for a kick). Springs count as over at 4 times
// their settle time, when they are far within a thousandth of the move.
const over = (m) => m.t + (m.kind === 'spring' || m.kind === 'kick' ? 4 * m.s.settle : m.secs);

export function valueAt(tr, t) {
	return tr.moves.reduce((v, m) => v + moveAt(m, t), tr.base);
}

// The track as an ffmpeg expression for the seconds [w0, w1] of the video, in a stream whose time
// `t` starts at w0. Moves over before w0 become a constant, moves after w1 are left out.
export function exprOf(tr, w0, w1, tv = 't') {
	let base = tr.base;
	const terms = [];
	for (const m of tr.moves) {
		if (m.t >= w1) continue;
		if (over(m) <= w0) {
			if (m.kind !== 'kick') base += m.d;
			continue;
		}
		const T = `max(${tv}-${n(m.t - w0)},0)`;
		switch (m.kind) {
			case 'spring': terms.push(`${n(m.d)}*${m.s.stepExpr(T)}`); break;
			case 'kick': terms.push(`${n(m.d)}*${m.s.kickExpr(T)}`); break;
			case 'exit': terms.push(`${n(m.d)}*pow(min(${T}/${n(m.secs)},1),${n(m.power)})`); break;
			case 'wave': terms.push(`${n(m.d / 2)}*(1-cos(PI*min(${T}/${n(m.secs)},1)))`); break;
		}
	}
	return [n(base), ...terms].join('+');
}

// Whether the track moves at all within [w0, w1].
export function moves(tr, w0, w1) {
	return tr.moves.some((m) => m.t < w1 && over(m) > w0);
}

// The smallest and largest value of a track over [t0, t1], sampled each frame.
export function range(tr, t0, t1, fps) {
	let lo = Infinity;
	let hi = -Infinity;
	for (let t = t0; t <= t1 + 1e-9; t += 1 / fps) {
		const v = valueAt(tr, t);
		lo = Math.min(lo, v);
		hi = Math.max(hi, v);
	}
	return [lo, hi];
}
