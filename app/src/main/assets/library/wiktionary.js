// @title Wiktionary
// @desc A word's meanings from Wiktionary, in any language: wiktionary <word>
// @author Kolahal
// @version 1.0
// @command
// @usage <word>
// @tags dictionary, words, languages
// @option lang English The language to show, as Wiktionary names it (English, German, Hindi…), or all
// @requires 0.4.7
//
// Talks to: en.wiktionary.org (Wikimedia's REST API, no key).
// `wiktionary serendipity`: the word and its language, then one line per meaning with its part of
// speech first and an example muted under it. Tap a line to open the entry on Wiktionary.
// A word with no entry in your language (`wiktionary Hund`) shows the languages that have one.

const MAX_LANGS = 3; // languages shown when yours has no entry, or with lang set to all
const PER_POS = 3; // meanings per part of speech
const MAX_DEFS = 8; // meanings per language

// A quoted example, without doubling quotes it already has.
const quote = (t) => (/^["“]/.test(t) ? t : `“${t}”`);

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const word = (argline || "").trim();
if (!word) return log(fmt.color("muted", "Usage: wiktionary <word>"));
if (word.length > 60) return fail("No entry", "That's too long for a word");
const lang = (options.lang || "").trim() || "English";

// Wiktionary sends meanings as HTML: keep the text, decode the few entities it uses.
// A meaning's sub-meanings come nested in it as a list, often left open; they follow as meanings
// of their own.
function text(html) {
  return (html || "")
    .replace(/<(ol|ul|dl)[\s>][\s\S]*/, "")
    .replace(/<[^>]*>/g, "")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

// Every entry for a title, or null when Wiktionary has none.
async function lookup(title) {
  const r = await http.get(`https://en.wiktionary.org/api/rest_v1/page/definition/${encodeURIComponent(title)}`);
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`Wiktionary answered HTTP ${r.status}`);
  const data = JSON.parse(r.body);
  // The answer groups entries by language code; "other" holds the languages without one, and
  // goes last so that German comes before Alemannic German.
  const codes = Object.keys(data).sort((a, b) => (a === "other") - (b === "other"));
  const entries = codes.flatMap((c) => data[c]).filter((e) => e && e.language);
  return entries.length ? entries : null;
}

let title = word;
let entries;
try {
  entries = await lookup(word);
  // "Serendipity" has no entry of its own, "serendipity" does.
  if (!entries && word.toLowerCase() !== word) {
    title = word.toLowerCase();
    entries = await lookup(title);
  }
} catch (e) {
  return fail("No entry", e instanceof SyntaxError ? "Wiktionary sent something unexpected" : `${e.message || e}`);
}
if (!entries) return log(fmt.color("muted", `Wiktionary has no entry for '${word}'`));

// The entries of each language, in the order Wiktionary lists them.
const byLang = new Map();
for (const e of entries) {
  if (!byLang.has(e.language)) byLang.set(e.language, []);
  byLang.get(e.language).push(e);
}

let langs;
if (lang.toLowerCase() === "all") {
  langs = [...byLang.keys()].slice(0, MAX_LANGS);
} else {
  const mine = [...byLang.keys()].find((l) => l.toLowerCase() === lang.toLowerCase());
  if (mine) {
    langs = [mine];
  } else {
    langs = [...byLang.keys()].slice(0, MAX_LANGS);
    log(fmt.color("muted", `No ${lang} entry for '${title}'. Other languages:`));
  }
}

for (const language of langs) {
  const url = `https://en.wiktionary.org/wiki/${encodeURIComponent(title)}#${encodeURIComponent(language.replace(/ /g, "_"))}`;
  log(`${fmt.bold(title)}  ${fmt.color("muted", language)}`, null, { url, copy: title });

  let shown = 0;
  for (const entry of byLang.get(language)) {
    const pos = (entry.partOfSpeech || "").toLowerCase();
    let inPos = 0;
    for (const d of entry.definitions || []) {
      if (shown >= MAX_DEFS || inPos >= PER_POS) break;
      const meaning = text(d.definition);
      if (!meaning) continue;
      // The examples of a meaning with sub-meanings are theirs: they show with them.
      const nested = /<(ol|ul|dl)[\s>]/.test(d.definition || "");
      const ex = d.parsedExamples && d.parsedExamples[0];
      let example = null;
      if (nested) example = null;
      else if (ex) example = quote(text(ex.example)) + (ex.translation ? ` (${text(ex.translation)})` : "");
      else if (d.examples && d.examples[0]) example = quote(text(d.examples[0]));
      // A small space above each meaning (gap, from Kolahal 0.5.0; older ones ignore it) keeps it
      // apart from the example above it.
      log(`${fmt.color("accent", pos)}  ${meaning}`, example, { url, copy: meaning, gap: true });
      shown++;
      inPos++;
    }
  }
}

// The rest, when there's more than fits: tap to see them all.
if (langs.length < byLang.size) {
  const more = byLang.size - langs.length;
  const url = `https://en.wiktionary.org/wiki/${encodeURIComponent(title)}`;
  log(fmt.color("muted", `${more} more ${more === 1 ? "language" : "languages"} on Wiktionary`), null, { url, gap: true });
}
