// reddit.js — today's top posts of a subreddit (public JSON/RSS, no API key).
// @command
// @desc Top posts today: reddit [subreddit] (default: its subreddit option, android)
// @usage [<subreddit>]
// @title Reddit top posts
// @option subreddit android Default subreddit
// @option posts 5 How many posts it shows, up to 25
//
// Usage:
//   reddit             today's top posts of its subreddit option (r/android to start with)
//   reddit linux       today's top posts of r/linux
//
// Uses the JSON listing (with scores and comment counts) and falls back to
// the subreddit's RSS feed when Reddit blocks anonymous JSON requests.
// Tap a line to open the discussion; long-press to copy the link.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const LIMIT = Math.min(25, Math.max(1, parseInt(options.posts, 10) || 5));
const UA = { "User-Agent": "android:kolahal-launcher:0.1 (by /u/kolahal)" }; // Reddit rejects generic agents
const sub = ((argline || "").trim() || (options.subreddit || "").trim() || "android").replace(/^\/?r\//i, "");

if (!/^[A-Za-z0-9_]{2,21}$/.test(sub)) {
  return fail("No posts", `'${sub}' isn't a subreddit name`);
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
    // One line a post: the title, then its points and comments, muted.
    log(`${p.title} ${fmt.color("muted", `· ${short(p.score)} points · ${p.num_comments} comments`)}`, { url: link, copy: link });
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
  log(item.title || "(untitled)", { url: item.link, copy: item.link });
}
