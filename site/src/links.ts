/**
 * Links that leave the site open in a new tab; links within it don't. The Markdown pages get this
 * from the link plugin in astro.config.mjs, the header's icons and the edit link from their
 * components (SocialIcons.astro, EditLink.astro), and the site's other components spread
 * `external(href)` on their links. `pnpm build` fails on a link that leaves the site without it.
 */
export function external(href: string): { target?: string; rel?: string } {
	return /^https?:\/\//.test(href) ? { target: '_blank', rel: 'noopener' } : {};
}
