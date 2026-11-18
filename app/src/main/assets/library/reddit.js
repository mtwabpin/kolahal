// reddit.js — today's top posts of a subreddit (public JSON/RSS, no API key).
// @command
// @desc Top posts today: reddit [subreddit] (default: its subreddit option, android)
// @usage [<subreddit>]
// @title Reddit top posts
// @author Kolahal
// @version 1.0
// @requires 0.5.0
// @option subreddit android Default subreddit
// @option posts 5 How many posts it shows, up to 25
// @option images on|off A picture under each post that has one; off saves data
//
// Usage:
//   reddit             today's top posts of its subreddit option (r/android to start with)
//   reddit linux       today's top posts of r/linux
//
// Uses the JSON listing (with scores and comment counts) and falls back to
// the subreddit's RSS feed when Reddit blocks anonymous JSON requests.
// Tap a line to open the discussion; long-press to copy the link. A post with a picture shows it
// under its line (not for NSFW or spoiler posts); the images option turns pictures off.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const LIMIT = Math.min(25, Math.max(1, parseInt(options.posts, 10) || 5));
const UA = { "User-Agent": "android:kolahal-launcher:0.1 (by /u/kolahal)" }; // Reddit rejects generic agents
const sub = ((argline || "").trim() || (options.subreddit || "").trim() || "android").replace(/^\/?r\//i, "");

if (!/^[A-Za-z0-9_]{2,21}$/.test(sub)) {
  return fail("No posts", `'${sub}' isn't a subreddit name`);
}

// Pictures under posts, unless the images option is off.
const PICTURES = (options.images || "on").trim().toLowerCase() !== "off";

// The picture of post [p], about 640 pixels wide, or null. Reddit's previews come in several sizes,
// so a phone needn't fetch the full original. NSFW and spoiler posts never show one.
function picture(p) {
  if (!PICTURES || p.over_18 || p.spoiler) return null;
  const https = (u) => (typeof u === "string" && u.startsWith("https://") ? u : null);
  // The size nearest 640 wide from a list of { u or url, x or width }.
  const near = (sizes) => {
    const list = (sizes || []).map((s) => ({ url: https(s.u || s.url), w: s.x || s.width || 0 })).filter((s) => s.url);
    if (list.length === 0) return null;
    const big = list.filter((s) => s.w >= 640).sort((a, b) => a.w - b.w);
    return (big[0] || list.sort((a, b) => b.w - a.w)[0]).url;
  };
  // A gallery: its first picture.
  if (p.is_gallery && p.media_metadata && p.gallery_data && p.gallery_data.items && p.gallery_data.items[0]) {
    const m = p.media_metadata[p.gallery_data.items[0].media_id];
    if (m && m.status === "valid") return near([...(m.p || []), m.s].filter(Boolean));
  }
  // An image, a link or a video: its preview.
  const image = p.preview && p.preview.images && p.preview.images[0];
  if (image) return near([...(image.resolutions || []), image.source].filter(Boolean));
  // An image post without a preview: the picture itself.
  if (p.post_hint === "image" && /\.(jpe?g|png|gif|webp)$/i.test(p.url || "")) return https(p.url);
  return null;
}

// Compact score: 12345 -> 12.3k
const short = (n) => (n >= 1000 ? (n / 1000).toFixed(1) + "k" : String(n));

// 1) JSON listing.
let r = null;
try {
  r = await http.get(`https://www.reddit.com/r/${sub}/top.json?limit=${LIMIT}&t=day&raw_json=1`, UA);
} catch (e) {
  return fail("No posts", `Network error: ${e.message || e}`);
}
if (r.status === 404) return fail("No posts", `There is no r/${sub}`);

if (r.ok) {
  let posts;
  try {
    posts = JSON.parse(r.body).data.children.map((c) => c.data);
  } catch (e) {
    return fail("No posts", "Reddit sent something unexpected");
  }
  if (posts.length === 0) return log(fmt.color("muted", `r/${sub}: no posts today`));
  for (const p of posts.slice(0, LIMIT)) {
    const link = "https://www.reddit.com" + p.permalink;
    // One line a post: the title (a tap opens the discussion), then its points and comments, muted.
    // A post that links elsewhere ends with a link to that site, which opens the article itself.
    // A small space above each post (gap) keeps the posts apart when their titles wrap.
    const out = /^https?:\/\//.test(p.url || "") && !p.is_self && !(p.url || "").startsWith(link) ? p.url : null;
    const site = out ? ` · ${fmt.link(p.domain || "article", out)}` : "";
    const image = picture(p);
    log(`${p.title} ${fmt.color("muted", `· ${short(p.score)} points · ${p.num_comments} comments`)}${site}`, { url: link, copy: link, gap: true, ...(image ? { image } : {}) });
  }
  return;
}

// 2) RSS fallback (JSON is often 403/429 for anonymous clients).
let feed;
try {
  feed = await rss.fetch(`https://www.reddit.com/r/${sub}/top/.rss?t=day&limit=${LIMIT}`);
} catch (e) {
  return fail("No posts", `Reddit answered HTTP ${r.status}, and its feed failed too: ${e.message || e}`);
}
const items = feed.items || [];
if (items.length === 0) return log(fmt.color("muted", `r/${sub}: no posts today (or the subreddit is private)`));
for (const item of items.slice(0, LIMIT)) {
  log(item.title || "(untitled)", { url: item.link, copy: item.link, gap: true });
}
