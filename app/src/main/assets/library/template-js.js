// @title Template: JavaScript
// @desc A starting point in JavaScript: a command that fetches JSON and logs a line you can tap
// @author Kolahal
// @version 1.2
// @command
// @usage [topic]
// @tags template, javascript
// @requires 0.4.1
//
// The same building blocks as the Lua templates, in JavaScript (QuickJS). The script runs inside an
// async function: `await` the http and rss calls, and `return` to stop early. Try `template-js`,
// then `template-js Lua (programming language)`. Talks to: en.wikipedia.org.

const topic = argline || "Android (operating system)";
const r = await http.get(
  "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodeURIComponent(topic.replace(/ /g, "_")),
);
if (r.status === 404) return log(fmt.color("muted", `Wikipedia has no page called '${topic}'`));
if (!r.ok) throw new Error("Wikipedia answered HTTP " + r.status);
const page = JSON.parse(r.body);

// log(title, body, ctx): the article's title (its short description beside it, in italics), its
// summary muted under it; the object at the end decides what tapping the line does: here, open the
// article. fmt styles parts of a line.
const title = page.title + (page.description ? " " + fmt.italic(page.description) : "");
log(title, page.extract, { url: page.content_urls.desktop.page });
