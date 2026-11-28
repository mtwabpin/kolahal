// Compiles every shader that comes with Kolahal (app/src/main/assets/shaders, the templates too) with the
// CanvasKit that the Shader preview page uses (canvaskit-wasm in package.json), so the page can compile
// what the app ships. It also checks each one as the app does before it compiles (ShaderMeta.problems)
// and the header's lines (ShaderMeta.warnings). Part of `pnpm build`; `pnpm check-shaders` runs it alone.
// Exits 1 on any shader that can't be used.
//
// CanvasKit's Skia is newer than a phone's (see the page), so a shader that passes here can still fail on
// an older phone in rare cases; the app's own tests compile them on Android.
import { readdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { problems, warnings, compileError, problemText, capital } from '../src/tools/shader-meta.js';

const here = dirname(fileURLToPath(import.meta.url));
const dir = resolve(here, '..', '..', 'app', 'src', 'main', 'assets', 'shaders');
const require = createRequire(import.meta.url);
const CanvasKitInit = require('canvaskit-wasm');
const { version } = require('canvaskit-wasm/package.json');
const wasm = dirname(require.resolve('canvaskit-wasm/bin/canvaskit.wasm'));

const ck = await CanvasKitInit({ locateFile: (file) => join(wasm, file) });

const files = readdirSync(dir).filter((f) => f.endsWith('.agsl')).sort();
let failed = 0;
for (const file of files) {
	const name = file.replace(/\.agsl$/, '');
	const source = readFileSync(join(dir, file), 'utf8');
	let problem = problems(source)[0] ?? null;
	if (problem === null) {
		let message = '';
		const effect = ck.RuntimeEffect.Make(source, (e) => (message = e));
		if (effect) effect.delete();
		else problem = problemText(compileError(message || "it doesn't compile"));
	}
	if (problem !== null) {
		console.error(`Shader ${name} can't be used\n  ${capital(problem)}`);
		failed++;
	}
	for (const w of warnings(source)) {
		console.error(`Shader ${name}: ${w}`);
		failed++;
	}
}
if (failed) {
	console.error(`\n${failed} of ${files.length} shaders can't be used with CanvasKit ${version}.`);
	process.exit(1);
}
console.log(`All ${files.length} shaders compile with CanvasKit ${version}.`);
