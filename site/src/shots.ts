/**
 * Pictures under `src/assets/` by their path there (`parts/home.png`), for pages that take a picture
 * by name and do without it when it isn't there yet: the tour, the gallery, the hero's phone and
 * the home page's pictures. The captures of the app go in `src/assets/shots/<part>/`; CAPTURES.md
 * lists each one.
 *
 * - `shot(path, standIn)`: the capture if its file is there, else the stand-in (an older picture),
 *   else nothing, and the page shows one picture fewer.
 * - `lightOf(path)`: the same picture in the light theme, `<name>-light.<ext>` beside it, if there
 *   is one. The site shows it in its light theme and the other in its dark one.
 */
import type { ImageMetadata } from 'astro';

const FILES = import.meta.glob<{ default: ImageMetadata }>('./assets/**/*.{png,jpg,jpeg,webp}', { eager: true });

/** The picture at `src/assets/<path>`, if the file is there. */
export function asset(path: string): ImageMetadata | undefined {
	return FILES[`./assets/${path}`]?.default;
}

/** The picture at `path`, else the one at `standIn`, else undefined. */
export function shot(path: string, standIn?: string): ImageMetadata | undefined {
	return asset(path) ?? (standIn ? asset(standIn) : undefined);
}

/** Whether the capture itself is there (not its stand-in). */
export function hasShot(path: string): boolean {
	return asset(path) !== undefined;
}

/** The light-theme twin of the picture at `path` (`home.png` → `home-light.png`), if there is one. */
export function lightOf(path: string): ImageMetadata | undefined {
	return asset(path.replace(/(\.[a-z]+)$/, '-light$1'));
}
