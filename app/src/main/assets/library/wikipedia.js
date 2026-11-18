// wikipedia.js — summary of a Wikipedia article (REST API, no key).
// @command
// @desc Wikipedia summary: wikipedia <topic> (no topic: a random article)
// @usage [<topic>]
// @title Wikipedia
// @author Kolahal
// @version 1.0
// @requires 0.5.0
// @option lang en The language edition: en, de, hi, simple…
//
// Usage:
//   wikipedia Alan Turing
//   wikipedia             a random article
//
// Tries the exact title first, then falls back to a search for the best match.
// The article's picture shows under its title; tap the title to open the full article.

const LANG = (options.lang || "").trim().toLowerCase() || "en";
const MAX_CHARS = 600;
const base = `https://${LANG}.wikipedia.org`;
// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

if (!/^[a-z][a-z0-9-]*$/.test(LANG)) return fail("No article", `'${LANG}' isn't a Wikipedia language, such as en or de`);

const topic = (argline || "").trim();

async function summary(title) {
  const r = await http.get(`${base}/api/rest_v1/page/summary/${encodeURIComponent(title.replace(/ /g, "_"))}`);
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

// Best search match for a fuzzy query (opensearch returns [query, [titles], ...]).
async function search(query) {
  const r = await http.get(
    `${base}/w/api.php?action=opensearch&limit=1&namespace=0&format=json&search=${encodeURIComponent(query)}`
  );
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  const titles = JSON.parse(r.body)[1] || [];
  return titles.length > 0 ? titles[0] : null;
}

// The article's picture, about as wide as the log: the original when it's small enough (and not an
// SVG, which Android can't decode), otherwise Wikimedia's 960px thumbnail.
function picture(p) {
  const orig = p.originalimage;
  if (orig && orig.source && orig.width <= 960 && !/\.svg(\?|$)/i.test(orig.source)) return orig.source;
  return p.thumbnail && p.thumbnail.source ? p.thumbnail.source.replace(/\/\d+px-/, "/960px-") : null;
}

let page;
try {
  if (!topic) {
    const r = await http.get(`${base}/api/rest_v1/page/random/summary`);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    page = JSON.parse(r.body);
  } else {
    page = await summary(topic);
    if (!page || page.type === "disambiguation") {
      const best = await search(topic);
      if (best && (!page || best !== page.title)) page = (await summary(best)) || page;
    }
  }
} catch (e) {
  return fail("No article", `${e.message || e}`);
}
if (!page) return log(fmt.color("muted", `Wikipedia has nothing for '${topic}'`));

const link =
  (page.content_urls && page.content_urls.mobile && page.content_urls.mobile.page) ||
  `${base}/wiki/${encodeURIComponent(page.title.replace(/ /g, "_"))}`;

const title = page.title + (page.description ? ` — ${page.description}` : "");
let extract = (page.extract || "").trim();
if (extract.length > MAX_CHARS) extract = extract.slice(0, MAX_CHARS).replace(/\s+\S*$/, "") + "…";

// The article's title, its summary under it; Copy text takes the whole summary.
const ctx = { url: link, copy: `${title}\n${(page.extract || "").trim()}` };
const img = picture(page);
if (img) ctx.image = img;
log(title, extract, ctx);
