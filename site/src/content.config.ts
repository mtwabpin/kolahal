import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { z } from 'astro/zod';
import { docsSchema } from '@astrojs/starlight/schema';
import { DIRECT_DOWNLOADS } from './release';

/**
 * Pages with an alpha-test twin: `<page>.alpha.mdx` has `slug: <page>` and replaces `<page>.mdx` while
 * DIRECT_DOWNLOADS is off (release.ts). While it is on, the twins are left out and the site is as before.
 */
const ALPHA_TWINS = ['getting-started/install'];

/** Starlight's docsLoader (every Markdown and MDX file not starting with `_`), with the twins swapped in or out. */
const pattern = [
	'**/[^_]*.{md,mdx}',
	...(DIRECT_DOWNLOADS ? ['!**/*.alpha.{md,mdx}'] : ALPHA_TWINS.map((page) => `!${page}.{md,mdx}`)),
];

/** Starlight's front matter, and `date`: when a blog post came out, shown under its title (PageTitle.astro). */
const schema = docsSchema({ extend: z.object({ date: z.coerce.date().optional() }) });

export const collections = {
	docs: defineCollection({ loader: glob({ base: './src/content/docs', pattern }), schema }),
};
