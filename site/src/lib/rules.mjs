// Copied from mtwabpin/kolahal-scripts (0.5.23, 591b251ec2f5), tools/rules.mjs, by scripts/copy-rules.mjs: change it there.
// The rules a Kolahal script (Lua or JavaScript) or shader (AGSL) is checked by, as plain functions of its
// text: no file system, no compiler, no dependencies. It runs in Node and in a browser.
//
// - tools/check.mjs checks every file of this repository with it, and writes index.json.
// - Kolahal's website copies it for its Script check page (site/src/lib/rules.mjs in the app's repository,
//   with the commit it came from beside it). After a change here, run `node site/scripts/copy-rules.mjs`
//   there. The website's build fails while its copy differs from this branch's file.
//
// Two sets of rules, each issue saying which it is from:
// - "kolahal": what Kolahal's own editor says when a script is saved (ScriptCheck), and what keeps a shader
//   from being used (ShaderMeta.problems and .warnings), in the app's words. A script is saved either way.
//   With them, the few features that an older Kolahal fails at without a word ([olderKolahalIssues]).
// - "library": what this repository asks of a script or shader it shares: a full header, the @requires that
//   newer features need, a size that can be read, and so on.
// Syntax isn't checked here: check.mjs has luac and Node for that, the website Lua 5.4 and acorn.
//
// An issue is { line, text, level, rules }: its line, counted from 1; its words, a short first line with any
// hints under it, one to a line ("\n"); "error" or "warning"; "kolahal" or "library".
// tools/examples.mjs has sample scripts and what each gets, and tools/rules.test.mjs checks them.

// ---- what Kolahal reads -------------------------------------------------------------------------------

// As Kolahal's ScriptMeta.USES: what `@uses` may name.
export const USES = ['notifications', 'clipboard', 'apps', 'commands', 'dnd', 'work', 'todos', 'notes', 'reminders', 'calendar', 'feeds', 'usage', 'screen', 'effects'];
// As Kolahal's ScriptMeta.EVENTS: what `@on` may name.
export const EVENTS = ['unlock', 'charger', 'unplug', 'wifi', 'offline', 'notification', 'boot', 'headset'];
// As Kolahal's ScriptCheck.DIRECTIVES: every `@` line a script's header may have, in its order.
export const DIRECTIVES = ['command', 'desc', 'usage', 'on', 'uses', 'secret', 'option', 'title', 'author', 'version', 'tags', 'every', 'at', 'notify', 'requires', 'widget', 'gives'];
// As Kolahal's ScriptCheck.SCREEN_REFUSES: a script with a screen of its own runs only when you start it.
export const SCREEN_REFUSES = ['every', 'at', 'on', 'widget'];
// As Kolahal's ScriptMeta.PACKAGE: an app's package name after `@on notification`.
const PACKAGE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;
// As Kolahal's ScriptCheck.NEEDS: calls Kolahal stops unless the script declares them with `@uses`.
export const NEEDS = [
  ['notifications', /(?<![.\w])notifications\s*\.\s*recent\b/],
  ['clipboard', /(?<![.\w])clipboard\s*\.\s*\w+/],
  ['apps', /(?<![.\w])apps\s*\.\s*list\b/],
  ['commands', /(?<![.\w])launcher\s*\.\s*run\b/],
  ['dnd', /(?<![.\w])device\s*\.\s*(dnd|setDnd)\b/],
  ['work', /(?<![.\w])device\s*\.\s*(work|setWork)\b/],
  ['todos', /(?<![.\w])todos\s*\.\s*(list|setDone)\b/],
  ['notes', /(?<![.\w])notes\s*\.\s*list\b/],
  ['reminders', /(?<![.\w])reminders\s*\.\s*list\b/],
  ['calendar', /(?<![.\w])calendar\s*\.\s*events\b/],
  ['feeds', /(?<![.\w])feeds\s*\.\s*unread\b/],
  ['usage', /(?<![.\w])usage\s*\.\s*(today|days|data|summary|unused)\b/],
  ['screen', /(?<![.\w])tty\s*\.\s*screen\b/],
  ['effects', /(?<![.\w])shader\s*\.\s*play\b/],
];
// As Kolahal's ScriptAccess.label: what the access switches are called.
export const ACCESS_LABELS = {
  internet: 'Internet', notifications: 'Your notifications', apps: 'Your app list', clipboard: 'Clipboard',
  commands: 'Run commands', dnd: 'Do Not Disturb', work: 'Work profile', todos: 'Your to-dos', notes: 'Your notes',
  reminders: 'Your reminders', calendar: 'Your calendar', feeds: 'Your feeds', usage: 'Your app usage',
  screen: 'Full screen', effects: 'Effects',
};
// As Kolahal's WidgetKind.forScripts, in its order, and WidgetSize.
export const WIDGET_KINDS = ['stat', 'text', 'list', 'progress', 'chart', 'countdown', 'buttons', 'toggles', 'clock', 'agenda', 'image', 'versus', 'sky'];
export const WIDGET_SIZES = ['1x1', '2x1', '2x2', '4x1', '4x2', '4x4'];
// As Kolahal's WidgetKind.size: the size a kind gets when its `@widget` line names none.
export const WIDGET_SIZE_OF = {
  stat: '2x1', text: '2x2', list: '4x2', progress: '2x1', chart: '2x2', countdown: '2x1', buttons: '4x1',
  toggles: '4x1', clock: '2x1', agenda: '4x2', image: '2x2', versus: '4x1', sky: '2x2',
};
// As Kolahal's WidgetKind.sizes: the sizes each kind is made for. Kolahal takes any of WIDGET_SIZES on the
// line, so another one is a warning, not an error.
export const WIDGET_SIZES_OF = {
  stat: ['1x1', '2x1', '2x2', '4x1'],
  text: ['2x1', '2x2', '4x1', '4x2'],
  list: ['2x2', '4x2', '4x4'],
  progress: ['1x1', '2x1', '2x2', '4x1', '4x2'],
  chart: ['2x1', '2x2', '4x2'],
  countdown: ['1x1', '2x1', '2x2'],
  buttons: ['2x1', '2x2', '4x1', '4x2'],
  toggles: ['2x1', '2x2', '4x1'],
  clock: ['2x1', '2x2', '4x1'],
  agenda: ['2x2', '4x2', '4x4'],
  image: ['2x1', '2x2', '4x2', '4x4'],
  versus: ['2x1', '2x2', '4x1', '4x2'],
  sky: ['2x1', '2x2'],
};
// As Kolahal's ShaderValues: a value's name on a `@gives` line, and how many a script may give.
const GIVES_NAME = /^[a-z][a-z0-9_-]{0,19}$/;
const GIVES_MAX = 8;
// As Kolahal's CommandParsing.MAX_INTERVAL_MIN: a week.
const MAX_INTERVAL = 7 * 24 * 60;

// ---- what the library asks for ------------------------------------------------------------------------

export const NAME = /^[a-z0-9_-]{1,40}$/;
// As Kolahal 0.5.17's CommandCatalog.RESERVED_NAMES: the built-in commands and their other names. Kolahal
// doesn't install a script of one of these names. Each version branch has the list of its Kolahal.
export const RESERVED = [
  'activity', 'agenda', 'alarm', 'alarms', 'alias', 'allow', 'apps', 'backup', 'bluetooth', 'boot', 'brightness',
  'bt', 'cal', 'call', 'calls', 'clear', 'clock', 'cls', 'config', 'contact', 'contacts', 'crashes', 'data', 'date',
  'decode', 'dial', 'dnd', 'echo', 'edit', 'fastfetch', 'fav', 'favs', 'feed', 'feeds', 'fetch', 'font', 'fresh', 'g',
  'help', 'hide', 'history', 'home', 'info', 'installs', 'js', 'launch', 'lock', 'log', 'ls', 'lua', 'map', 'maps',
  'matrix', 'media', 'mp', 'neofetch', 'next', 'nfc', 'note', 'notes', 'notifications', 'notifs', 'open', 'pin',
  'pins', 'pipes', 'play', 'prefs', 'prev', 'private', 'reboot', 'remind', 'reminders', 'rename', 'restart', 'ring',
  'rotate', 'rss', 'scramble', 'screen', 'screen-timeout', 'screentime', 'script', 'scripts', 'search', 'set',
  'settings', 'setup', 'shade', 'shader', 'shortcuts', 'sl', 'sms', 'source', 'status', 'stopwatch', 'storage',
  'store', 'system', 'text', 'theme', 'time', 'timer', 'todo', 'toggles', 'top', 'torch', 'tour', 'unalias', 'unfav',
  'unhide', 'uninstall', 'unlocks', 'unpin', 'update', 'updates', 'url', 'usage', 'vol', 'volume', 'wallpaper', 'web',
  'widget', 'widgets', 'wifi', 'wiki', 'work', 'youtube', 'yt',
];
const VERSION = /^\d+(\.\d+){0,2}$/;
export const MAX_BYTES = 64 * 1024;
const MAX_LINE = 300; // longer lines are usually minified code, which nobody can review
const REQUIRED = ['title', 'desc', 'author', 'version'];
// As Kolahal's ScriptMeta.secrets: `@secret NAME site[,site] description`.
const SECRET_NAME = /^[A-Za-z][A-Za-z0-9_]{0,39}$/;
const HOST = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;
// As Kolahal's ScriptMeta.options: `@option name default description`, read as options.name.
const OPTION_NAME = /^[A-Za-z][A-Za-z0-9_]{0,31}$/;
// The Kolahal each feature came in: an older one stops the script with an error, or reads the line wrong.
const SINCE = {
  secrets: '0.4.0', // @secret, secrets.get
  styles: '0.4.1', // fmt.*, md.*, logs()
  device: '0.4.4', // @uses dnd and work
  options: '0.4.7', // @option, options.*
  screen: '0.5.17', // @uses screen and effects, @gives, and widgets' versus and sky (below)
  notifyApps: '0.5.18', // @on notification com.example.chat: an older one runs it for every app's
  webOpen: '0.5.20', // web.open
  requestTimeout: '0.5.23', // http.request's timeout: an older Kolahal stops a request after 30 s
  widgetFields: '0.5.22', // the widget fields of WIDGET_FIELDS_NEW
  versusFields: '0.5.23', // the versus fields of VERSUS_FIELDS_NEW
};
// Widgets of Kolahal 0.5.17: the versus and sky kinds, a progress widget's gauge, rings and stages, and a
// stat's scene, each found in the code with its kind or field. A stat's `points` and a ring's `unit` and
// `moving` are 0.5.17's too, but their names are also older fields' (a chart's points), so they aren't looked for.
const NOT_DECLARED = '(?<!\\b(?:local|const|let|var)\\s+)(?<![.\\w$])["\']?';
const WIDGETS_NEW = [
  [new RegExp(`${NOT_DECLARED}kind["']?\\s*[:=]\\s*["'\`]\\s*versus\\s*["'\`]`, 'i'), 'a versus widget'],
  [new RegExp(`${NOT_DECLARED}kind["']?\\s*[:=]\\s*["'\`]\\s*sky\\s*["'\`]`, 'i'), 'a sky widget'],
  [new RegExp(`${NOT_DECLARED}style["']?\\s*[:=]\\s*["'\`]\\s*(gauge|rings)\\s*["'\`]`, 'i'), 'a progress widget\'s gauge or rings'],
  [new RegExp(`${NOT_DECLARED}stages["']?\\s*[:=](?!=)`), 'a progress widget\'s stages'],
  [new RegExp(`${NOT_DECLARED}scene["']?\\s*[:=](?!=)`), 'a stat\'s scene'],
];
// Widgets of Kolahal 0.5.22: a footer on every kind, a badge on every kind but buttons and toggles (a versus
// card had one before), a stat's target, a progress widget's details and a versus side's detail, each found in
// the code with its field, in a script whose @widget line names a kind that takes it, as their names are also
// common words (counter's target) or older fields' (a list row's detail). A button's and a toggle's `value` are
// 0.5.22's too, but the name is also older fields' (a stat's value), so they aren't looked for.
const WIDGET_FIELDS_NEW = [
  [new RegExp(`${NOT_DECLARED}footer["']?\\s*[:=](?!=)`), 'a footer', WIDGET_KINDS],
  [new RegExp(`${NOT_DECLARED}badge["']?\\s*[:=](?!=)`), 'a badge', WIDGET_KINDS.filter((k) => !['buttons', 'toggles', 'versus'].includes(k))],
  [new RegExp(`${NOT_DECLARED}target["']?\\s*[:=](?!=)`), 'a stat\'s target', ['stat']],
  [new RegExp(`${NOT_DECLARED}details["']?\\s*[:=](?!=)`), 'a progress widget\'s details', ['progress']],
  [new RegExp(`${NOT_DECLARED}detail["']?\\s*[:=](?!=)`), 'a versus side\'s detail', ['versus']],
];
// Widgets of Kolahal 0.5.23: a versus side's clock and lines, each found in the code with its field, in a script
// whose @widget line names versus. A side's own `score` is 0.5.23's too, but the name is also the card's score's,
// so it isn't looked for.
const VERSUS_FIELDS_NEW = [
  [new RegExp(`${NOT_DECLARED}clock["']?\\s*[:=](?!=)`), 'a versus side\'s clock', ['versus']],
  [new RegExp(`${NOT_DECLARED}lines["']?\\s*[:=](?!=)`), 'a versus side\'s lines', ['versus']],
];

// ---- shaders (AGSL, Kolahal 0.5.17 and newer) -----------------------------------------------------------

// As Kolahal's ShaderCatalog: a shader's name is one word at the prompt, and none of the `shader` command's
// own words (WORDS). Those that come with Kolahal (BUNDLED) can't be shared under their names, and
// `template-…` is for the templates it has (TEMPLATES).
const SHADER_NAME = /^[a-z0-9][a-z0-9_-]{0,39}$/;
export const SHADER_WORDS = ['ls', 'use', 'on', 'off', 'set', 'reset', 'follow', 'values', 'play', 'words', 'get', 'check', 'reload', 'save', 'edit', 'rm', 'delete'];
export const SHADER_BUNDLED = [
  'crt', 'lcd', 'eink', 'paper', 'riso', 'graph', 'foil', 'lamp', 'dusk', 'frost',
  'mountains', 'pond', 'dunes', 'rain', 'snowfall', 'grove-snow', 'grove-autumn', 'aurora', 'night', 'fireflies',
  'contour', 'ink', 'aura', 'day-ring', 'lanterns', 'kintsugi', 'record', 'candle', 'hourglass', 'windows',
  'seismograph', 'hearts', 'hearts-fountain', 'hearts-falling', 'hearts-stream', 'hearts-poppers', 'hearts-fireworks',
  'hearts-bounce', 'pulse', 'comet', 'breath', 'scan-line', 'spill', 'trace', 'northern-lights',
];
const SHADERS_SINCE = '0.5.17';
// As Kolahal's ShaderMeta: how many numbers each type holds, and the uniforms Kolahal sets (KNOWN), with theirs.
const FLOATS = {
  float: 1, half: 1, float2: 2, half2: 2, vec2: 2, float3: 3, half3: 3, vec3: 3, float4: 4, half4: 4, vec4: 4,
};
export const SHADER_COLORS = ['bg', 'surface', 'fg', 'dim', 'accent', 'error', 'warn', 'good', 'info', 'link'];
export const UNIFORMS = {
  resolution: 2, cell: 2, grid: 2, origin: 2, time: 1, touch: 2, touchAge: 1, dark: 1, date: 4, tilt: 2,
  pressed: 1, battery: 1, charging: 1, progress: 1, wallpaper: 1, corner: 1, cutout: 4, seed: 1,
  ...Object.fromEntries(SHADER_COLORS.map((c) => [c, 4])),
};
const SHADER_REQUIRED = [['name', 'title'], ['description', 'desc'], ['author'], ['version'], ['license'], ['requires']];
// As Kolahal's ShaderParam: `// @param amount 0.0..2.0 = 1.0 Title [@follow value]` or `shape a|b = a Title`.
export const PARAMS_MAX = 8;
const PARAM_CHOICES_MAX = 8;
const PARAM_LINE = /^([A-Za-z_]\w{0,39})\s+(\S+)\s*=\s*(\S+)\s*(.*)$/;
const PARAM_FOLLOW = /(?:^|\s+)@follow\s+(\S.*?)\s*$/;
const PARAM_RANGE = /^(-?\d+(?:\.\d+)?)\.\.(-?\d+(?:\.\d+)?)$/;
const PARAM_CHOICE = /^[A-Za-z0-9][A-Za-z0-9-]{0,19}$/;
// As Kotlin's String.toFloatOrNull, for the numbers a header has.
const FLOAT = /^[-+]?(NaN|Infinity|(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?[fFdD]?)$/;
const NUMBER = /^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?$/;
// As Kolahal's ShaderValues and UsageValues: the values a parameter can follow. Kolahal's own are one word,
// or one of its usage values; a script's is `<script>.<name>`, from the script's @gives line.
const USAGE_VALUES = [
  'unlocks', 'unlocks.usual', ...[1, 2, 3, 4, 5, 6, 7].map((d) => `unlocks.d${d}`),
  'screen', 'screen.usual', ...[1, 2, 3, 4, 5, 6, 7].map((d) => `screen.d${d}`),
  ...[1, 2, 3, 4, 5, 6, 7, 8].map((h) => `screen.h${h}`), 'away',
];
const OWN_VALUES = ['battery', 'charging', 'day', 'night', ...USAGE_VALUES];
const VALUE_GROUPS = ['unlocks', 'screen'];
const VALUE_ID = /[a-z0-9_][a-z0-9_-]*\.[a-z][a-z0-9_-]*|[a-z][a-z0-9_]*/g;
// As Kolahal's ShaderClock.FPS and IDLE_MS, and ShaderMeta.MIN_SETTLES and MAX_STILL.
const FPS_MAX = 30;
const SETTLES = [0.5, 60];
const STILL_MAX = 120;

// ---- helpers ------------------------------------------------------------------------------------------

/** The lines of [code], as Kotlin's String.lines() splits them. */
const linesOf = (code) => code.split(/\r\n|\n|\r/);
/** One `@` line: its comment mark, its name and what follows. */
const DIRECTIVE_LINE = /^\s*(--|\/\/)\s*@(\w+)\b[ \t]*(.*)$/;
const issue = (line, text, rules, level = 'error') => ({ line, text, level, rules });

/** As Kolahal's CommandParsing.editDistance: Levenshtein's. */
export function editDistance(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

/** As Kolahal's CommandParsing.parseInterval: minutes, 0 for off, or null. */
export function parseInterval(s) {
  const w = (s ?? '').toLowerCase();
  if (w === 'off' || w === '0' || w === 'manual') return 0;
  const m = /^(\d+)([mhd]?)$/.exec(w);
  if (!m) return null;
  const n = Number(m[1]) * ({ h: 60, d: 1440 }[m[2]] ?? 1);
  return n <= MAX_INTERVAL ? n : null;
}

/** As Kolahal's ScriptScheduler.normalizeTime: "8:00" gives "08:00"; null for anything that isn't a time. */
export function normalizeTime(raw) {
  const parts = raw.trim().split(':');
  if (parts.length !== 2 || !parts.every((p) => /^[-+]?\d{1,9}$/.test(p))) return null;
  const [h, m] = parts.map(Number);
  if (!(h >= 0 && h <= 23 && m >= 0 && m <= 59)) return null;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** As Kolahal's ScriptMeta.usesNames: the names on one `@uses` (or `@gives`) line, after it. */
const usesNames = (rest) => rest.split(/[, \t]/).map((s) => s.trim().toLowerCase()).filter(Boolean);

/** The text after the first `@<name>` line, trimmed, or null when there is none or it is empty (ScriptMeta.value). */
export function value(code, name) {
  const m = code.match(new RegExp(`^\\s*(--|//)\\s*@${name}\\b[ \\t]*(.*)$`, 'm'));
  return m && m[2].trim() ? m[2].trim() : null;
}
/** The text after every `@<name>` line, with the line of each. */
function every(code, name) {
  const out = [];
  linesOf(code).forEach((line, i) => {
    const m = DIRECTIVE_LINE.exec(line);
    if (m && m[2] === name) out.push({ line: i + 1, rest: m[3] });
  });
  return out;
}
const has = (code, name) => new RegExp(`^\\s*(--|//)\\s*@${name}\\b`, 'm').test(code);
/** The line of the first `@<name>` line, or 1. */
const lineOf = (code, name) => every(code, name)[0]?.line ?? 1;
/** The line that [offset] of [text] is on. */
const lineAt = (text, offset) => text.slice(0, offset).split('\n').length;

/** True when version a is older than b ("0.3.1" < "0.4.0"). */
export function older(a, b) {
  const x = a.split('.').map(Number);
  const y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) < (y[i] ?? 0);
  return false;
}

/** Bytes of [code] as UTF-8. */
const sizeOf = (code) => new TextEncoder().encode(code).length;

/** Lines broken at \v, \f, U+0085, U+2028, U+2029 or a lone \r, which Kolahal (Android) breaks at too. */
function oddBreaks(code, rules) {
  const m = /[\v\f\u0085\u2028\u2029]|\r(?!\n)/.exec(code);
  if (!m) return [];
  return [issue(code.slice(0, m.index).split(/\r\n|\n|\r/).length, 'A line break the library doesn\'t take\nOnly \\n or \\r\\n: no \\v, \\f, U+0085, U+2028, U+2029 or a lone \\r', rules)];
}

/** Lines longer than MAX_LINE characters, and a file over MAX_BYTES. */
function sizes(code, bytes, rules) {
  const out = [];
  if (bytes > MAX_BYTES) out.push(issue(1, `The file is ${bytes} bytes\nThe library takes at most ${MAX_BYTES}`, rules));
  code.split('\n').forEach((line, i) => {
    if (line.length > MAX_LINE) out.push(issue(i + 1, `Line ${i + 1} is ${line.length} characters\nAt most ${MAX_LINE}, so it can be read`, rules));
  });
  return out;
}

// ---- scripts --------------------------------------------------------------------------------------------

/** The language of a script or shader: from its file's name when it has one, else from its text. */
export function languageOf(fileName, code = '') {
  const ext = /\.([A-Za-z]+)$/.exec(fileName ?? '')?.[1]?.toLowerCase();
  if (ext === 'lua') return 'lua';
  if (ext === 'js' || ext === 'mjs') return 'js';
  if (ext === 'agsl' || ext === 'sksl') return 'agsl';
  if (/\buniform\s+shader\b|\bhalf4\s+main\s*\(/.test(code)) return 'agsl';
  const marks = [...code.matchAll(/^\s*(--|\/\/)\s*@\w+/gm)].map((m) => m[1]);
  if (marks.length) return marks.filter((m) => m === '--').length >= marks.length / 2 ? 'lua' : 'js';
  if (/^\s*local\s|\bend\s*$|\bthen\b|~=|\.\./m.test(code)) return 'lua';
  return /\b(const|let|function|=>)\b|;\s*$/m.test(code) ? 'js' : 'lua';
}

/** As Kolahal's ScriptMeta.uses: the known names of every `@uses` line, in USES order. */
export const usesOf = (code) => {
  const named = new Set([...code.matchAll(/^\s*(--|\/\/)\s*@uses\b(.*)$/gm)].flatMap((m) => usesNames(m[2])));
  return USES.filter((u) => named.has(u));
};
/** As Kolahal's ScriptMeta.isScreen: it says `@uses screen`. */
const isScreen = (code) => usesOf(code).includes('screen');

/**
 * As Kolahal's ScriptMeta.readOn: the text after `@on`. Commas and spaces separate events. The words after
 * `notification`, up to the next comma or event, are the package names of the apps it is for. A word with a dot
 * at the start of a later comma part is one too, of the last `notification`.
 */
export function readOn(rest) {
  const events = [];
  const problems = [];
  let last = null;
  for (const part of rest.split(',')) {
    let apps = null;
    let lead = true;
    for (const word of part.split(/\s+/).filter(Boolean)) {
      const name = word.toLowerCase();
      if (lead && !EVENTS.includes(name) && last !== null && word.includes('.')) apps = last;
      if (EVENTS.includes(name)) {
        lead = false;
        apps = name === 'notification' ? [] : null;
        if (name === 'notification') last = apps;
        events.push([name, apps ?? []]);
      } else if (apps !== null && word.includes('.')) {
        apps.push(word);
        if (!PACKAGE.test(word)) problems.push([word, `@on notification ${word} isn't a package name: letters, digits, _ and dots, such as com.example.chat`]);
      } else if (apps !== null) {
        apps.push(word);
        problems.push([word, `@on ${word} isn't an event or a package name: ${EVENTS.join(', ')}`]);
      } else if (PACKAGE.test(word)) {
        problems.push([word, `@on ${word}: only notification takes package names, as in @on notification ${word}`]);
      } else {
        problems.push([word, `@on ${word} isn't an event: ${EVENTS.join(', ')}`]);
      }
    }
  }
  return { events, problems };
}

/** As Kolahal's ScriptCheck.widgetIssue: what is wrong with the text after `@widget`, or null. */
function widgetIssue(rest) {
  const words = rest.trim().split(/\s+/);
  const kind = words[0];
  const size = words[1];
  if (!kind) return `@widget needs a kind: ${WIDGET_KINDS.join(', ')}`;
  if (!WIDGET_KINDS.includes(kind.toLowerCase())) return `@widget ${kind} isn't a kind: ${WIDGET_KINDS.join(', ')}`;
  if (size !== undefined && !WIDGET_SIZES.includes(size.toLowerCase().replace('×', 'x'))) return `@widget ${kind} ${size} isn't a size: ${WIDGET_SIZES.join(', ')}`;
  return null;
}

/** The code with its comment lines emptied, as ScriptCheck reads it: a note about `http.get` is not a call. */
const bodyOf = (code, lang) => {
  const comment = lang === 'lua' ? '--' : '//';
  return linesOf(code).map((line) => (line.trimStart().startsWith(comment) ? '' : line)).join('\n');
};

/**
 * What Kolahal's editor says about [code] when it is saved (ScriptCheck.check), in its words. [api]: the API's
 * names in this language, { names, namespaces }, as the website keeps them (api-names.json); without it, calls
 * aren't checked against the API.
 */
function editorIssues(code, lang, api) {
  const lines = linesOf(code);
  const out = [];
  const add = (line, text) => out.push(issue(line, text, 'kolahal'));
  const comment = lang === 'lua' ? '--' : '//';
  lines.forEach((line, i) => {
    const m = DIRECTIVE_LINE.exec(line);
    if (!m) return;
    const [, , name, rest] = m;
    if (!DIRECTIVES.includes(name)) {
      const near = DIRECTIVES.find((d) => editDistance(d, name) <= 2);
      return add(i + 1, `Unknown directive @${name}${near ? `: @${near}?` : ''}`);
    }
    if (name === 'every' && parseInterval(rest.trim()) === null) add(i + 1, `@every ${rest.trim()} isn't an interval: 15m, 1h, 1d or off`);
    if (name === 'at') {
      const bad = rest.split(/[, ]/).filter((t) => t.trim()).find((t) => normalizeTime(t) === null);
      if (bad !== undefined) add(i + 1, `@at ${bad} isn't a time: HH:mm, such as 07:30`);
    }
    if (name === 'on') {
      const problem = readOn(rest).problems[0];
      if (problem) add(i + 1, problem[1]);
    }
    if (name === 'uses') {
      const bad = usesNames(rest).find((u) => !USES.includes(u));
      if (bad) add(i + 1, `@uses ${bad} isn't valid: ${USES.join(', ')}`);
    }
    if (name === 'widget') {
      const problem = widgetIssue(rest);
      if (problem) add(i + 1, problem);
    }
    if (name === 'gives') {
      const bad = usesNames(rest).find((g) => !GIVES_NAME.test(g));
      if (bad) add(i + 1, `@gives ${bad} isn't a name: small letters, digits, - and _, at most 20`);
    }
  });

  // A script with a screen of its own is a command only: these lines count for nothing in it.
  if (isScreen(code)) {
    lines.forEach((line, i) => {
      const m = DIRECTIVE_LINE.exec(line);
      if (!m) return;
      const off = m[2] === 'every' && parseInterval(m[3].trim()) === 0;
      if (SCREEN_REFUSES.includes(m[2]) && !off) add(i + 1, `@${m[2]} doesn't work with @uses screen: a script that draws a full screen runs only when you start it`);
    });
  }

  const body = bodyOf(code, lang);
  const uses = usesOf(code);
  for (const [use, call] of NEEDS) {
    if (uses.includes(use)) continue;
    const hit = call.exec(body);
    if (hit) add(lineAt(body, hit.index), `${hit[0].replace(/\s/g, '')} needs a \`${comment} @uses ${use}\` line`);
  }

  if (api) {
    const names = new Set(api.names);
    const sorted = [...names].sort();
    for (const ns of api.namespaces) {
      // A script that makes its own table of that name fills it, so it is skipped.
      if (new RegExp(`(?<![.\\w])(local\\s+|let\\s+|const\\s+|var\\s+)?${ns}\\s*=[^=]`).test(body)) continue;
      for (const hit of body.matchAll(new RegExp(`(?<![.\\w])${ns}\\s*\\.\\s*([A-Za-z_]\\w*)`, 'g'))) {
        const full = `${ns}.${hit[1]}`;
        if (names.has(full)) continue;
        let near = null;
        let best = Infinity;
        for (const n of sorted) {
          if (!n.startsWith(`${ns}.`)) continue;
          const d = editDistance(n, full);
          if (d < best) [near, best] = [n, d];
        }
        add(lineAt(body, hit.index), `${full} isn't in the API${near && best <= 2 ? `: ${near}?` : ''}`);
      }
    }
  }
  const seen = new Set();
  return out.filter((x) => !seen.has(`${x.line}\n${x.text}`) && seen.add(`${x.line}\n${x.text}`))
    .sort((a, b) => a.line - b.line);
}

/** What this repository asks of a script it shares, besides what Kolahal's editor says ([editorIssues]). */
/**
 * The arguments of each call that [call] finds in [body]: from the bracket after its name (a Lua call may
 * take a table without parentheses, `http.request{ … }`) to the one that closes it. Strings are skipped.
 */
function callArguments(body, call) {
  const out = [];
  for (const m of body.matchAll(call)) {
    const start = m.index + m[0].length - 1;
    let depth = 0;
    let quote = null;
    let i = start;
    for (; i < body.length; i++) {
      const c = body[i];
      if (quote) {
        if (c === '\\') i++;
        else if (c === quote) quote = null;
      } else if (c === '"' || c === "'" || c === '`') quote = c;
      else if ('({['.includes(c)) depth++;
      else if (')}]'.includes(c) && --depth === 0) break;
    }
    out.push({ index: m.index, text: body.slice(start, i + 1) });
  }
  return out;
}

/**
 * Features that an older Kolahal fails at without saying why, checked for the phone as for the library: each
 * needs a `@requires` of the Kolahal it came in.
 * - `http.request` with a `timeout` (0.5.23, up to 60 seconds): an older one stops a request after 30
 *   seconds all the same.
 */
function olderKolahalIssues(code, lang) {
  const out = [];
  const requires = value(code, 'requires');
  const since = (v) => requires && VERSION.test(requires) && !older(requires, v);
  const comment = lang === 'lua' ? '--' : '//';
  const body = bodyOf(code, lang);
  const timeout = callArguments(body, /(?<![.\w])http\s*\.\s*request\s*[({]/g)
    .find(({ text }) => /(?<![.\w$])["']?timeout["']?\s*[:=](?!=)/.test(text));
  if (timeout && !since(SINCE.requestTimeout)) {
    out.push(issue(lineAt(body, timeout.index), `http.request's timeout needs \`${comment} @requires ${SINCE.requestTimeout}\` or newer\nAn older Kolahal stops a request after 30 s`, 'kolahal'));
  }
  return out;
}

function libraryIssues(name, code, lang) {
  const out = [];
  const add = (line, text, level) => out.push(issue(line, text, 'library', level));
  const body = bodyOf(code, lang);
  const firstHit = (re) => { const m = re.exec(body); return m ? lineAt(body, m.index) : null; };

  if (name !== undefined && name !== null) {
    if (!NAME.test(name)) add(1, `${name} isn't a name the library takes\nSmall letters, digits, - and _, at most 40`);
    if (RESERVED.includes(name)) add(1, `${name} is a Kolahal command\nKolahal doesn't install a script of that name`);
  }
  out.push(...oddBreaks(code, 'library'));
  const missing = REQUIRED.filter((n) => !value(code, n));
  if (missing.length) add(1, `No ${missing.map((n) => `@${n}`).join(', ')} line${missing.length > 1 ? 's' : ''}\nThe library needs @title, @desc, @author and @version`);
  const version = value(code, 'version');
  if (version && !VERSION.test(version)) add(lineOf(code, 'version'), `@version ${version} isn't a version: numbers like 1.2`);
  const requires = value(code, 'requires');
  if (requires && !VERSION.test(requires)) add(lineOf(code, 'requires'), `@requires ${requires} isn't a Kolahal version: such as 0.4.0`);
  // A feature of a newer Kolahal than the script's @requires says.
  const since = (version) => requires && VERSION.test(requires) && !older(requires, version);
  const needs = (line, what, version) => {
    if (!since(version)) add(requires ? lineOf(code, 'requires') : line, `${what}, so it needs \`${lang === 'lua' ? '--' : '//'} @requires ${version}\` or newer`);
  };

  const uses = usesOf(code);
  const onLines = every(code, 'on').map(({ line, rest }) => ({ line, ...readOn(rest) }));
  const appsLine = onLines.find((o) => o.events.some(([e, apps]) => e === 'notification' && apps.some((a) => PACKAGE.test(a))));
  if (appsLine) needs(appsLine.line, '@on notification names apps', SINCE.notifyApps);
  const onNotification = onLines.find((o) => o.events.some(([e]) => e === 'notification'));
  if (onNotification && !uses.includes('notifications')) add(onNotification.line, '@on notification needs a `@uses notifications` line');

  // Values for shaders: at most GIVES_MAX, and each one shader.give names declared.
  const gives = every(code, 'gives').flatMap(({ rest }) => usesNames(rest));
  if (new Set(gives).size > GIVES_MAX) add(lineOf(code, 'gives'), `@gives names ${new Set(gives).size} values\nAt most ${GIVES_MAX}`);
  for (const m of body.matchAll(/(?<![.\w])shader\s*\.\s*give\s*\(\s*["'`]([^"'`]*)["'`]/g)) {
    if (!gives.includes(m[1].toLowerCase())) add(lineAt(body, m.index), `shader.give("${m[1]}") needs a \`@gives ${m[1].toLowerCase()}\` line`);
  }
  const giveLine = firstHit(/(?<![.\w])shader\s*\.\s*give\b/);
  if (giveLine && !gives.length) add(giveLine, 'shader.give needs a @gives line');
  if (uses.includes('screen') || uses.includes('effects') || has(code, 'gives')) {
    needs(lineOf(code, uses.includes('screen') || uses.includes('effects') ? 'uses' : 'gives'), 'It uses screen, effects or @gives', SINCE.screen);
  }

  // The first @widget line: a size the kind isn't made for, and the newer widgets' kinds and fields.
  const widget = every(code, 'widget')[0];
  if (widget) {
    const [kind = '', size] = widget.rest.trim().split(/\s+/);
    const k = kind.toLowerCase();
    const s = (size ?? '').toLowerCase().replace('×', 'x');
    if (WIDGET_KINDS.includes(k)) {
      if (size && WIDGET_SIZES.includes(s) && !WIDGET_SIZES_OF[k].includes(s)) {
        add(widget.line, `@widget ${kind} ${size}: ${k} is made for ${WIDGET_SIZES_OF[k].join(', ')}\nKolahal draws it at ${s} all the same`, 'warning');
      }
      const found = [
        ...(['versus', 'sky'].includes(k) ? [`a ${k} widget`] : []),
        ...WIDGETS_NEW.filter(([re]) => re.test(body)).map(([, what]) => what),
      ];
      if (found.length) needs(widget.line, `It uses ${[...new Set(found)].join(', ')}`, SINCE.screen);
      const added = WIDGET_FIELDS_NEW.filter(([re, , kinds]) => kinds.includes(k) && re.test(body)).map(([, what]) => what);
      if (added.length) needs(widget.line, `It uses ${added.join(', ')}`, SINCE.widgetFields);
      const versus = VERSUS_FIELDS_NEW.filter(([re, , kinds]) => kinds.includes(k) && re.test(body)).map(([, what]) => what);
      if (versus.length) needs(widget.line, `It uses ${versus.join(', ')}`, SINCE.versusFields);
    }
  }

  // API keys: named with @secret, sent only to a site it names, read with secrets.get.
  const secrets = every(code, 'secret').map(({ line, rest }) => ({ line, words: rest.trim().split(/\s+/) }));
  for (const { line, words: [secret = '', hosts = '', ...desc] } of secrets) {
    if (!SECRET_NAME.test(secret)) add(line, `@secret ${secret} isn't a name: letters, digits and _, up to 40, such as OWM_KEY`);
    const sites = hosts.toLowerCase().split(',').filter(Boolean);
    if (!sites.length || sites.some((h) => !HOST.test(h))) add(line, `@secret ${secret} needs the site it is sent to\nSuch as api.example.com`);
    if (!desc.length) add(line, `@secret ${secret} needs a few words after the site\nWhat it is and where to get one`);
  }
  const declared = secrets.map(({ words }) => words[0]);
  for (const m of code.matchAll(/\bsecrets\.get\s*\(\s*["'`]([^"'`]*)["'`]/g)) {
    if (!declared.includes(m[1])) add(lineAt(code, m.index), `secrets.get("${m[1]}") needs a \`@secret ${m[1]} <site> <description>\` line`);
  }
  const secretsLine = secrets[0]?.line ?? (/\bsecrets\.get\s*\(/.test(code) ? lineAt(code, code.search(/\bsecrets\.get\s*\(/)) : null);
  if (secretsLine) needs(secretsLine, 'It uses secrets', SINCE.secrets);
  const styles = code.search(/\b(fmt|md)\.\w+\s*\(|\blogs\s*\(/);
  if (styles >= 0) needs(lineAt(code, styles), 'It uses fmt, md or logs', SINCE.styles);
  if (uses.includes('dnd') || uses.includes('work')) needs(lineOf(code, 'uses'), 'It uses dnd or work', SINCE.device);

  // Settings: each named once, with a default and a description; each one the code reads declared (a
  // misspelt name reads as nil).
  const options = every(code, 'option').map(({ line, rest }) => ({ line, m: /^(\S*)\s*("[^"]*"|\S*)\s*(.*)$/.exec(rest.trim()) }));
  const optionNames = [];
  for (const { line, m: [, option, def, desc] } of options) {
    if (!OPTION_NAME.test(option)) add(line, `@option ${option} isn't a name: letters, digits and _, up to 32, such as city`);
    else if (optionNames.includes(option)) add(line, `@option ${option} is there twice`);
    else if (!def) add(line, `@option ${option} needs its default after the name\n"" for none`);
    else if (!desc) add(line, `@option ${option} needs a few words after its default\nWhat it sets`);
    optionNames.push(option);
  }
  const read = new Map();
  for (const m of body.matchAll(/\boptions\.(\w+)/g)) if (!read.has(m[1])) read.set(m[1], lineAt(body, m.index));
  for (const [option, line] of read) {
    if (!optionNames.includes(option)) add(line, `options.${option} needs a \`@option ${option} <default> <description>\` line`);
  }
  if (options.length || read.size) needs(options[0]?.line ?? [...read.values()][0], 'It has options', SINCE.options);
  const webOpen = firstHit(/(?<![.\w])web\s*\.\s*open\b/);
  if (webOpen) needs(webOpen, 'It calls web.open', SINCE.webOpen);

  // Kolahal takes `@every off`; a shared script that suggests no timer has no @every line.
  const everyLine = every(code, 'every')[0];
  if (everyLine && parseInterval(everyLine.rest.trim()) === 0) add(everyLine.line, `@every ${everyLine.rest.trim()}: the library takes 15m, 1h or 1d\nOr no @every line`);

  out.push(...sizes(code, sizeOf(code), 'library'));
  return out.sort((a, b) => a.line - b.line);
}

/** What Kolahal reads from a script's header (ScriptMeta), for people: what it is, how it runs, what it asks for. */
export function scriptHeader(code) {
  const screen = isScreen(code);
  const uses = usesOf(code);
  const onLines = every(code, 'on').map(({ rest }) => readOn(rest));
  const named = new Set(onLines.flatMap((o) => o.events.map(([e]) => e)));
  const notification = onLines.flatMap((o) => o.events).filter(([e]) => e === 'notification');
  const apps = notification.length && notification.every(([, a]) => a.length)
    ? [...new Set(notification.flatMap(([, a]) => a).filter((a) => PACKAGE.test(a)))] : null;
  const widgetWords = (value(code, 'widget') ?? '').split(/[ \t]/).filter(Boolean);
  const kind = WIDGET_KINDS.includes(widgetWords[0]?.toLowerCase()) ? widgetWords[0].toLowerCase() : null;
  const size = (widgetWords[1] ?? '').toLowerCase().replace('×', 'x');
  const everyMin = screen ? null : parseInterval(value(code, 'every'));
  const at = screen ? [] : [...new Set((value(code, 'at') ?? '').split(/[, ]/).filter((t) => t.trim()).map(normalizeTime).filter(Boolean))].sort();
  return {
    title: value(code, 'title'),
    desc: code.match(/^\s*(--|\/\/)[ \t]*@desc[ \t]+(.+)$/m)?.[2]?.trim() || null,
    author: value(code, 'author'),
    version: value(code, 'version'),
    requires: value(code, 'requires'),
    tags: (value(code, 'tags') ?? '').split(',').map((t) => t.trim().toLowerCase()).filter(Boolean),
    command: has(code, 'command') || screen,
    screen,
    widget: !screen && kind ? { kind, size: WIDGET_SIZES.includes(size) ? size : WIDGET_SIZE_OF[kind] } : null,
    events: screen ? [] : EVENTS.filter((e) => named.has(e)),
    apps: screen ? null : apps,
    every: everyMin || null,
    at,
    notify: has(code, 'notify'),
    uses,
    internet: /\b(http|rss|url|image|web)\b|\blauncher\s*(\.\s*run\b|\?\.|\[)|[=(,]\s*launcher\b/.test(code),
    secrets: every(code, 'secret').map(({ rest }) => /^(\S+)\s+([^\s,]+(?:\s*,\s*[^\s,]+)*)/.exec(rest.trim()))
      .filter((m) => m && SECRET_NAME.test(m[1]))
      .map(([, n, hosts]) => ({ name: n, hosts: hosts.split(',').map((h) => h.trim().toLowerCase()).filter(Boolean) })),
    options: every(code, 'option').map(({ rest }) => /^(\S+)\s*("[^"]*"|\S+)?\s*(.*)$/.exec(rest.trim()))
      .filter((m) => m && OPTION_NAME.test(m[1])).map(([, n, raw = '', desc]) => {
        const choices = !raw.startsWith('"') && raw.includes('|') ? [...new Set(raw.split('|').filter(Boolean))] : [];
        return { name: n, default: choices[0] ?? raw.replace(/^"(.*)"$/, '$1'), choices, desc };
      }),
    gives: [...new Set(every(code, 'gives').flatMap(({ rest }) => usesNames(rest)).filter((g) => GIVES_NAME.test(g)))].slice(0, GIVES_MAX),
  };
}

/**
 * Checks a script. [name]: its name, as its folder's or file's, without the extension; without one, the
 * library's rules for names are left out. [lang]: "lua" or "js", else read from [fileName] or the code.
 * [api]: { names, namespaces } in that language, to check calls against the API as Kolahal's editor does.
 * Returns the issues by the "kolahal" and the "library" rules, and what Kolahal reads from the header.
 */
export function checkScript({ name, code, lang, fileName, api } = {}) {
  const l = lang ?? languageOf(fileName, code);
  return {
    lang: l,
    kolahal: [...editorIssues(code, l, api), ...olderKolahalIssues(code, l)].sort((a, b) => a.line - b.line),
    library: libraryIssues(name, code, l),
    header: scriptHeader(code),
  };
}

// ---- shaders --------------------------------------------------------------------------------------------

/** The shader's header, as ShaderMeta reads it: each `// @name value` by its name in lower case, the first of two, with its line. */
function shaderTags(code) {
  const tags = new Map();
  linesOf(code).forEach((line, i) => {
    const m = /^\s*\/\/\s*@([A-Za-z]+)\b[ \t]*(.*)$/.exec(line);
    if (m && !tags.has(m[1].toLowerCase())) tags.set(m[1].toLowerCase(), { value: m[2].trim(), line: i + 1 });
  });
  return tags;
}

/** [code] with its comments made spaces, so offsets and lines stay where they were. */
const uncommented = (code) => code.replace(/\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (c) => c.replace(/[^\n]/g, ' '));

/** As ShaderMeta.uniforms: each uniform [code] declares, with its line. */
function uniformsOf(code) {
  const body = uncommented(code);
  return [...body.matchAll(/(layout\s*\(\s*color\s*\)\s*)?\buniform\s+([A-Za-z_]\w*)\s+([^;]+);/g)]
    .flatMap((m) => m[3].split(',').map((n) => n.trim()).filter(Boolean).map((n) => ({
      name: n.replace(/\[.*/, '').trim(), type: m[2], color: !!m[1], array: n.includes('['), line: lineAt(body, m.index),
    })));
}
const floatsOf = (u) => (u.array ? 0 : FLOATS[u.type] ?? 0);
const toFloat = (s) => (FLOAT.test(s) ? Number(s.replace(/[fFdD]$/, '')) : null);

/** As ShaderParam.read: the parameter the text after `@param` makes, or null when it can't be read. */
export function readParam(text) {
  const m = PARAM_LINE.exec(text.trim());
  if (!m) return null;
  const [, name, values, first, rest] = m;
  const follow = PARAM_FOLLOW.exec(rest)?.[1] ?? null;
  const words = rest.replace(PARAM_FOLLOW, '').trim();
  const title = words || name.replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
  const range = PARAM_RANGE.exec(values);
  if (range) {
    const [min, max] = [Number(range[1]), Number(range[2])];
    const def = toFloat(first);
    if (def === null || min >= max || !(def >= min && def <= max)) return null;
    return { name, title, min, max, default: def, whole: !values.replace('..', '').includes('.') && !first.includes('.'), choices: [], follow };
  }
  const choices = values.split('|');
  if (choices.length < 2 || choices.length > PARAM_CHOICES_MAX) return null;
  if (new Set(choices.map((c) => c.toLowerCase())).size !== choices.length || !choices.every((c) => PARAM_CHOICE.test(c))) return null;
  const def = choices.indexOf(first);
  if (def < 0) return null;
  return { name, title, min: 0, max: choices.length - 1, default: def, whole: true, choices, follow };
}

/** As ShaderParam.range: what a parameter can be, `0..2` or its words. */
export function paramRange(p) {
  if (p.choices.length) return p.choices.join(' · ');
  const text = (v) => (p.whole ? String(Math.round(v)) : v.toFixed(2).replace(/0+$/, '').replace(/\.$/, '').replace(/^-0$/, '0'));
  return `${text(p.min)}..${text(p.max)}`;
}

/** What is wrong with the value a `@follow` names ([ShaderValues.parse]), or null when Kolahal reads one. */
function followProblem(text) {
  const id = [...text.toLowerCase().matchAll(VALUE_ID)].map((m) => m[0]).find((w) => OWN_VALUES.includes(w) || w.includes('.'));
  if (!id) return `it names no value: ${OWN_VALUES.slice(0, 4).join(', ')}, unlocks, screen, away… or a script's, such as weather.rain`;
  if (VALUE_GROUPS.includes(id.split('.')[0]) && !OWN_VALUES.includes(id)) return `${id} isn't one of Kolahal's values`;
  return null;
}

/** As ShaderMeta: what a shader's header says, and what Kolahal makes of it (ShaderCommands.checkLines). */
export function shaderHeader(code) {
  const tags = shaderTags(code);
  // As ShaderMeta.parse's said: the first name that is there, null when its text is empty.
  const said = (...names) => names.map((n) => tags.get(n)?.value).find((v) => v !== undefined) || null;
  const onceText = said('once');
  const once = onceText !== null && toFloat(onceText) !== null && Number.isFinite(toFloat(onceText)) ? toFloat(onceText) : null;
  const uniforms = uniformsOf(code);
  const params = [];
  for (const m of linesOf(code).map((l) => /^\s*\/\/\s*@([A-Za-z]+)\b[ \t]*(.*)$/.exec(l)).filter((m) => m && m[1].toLowerCase() === 'param')) {
    const p = readParam(m[2].trim());
    if (p && !params.some((q) => q.name === p.name) && params.length < PARAMS_MAX) params.push(p);
  }
  const int = (s) => (/^[-+]?\d+$/.test(s ?? '') ? Number(s) : null);
  const fps = int(tags.get('fps')?.value);
  const settles = toFloat(tags.get('settles')?.value ?? '');
  const animated = tags.has('animated') || once !== null;
  const uses = (n) => uniforms.some((u) => u.name === n);
  // As ShaderClock.Pace.of: a one-off shader keeps the default.
  const fpsRead = fps !== null && fps >= 1 && fps <= FPS_MAX ? fps : null;
  const settlesRead = settles !== null && settles >= SETTLES[0] && settles <= SETTLES[1] ? settles : null;
  const pace = once !== null ? { fps: FPS_MAX, settles: SETTLES[1], readsTouch: false, onlyAfterTouch: false } : {
    fps: fpsRead ?? FPS_MAX,
    settles: settlesRead ?? SETTLES[1],
    readsTouch: uses('touch') || uses('touchAge') || uses('pressed'),
    onlyAfterTouch: animated && uses('touchAge') && !uses('time') && !uses('progress') && settlesRead !== null,
  };
  const seconds = String(pace.settles);
  return {
    title: said('name', 'title'),
    desc: said('description', 'desc'),
    author: said('author'),
    version: said('version'),
    license: said('license'),
    requires: said('requires'),
    tags: (said('tags') ?? '').split(',').map((t) => t.trim()).filter(Boolean),
    animated,
    once,
    params,
    uniforms,
    sets: uniforms.map((u) => u.name).filter((n) => n in UNIFORMS),
    unset: uniforms.map((u) => u.name).filter((n) => n !== 'content' && !(n in UNIFORMS) && !params.some((p) => p.name === n)),
    tilt: uses('tilt'),
    redrawn: !animated ? null
      : pace.onlyAfterTouch ? `redrawn ${FPS_MAX} times a second for ${seconds} s after a touch`
        : pace.readsTouch && pace.fps < FPS_MAX ? `redrawn ${pace.fps} times a second, ${FPS_MAX} for ${seconds} s after a touch`
          : `redrawn ${pace.fps} times a second`,
  };
}

/** What keeps a shader from being used (ShaderMeta.problems), and the header lines it leaves out (.warnings), in Kolahal's words. */
function shaderAppIssues(code) {
  const out = [];
  const add = (line, text, level = 'error') => out.push(issue(line, text, 'kolahal', level));
  if (sizeOf(code) > MAX_BYTES) add(1, `the file is larger than ${MAX_BYTES / 1024} KB`);
  const all = uniformsOf(code);
  if (!all.some((u) => u.name === 'content' && u.type === 'shader' && !u.array)) add(1, 'it needs \'uniform shader content;\', the surface it draws over');
  for (const u of all) {
    if (u.name === 'content') continue;
    const count = UNIFORMS[u.name];
    if (u.type === 'shader') add(u.line, `'${u.name}': the only shader input is content`);
    else if (count !== undefined && floatsOf(u) !== count) add(u.line, `'${u.name}' must be a ${count === 1 ? 'float' : `float${count}`}, not ${u.type}${u.array ? '[]' : ''}`);
  }
  const lines = [];
  linesOf(code).forEach((l, i) => {
    const m = /^\s*\/\/\s*@([A-Za-z]+)\b[ \t]*(.*)$/.exec(l);
    if (m && m[1].toLowerCase() === 'param') lines.push({ line: i + 1, text: m[2].trim() });
  });
  if (lines.length > PARAMS_MAX) add(lines[PARAMS_MAX].line, `it has ${lines.length} parameters, and can have ${PARAMS_MAX}`);
  const seen = new Set();
  for (const { line, text } of lines) {
    const p = readParam(text);
    if (!p) { add(line, `'@param ${text}' can't be read: write '@param amount 0.0..2.0 = 1.0 Title' or '@param shape a|b = a Title'`); continue; }
    if (p.name in UNIFORMS || p.name === 'content') add(line, `'${p.name}' can't be a parameter: Kolahal sets it`);
    else if (seen.has(p.name)) add(line, `the parameter '${p.name}' is there twice`);
    else if (!all.some((u) => u.name === p.name && floatsOf(u) === 1)) add(line, `the parameter '${p.name}' needs 'uniform float ${p.name};'`);
    seen.add(p.name);
  }
  // The header's lines that can't be read: left out, and the shader works without them.
  const tags = shaderTags(code);
  const quoted = (n, s) => (s ? `'@${n} ${s}'` : `'@${n}'`);
  const fps = tags.get('fps');
  if (fps && !(/^[-+]?\d+$/.test(fps.value) && Number(fps.value) >= 1 && Number(fps.value) <= FPS_MAX)) add(fps.line, `${quoted('fps', fps.value)}: write a whole number from 1 to ${FPS_MAX}`, 'warning');
  const settles = tags.get('settles');
  const s = settles && toFloat(settles.value);
  if (settles && !(s !== null && s >= SETTLES[0] && s <= SETTLES[1])) add(settles.line, `${quoted('settles', settles.value)}: write the seconds, from ${SETTLES[0]} to ${SETTLES[1]}`, 'warning');
  const still = tags.get('still');
  const t = still && toFloat(still.value);
  if (still && !(t !== null && t >= 0 && t <= STILL_MAX)) add(still.line, `${quoted('still', still.value)}: write the seconds, from 0 to ${STILL_MAX}`, 'warning');
  return out.sort((a, b) => a.line - b.line);
}

/** What this repository asks of a shader it shares, besides what keeps Kolahal from using it ([shaderAppIssues]). */
function shaderLibraryIssues(name, code) {
  const out = [];
  const add = (line, text, level) => out.push(issue(line, text, 'library', level));
  if (name !== undefined && name !== null) {
    if (!SHADER_NAME.test(name)) add(1, `${name} isn't a name the library takes\nSmall letters, digits, - and _, at most 40, starting with a letter or digit`);
    if (SHADER_WORDS.includes(name)) add(1, `${name} is a word of the shader command`);
    if (SHADER_BUNDLED.includes(name)) add(1, `${name} comes with Kolahal\nShare it under another name`);
    if (name.startsWith('template-')) add(1, `${name}: template-… names are for the templates that come with Kolahal`);
  }
  out.push(...oddBreaks(code, 'library'));
  const tags = shaderTags(code);
  const said = (...names) => names.map((n) => tags.get(n)?.value).find((v) => v) ?? null;
  const missing = SHADER_REQUIRED.filter((names) => !said(...names)).map((names) => `@${names[0]}`);
  if (missing.length) add(1, `No ${missing.join(', ')} line${missing.length > 1 ? 's' : ''}\nThe library needs @name, @description, @author, @version, @license and @requires`);
  const version = said('version');
  if (version && !VERSION.test(version)) add(tags.get('version').line, `@version ${version} isn't a version: numbers like 1.2`);
  const requires = said('requires');
  if (requires && (!VERSION.test(requires) || older(requires, SHADERS_SINCE))) add(tags.get('requires').line, `@requires ${requires}: ${SHADERS_SINCE} or newer\nThe first Kolahal with shaders`);
  const header = shaderHeader(code);
  linesOf(code).forEach((l, i) => {
    const m = /^\s*\/\/\s*@([A-Za-z]+)\b[ \t]*(.*)$/.exec(l);
    const p = m && m[1].toLowerCase() === 'param' && readParam(m[2].trim());
    const problem = p && p.follow && followProblem(p.follow);
    if (problem) add(i + 1, `@param ${p.name} @follow ${p.follow}: ${problem}\nKolahal leaves it out`, 'warning');
  });
  for (const u of header.uniforms) {
    if (u.name === 'content' || header.params.some((p) => p.name === u.name)) continue;
    if (!(u.name in UNIFORMS) && u.type !== 'shader') add(u.line, `uniform ${u.name}: Kolahal doesn't set it, so it stays 0\nMake it a const, or a @param`);
    if (u.color && floatsOf(u) < 3) add(u.line, `uniform ${u.name}: layout(color) needs three or four numbers`);
  }
  const time = header.uniforms.find((u) => u.name === 'time');
  if (time && !header.animated) add(time.line, 'It reads time, which only moves in a shader with // @animated (or @once)');
  if (!/\bhalf4\s+main\s*\(|\bfloat4\s+main\s*\(|\bvec4\s+main\s*\(/.test(uncommented(code))) add(1, 'It needs a half4 main(float2 xy) function');
  // Its size is Kolahal's rule (shaderAppIssues).
  out.push(...sizes(code, 0, 'library'));
  return out.sort((a, b) => a.line - b.line);
}

/** Checks a shader, as [checkScript] a script. [name]: its file's name without `.agsl`; without one, the rules for names are left out. */
export function checkShader({ name, code } = {}) {
  return {
    lang: 'agsl',
    kolahal: shaderAppIssues(code),
    library: shaderLibraryIssues(name, code),
    header: shaderHeader(code),
  };
}

/** An issue in one line, for a terminal or a GitHub annotation: its hints after its first line, as sentences. */
export const oneLine = (x) => x.text.split('\n').map((s, i) => (i ? s.replace(/^./, (c) => c.toUpperCase()) : s)).join('. ');
