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
├── scripts/check-links.mjs post-build link and anchor checker (part of pnpm build)
├── public/favicon.svg
└── src/
    ├── assets/             logo, screenshots and scenes (optimized by astro:assets)
    ├── components/         Hero and PageTitle overrides, the home page's sections, the tour, theme cards
    ├── content/docs/       the pages (.md / .mdx); the folder structure is the URL structure
    ├── home.ts             whether the home page shows the tour
    └── styles/
        ├── tokens.css      the site's colors and type, light and dark (--s-*)
        └── site.css        the look: Starlight mapped to the tokens, and the app's palette (--k-*)
```

## The look

The home page opens on a scene from the app's own shaders: `mountains` in the light theme, `pond` in
the dark one, and the page under it is the color the scene fades into. The pictures in
`src/assets/scenes/` are drawn from the shader files in `app/src/main/assets/shaders/`, so they are
the app's scenes, not illustrations of them. Headings are in Bricolage Grotesque, text in Instrument
Sans, and JetBrains Mono is kept for code, commands and what a phone shows. A phone's screen and the
theme cards keep the app's own colors.

The scroll tour (`components/Tour.astro`) is a module: it has a page of its own, `/tour/`, and
`src/home.ts` decides whether the home page shows it too.

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
