import pkg from '../package.json';

/**
 * The release these pages describe, and its APKs on GitHub (the full build and the Play build). The version is site/package.json's,
 * which every release bumps together with the app, so the links follow each release.
 */
export const VERSION: string = pkg.version;

const REPO = 'https://github.com/mtwabpin/kolahal';
const DOWNLOAD = `${REPO}/releases/download/v${VERSION}`;

export const APK = `${DOWNLOAD}/kolahal-${VERSION}.apk`;
export const PLAY_APK = `${DOWNLOAD}/kolahal-${VERSION}-play.apk`;
export const RELEASES = `${REPO}/releases`;

/**
 * Whether the site offers the APKs on GitHub. Off while Kolahal is in alpha testing on Google Play: the site
 * offers only the alpha test and the Play Store. Turn it on when Kolahal is public on Google Play, and the site
 * is back as it was: the APK buttons, sideloading, the Play Protect help, the full build next to the Play build,
 * `update` and the other download notes. Pages that change with it: `<name>.alpha.mdx` replaces `<name>.mdx`
 * while it is off (content.config.ts), and `<Downloads>` shows or hides a part of a page (components/Downloads.astro).
 *
 * To preview the other state without changing this file: `PUBLIC_DIRECT_DOWNLOADS=true pnpm build` (or `=false`).
 */
const DIRECT_DOWNLOADS_ON = false;

const override = import.meta.env.PUBLIC_DIRECT_DOWNLOADS;
export const DIRECT_DOWNLOADS: boolean = override === 'true' ? true : override === 'false' ? false : DIRECT_DOWNLOADS_ON;
