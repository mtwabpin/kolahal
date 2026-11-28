// CanvasKit (Skia in WebAssembly, canvaskit-wasm in package.json) on the site itself, for the Shader preview
// page: its canvaskit.js and canvaskit.wasm, and its license, at <base>/canvaskit/<version>/. Visitors fetch
// them from the site, never from another one, and only that page loads them. The build copies them into
// dist/; the dev server serves them from node_modules.
import { copyFileSync, mkdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const root = dirname(require.resolve('canvaskit-wasm/package.json'));
export const CANVASKIT_VERSION = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8')).version;

/**
 * The Skia milestone each CanvasKit is built from: SK_MILESTONE in include/core/SkMilestone.h at Skia's tag
 * canvaskit/<version>. The page says it. A new canvaskit-wasm needs its line here, or the build stops.
 */
const MILESTONES = { '0.42.0': 153 };
export const SKIA_MILESTONE = MILESTONES[CANVASKIT_VERSION];
if (!SKIA_MILESTONE) throw new Error(`canvaskit.mjs: add the Skia milestone of canvaskit-wasm ${CANVASKIT_VERSION} to MILESTONES`);

/** The published name of each file, and where it is in the package. */
const FILES = {
	'canvaskit.js': 'bin/canvaskit.js',
	'canvaskit.wasm': 'bin/canvaskit.wasm',
	'LICENSE.txt': 'LICENSE',
};
const TYPES = { '.js': 'text/javascript', '.wasm': 'application/wasm', '.txt': 'text/plain; charset=utf-8' };

export function canvaskit() {
	let base = '/';
	return {
		name: 'canvaskit',
		hooks: {
			'astro:config:done': ({ config }) => {
				base = config.base.endsWith('/') ? config.base : config.base + '/';
			},
			'astro:server:setup': ({ server }) => {
				const prefix = `${base}canvaskit/${CANVASKIT_VERSION}/`;
				server.middlewares.use((req, res, next) => {
					const path = (req.url ?? '').split('?')[0];
					const file = path.startsWith(prefix) ? FILES[path.slice(prefix.length)] : undefined;
					if (!file) return next();
					res.setHeader('Content-Type', TYPES[path.slice(path.lastIndexOf('.'))]);
					res.end(readFileSync(join(root, file)));
				});
			},
			'astro:build:done': ({ dir }) => {
				const out = join(fileURLToPath(dir), 'canvaskit', CANVASKIT_VERSION);
				mkdirSync(out, { recursive: true });
				for (const [name, file] of Object.entries(FILES)) copyFileSync(join(root, file), join(out, name));
			},
		},
	};
}
