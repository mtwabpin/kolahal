// Post-build check: every internal href/src in dist/ must point at a file that exists, and every
// #fragment on an internal page must match an id on that page. Exits 1 on any broken link.
// Links must be absolute (`/kolahal/...`): a relative link breaks when a page is opened without its
// trailing slash. Markdown links are written `/reference/...` and astro.config.mjs adds the base.
import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { join, dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const base = (process.env.DOCS_BASE ?? '/kolahal').replace(/\/$/, '');

function walk(dir) {
	return readdirSync(dir).flatMap((name) => {
		const p = join(dir, name);
		return statSync(p).isDirectory() ? walk(p) : [p];
	});
}

const pages = walk(root).filter((f) => f.endsWith('.html'));
const idsCache = new Map();
function idsOf(file) {
	if (!idsCache.has(file)) {
		const html = readFileSync(file, 'utf8');
		idsCache.set(file, new Set([...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1])));
	}
	return idsCache.get(file);
}

function targetFile(urlPath) {
	let p = decodeURI(urlPath);
	if (base && p.startsWith(base + '/')) p = p.slice(base.length);
	else if (base && p === base) p = '/';
	else if (base) return null; // absolute path outside the base: broken on GitHub Pages
	const abs = join(root, p);
	if (p.endsWith('/')) return join(abs, 'index.html');
	if (existsSync(abs) && statSync(abs).isFile()) return abs;
	if (existsSync(join(abs, 'index.html'))) return join(abs, 'index.html');
	return abs;
}

let broken = 0;
for (const page of pages) {
	const html = readFileSync(page, 'utf8');
	const pageUrl = '/' + relative(root, page).replace(/index\.html$/, '').replace(/\\/g, '/');
	const pageDir = new URL(base + pageUrl, 'http://x');
	for (const m of html.matchAll(/\s(?:href|src)="([^"]+)"/g)) {
		const raw = m[1].replace(/&amp;/g, '&');
		if (/^(https?:|mailto:|tel:|data:|javascript:|\/\/)/.test(raw)) continue;
		if (!raw.startsWith('/') && !raw.startsWith('#')) {
			console.error(`relative link: ${pageUrl} -> ${raw} (write it as /path/ from the site root)`);
			broken++;
			continue;
		}
		const url = new URL(raw, pageDir);
		if (url.host !== 'x') continue;
		const file = targetFile(url.pathname);
		if (!file || !existsSync(file)) {
			console.error(`broken: ${pageUrl} -> ${raw}`);
			broken++;
			continue;
		}
		const hash = decodeURIComponent(url.hash.slice(1));
		if (hash && file.endsWith('.html') && hash !== '_top' && !idsOf(file).has(hash)) {
			console.error(`broken anchor: ${pageUrl} -> ${raw}`);
			broken++;
		}
	}
}
// Links that leave the site open in a new tab (src/links.ts): every <a> to another site has
// target="_blank" and rel="noopener"; links within the site have no target.
const site = (process.env.DOCS_SITE ?? 'https://mtwabpin.github.io').replace(/\/$/, '') + base;
let tabs = 0;
for (const page of pages) {
	const html = readFileSync(page, 'utf8');
	const pageUrl = '/' + relative(root, page).replace(/index\.html$/, '').replace(/\\/g, '/');
	for (const [tag] of html.matchAll(/<a\s[^>]*>/g)) {
		const href = tag.match(/\shref="([^"]+)"/)?.[1];
		if (!href) continue;
		const leaves = /^https?:\/\//.test(href) && href !== site && !href.startsWith(site + '/') && !href.startsWith(site + '#');
		const newTab = /\starget="?_blank/.test(tag);
		if (leaves && !(newTab && /\srel="[^"]*noopener/.test(tag))) {
			console.error(`leaves the site but not in a new tab with rel="noopener": ${pageUrl} -> ${href}`);
			tabs++;
		} else if (!leaves && newTab) {
			console.error(`within the site but opens a new tab: ${pageUrl} -> ${href}`);
			tabs++;
		}
	}
}
broken += tabs;
console.log(`checked ${pages.length} pages: ${broken} broken link(s)`);
process.exit(broken ? 1 : 0);
