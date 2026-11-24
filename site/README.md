# Kolahal website

The website and user guide for Kolahal, built with [Astro](https://astro.build) and
[Starlight](https://starlight.astro.build). It is published to GitHub Pages by
`.github/workflows/site.yml` on every push to `master` that touches `site/`. (The maintainer's
own notes, on the code and on releases, are in `docs/` at the repository root; they aren't part of
the site.)

```sh
cd site
pnpm install
pnpm dev          # http://localhost:4321/kolahal/
pnpm build        # static site in dist/, then checks every internal link
pnpm preview      # serve dist/
```

## Layout

```
site/
├── astro.config.mjs        site URL, base path, sidebar, theme settings
├── CAPTURES.md             the captures of the app the site needs, and how to take each one
├── scripts/check-links.mjs post-build link, anchor and new-tab checker (part of pnpm build)
├── public/favicon.svg
└── src/
    ├── assets/             logo, screenshots and scenes (optimized by astro:assets)
    │   └── shots/          the captures of the app by name, one folder for each part (CAPTURES.md)
    ├── components/         Starlight overrides, the home page's sections, the tour's and gallery's parts
    ├── content/docs/       the pages (.md / .mdx); the folder structure is the URL structure
    ├── data/themes.json    the app's built-in themes, exported from the app (tools/export-themes.mjs)
    ├── gallery.ts          the gallery's pictures, one line each
    ├── links.ts            links that leave the site open in a new tab
    ├── shots.ts            pictures by name, with a stand-in until the capture is there
    └── styles/
        ├── tokens.css      the site's colors and type, light and dark (--s-*)
        └── site.css        the look: Starlight mapped to the tokens, and the app's palette (--k-*)
```

## The look

The home page opens on a scene from the app's own shaders, which fades to the next every few
seconds: `mountains`, `pond` and `dunes`, drawn in the site's light or dark theme, with a phone
showing home over the same scene. The page under it is the color the scene fades into. The pictures in
`src/assets/scenes/` are drawn from the shader files in `app/src/main/assets/shaders/`, so they are
the app's scenes, not illustrations of them: `tools/site-scenes.sh` draws them all again. Headings
are in Bricolage Grotesque, text in Instrument Sans, and JetBrains Mono is kept for code, commands and
what a phone shows. A phone's screen and the theme cards keep the app's own colors, from
`src/data/themes.json`. How every picture is made again, in this theme or another:
`docs/pictures.org` at the repository's root.

The tour (`/tour/`) is a page of short parts, each with screenshots, a command to try and a link to
the guide. Its pictures, the gallery's and the hero's phone are taken by name from
`src/assets/shots/` (`src/shots.ts`): until a capture is there, an older picture stands in, or the
page shows one picture fewer. `CAPTURES.md` lists every capture, how to take it, and which are still
stand-ins.

## Adding a picture to the gallery

1. Put the picture in `src/assets/` (a new capture in `src/assets/shots/<part>/`, as a PNG).
2. Add one line for it to `src/gallery.ts`, under its section, in the guide's order:
   `{ section: 'widgets', file: 'shots/widgets/page.png', caption: 'The widgets page', alt: 'What it shows', href: '/guides/widgets/#the-widgets-page' },`
3. `pnpm build` checks the link; the picture's size comes from its file.

Links that leave the site open in a new tab: the Markdown link plugin in `astro.config.mjs` does it
for the pages' text, and components use `external()` from `src/links.ts`. `pnpm build` fails on a
link to another site that doesn't.

Links between pages start at the site root without the base (`/reference/commands/#widgets`);
a small plugin in `astro.config.mjs` adds the base, so the site works under any base path and from
URLs with or without the trailing slash. Do not write relative links (`../commands/`): they break
when a page is opened without its slash, and `pnpm build` fails on them, as it does on any broken
link or anchor.

## Hosting somewhere else

`site` and `base` are at the top of `astro.config.mjs`:

```js
const SITE = process.env.DOCS_SITE ?? 'https://mtwabpin.github.io';
const BASE = process.env.DOCS_BASE ?? '/kolahal';
```

For a fork, change them to `https://<user>.github.io` and `/<repo>`, or set `DOCS_SITE` /
`DOCS_BASE` when building. Use `BASE = '/'` for a custom domain or a `<user>.github.io` repository.
The link checker reads `DOCS_BASE` too.

In the repository's **Settings → Pages**, set the source to **GitHub Actions** once.

Dependency versions are pinned exactly in `package.json`, and `pnpm-lock.yaml` is committed;
CI installs with `pnpm install --frozen-lockfile`. The pnpm version is pinned by
`packageManager` in `package.json`; run `corepack enable` once if `pnpm` is not on your PATH.
