// The Script check page (src/content/docs/tools/script-check.mdx) runs the rules of kolahal-scripts, from
// its tools/rules.mjs: the same rules its pull requests are checked by, in the words of Kolahal's editor. This
// keeps the site's copy of that file, src/lib/rules.mjs, and src/lib/rules.source.json beside it: the branch
// and commit it came from, and its SHA-256.
//
//   node scripts/copy-rules.mjs [<checkout>]   copy it as committed in a kolahal-scripts checkout (default: the
//                                             one beside this repository), on the branch named after the app's
//                                             next version
//   node scripts/copy-rules.mjs --working      the same with the file as it is, changes not yet committed
//                                             (rules.source.json then says "uncommitted": true)
//   node scripts/copy-rules.mjs --check        exit 1 when the copy isn't the file on GitHub now, on the branch it
//                                             came from (main, once that is gone), or was changed here
//
// The website's workflow (.github/workflows/site.yml) runs --check before it builds, so a change to the rules in
// kolahal-scripts fails the next build of the site until the copy is made again. When GitHub can't be reached,
// it says so and goes on. Needs git and Node.js only.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const site = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const COPY = join(site, 'src/lib/rules.mjs');
const SOURCE = join(site, 'src/lib/rules.source.json');
const REPO = 'mtwabpin/kolahal-scripts';
const FILE = 'tools/rules.mjs';
const sha256 = (text) => createHash('sha256').update(text).digest('hex');
// What the copy starts with, so nobody edits it here.
const banner = (branch, commit) =>
	`// Copied from ${REPO} (${branch}, ${commit.slice(0, 12)}), ${FILE}, by scripts/copy-rules.mjs: change it there.\n`;

if (process.argv.includes('--check')) {
	const source = JSON.parse(readFileSync(SOURCE, 'utf8'));
	const copy = readFileSync(COPY, 'utf8');
	const body = copy.slice(copy.indexOf('\n') + 1);
	if (sha256(body) !== source.sha256) {
		console.error('src/lib/rules.mjs was changed here\nChange tools/rules.mjs in kolahal-scripts, then run node scripts/copy-rules.mjs');
		process.exit(1);
	}
	if (process.env.KOLAHAL_RULES_OFFLINE) process.exit(0);
	let live = null;
	for (const branch of [source.branch, 'main']) {
		try {
			const res = await fetch(`https://raw.githubusercontent.com/${REPO}/${branch}/${FILE}`, { signal: AbortSignal.timeout(15000) });
			if (res.ok) { live = { branch, text: await res.text() }; break; }
		} catch (e) {
			console.log(`The rules on GitHub couldn't be read: ${e.message}\nsrc/lib/rules.mjs wasn't compared with them`);
			process.exit(0);
		}
	}
	if (!live) {
		console.error(`No ${FILE} on ${REPO}'s ${source.branch} or main`);
		process.exit(1);
	}
	if (sha256(live.text) !== source.sha256) {
		console.error(`src/lib/rules.mjs isn't ${FILE} of ${REPO}'s ${live.branch}\nRun node scripts/copy-rules.mjs with a checkout of that branch`);
		process.exit(1);
	}
	console.log(`src/lib/rules.mjs is ${REPO}'s ${live.branch} ${FILE}`);
	process.exit(0);
}

const working = process.argv.includes('--working');
const repo = resolve(process.argv.slice(2).find((a) => !a.startsWith('--')) ?? join(site, '../../kolahal-scripts'));
const git = (...args) => execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
const branch = git('branch', '--show-current').trim();
const commit = git('rev-parse', 'HEAD').trim();
const now = readFileSync(join(repo, FILE), 'utf8');
// The file as committed, what GitHub has once the branch is pushed; or as it is, with --working.
const committed = (() => { try { return git('show', `HEAD:${FILE}`); } catch { return null; } })();
if (!working && committed === null) throw new Error(`${FILE} isn't committed in ${branch}: commit it, or copy it with --working`);
const text = working ? now : committed;
if (!working && now !== text) console.log(`${FILE} has changes that aren't committed\nThe committed file was copied; --working copies the changes`);
writeFileSync(COPY, banner(branch, commit) + text);
const record = { repo: REPO, branch, commit, file: FILE, sha256: sha256(text), ...(text !== committed ? { uncommitted: true } : {}) };
writeFileSync(SOURCE, JSON.stringify(record, null, '\t') + '\n');
console.log(`Copied ${FILE} of ${branch} (${commit.slice(0, 12)})${record.uncommitted ? ', with changes not yet committed' : ''}`);
