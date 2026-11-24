// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { satteri } from '@astrojs/markdown-satteri';

// Where the site is published. For a fork, change these two values (or set the env vars):
//   SITE  https://<github-user>.github.io
//   BASE  /<repository-name>        (use '/' for a custom domain or a <user>.github.io repo)
const SITE = process.env.DOCS_SITE ?? 'https://mtwabpin.github.io';
const BASE = process.env.DOCS_BASE ?? '/kolahal';
const REPO = 'https://github.com/mtwabpin/kolahal';
// The community's Discord server: questions, ideas and help.
const DISCORD = 'https://discord.gg/3D25w6gXR5';

/**
 * Pages link to each other site-absolute (`/reference/commands/`); this adds the base path at
 * build time. Relative links would break whenever a page is opened without its trailing slash.
 */
function withBase(path) {
	const base = BASE.replace(/\/$/, '');
	if (!path.startsWith('/') || path.startsWith('//') || path === base || path.startsWith(base + '/')) return path;
	return base + path;
}
/** Whether a link leaves the site: an address on another host, or outside the base on this one. */
function leavesSite(href) {
	if (!/^https?:\/\//.test(href)) return false;
	const here = SITE.replace(/\/$/, '') + BASE.replace(/\/$/, '');
	return href !== here && !href.startsWith(here + '/') && !href.startsWith(here + '#');
}
/**
 * Sätteri (Astro's Markdown processor) hast plugin: runs on every Markdown / MDX link. It adds the
 * base to site-absolute links, and opens links that leave the site in a new tab (src/links.ts).
 */
const baseLinks = {
	name: 'base-links',
	element: {
		filter: ['a'],
		visit(node, ctx) {
			const href = node.properties?.href;
			if (typeof href !== 'string') return;
			if (withBase(href) !== href) ctx.setProperty(node, 'href', withBase(href));
			if (leavesSite(href)) {
				ctx.setProperty(node, 'target', '_blank');
				ctx.setProperty(node, 'rel', 'noopener');
			}
		},
	},
};

export default defineConfig({
	site: SITE,
	base: BASE,
	markdown: { processor: satteri({ hastPlugins: [baseLinks] }) },
	integrations: [
		starlight({
			title: 'Kolahal',
			description: 'A touch and type optimized launcher for Android, with scripts and widgets in Lua 5.4 or JavaScript, and work profile support.',
			logo: { src: './src/assets/logo.svg', replacesTitle: false },
			favicon: '/favicon.svg',
			social: [
				{ icon: 'github', label: 'GitHub', href: REPO },
				{ icon: 'discord', label: 'Discord', href: DISCORD },
			],
			editLink: { baseUrl: `${REPO}/edit/master/site/` },
			customCss: [
				'@fontsource-variable/bricolage-grotesque/index.css',
				'@fontsource-variable/instrument-sans/index.css',
				'@fontsource/jetbrains-mono/400.css',
				'@fontsource/jetbrains-mono/600.css',
				'@fontsource/jetbrains-mono/700.css',
				'./src/styles/tokens.css',
				'./src/styles/site.css',
			],
			components: {
				Banner: './src/components/Banner.astro',
				EditLink: './src/components/EditLink.astro',
				Hero: './src/components/Hero.astro',
				PageTitle: './src/components/PageTitle.astro',
				SocialIcons: './src/components/SocialIcons.astro',
			},
			expressiveCode: {
				// One dark theme in both site themes: code blocks are dark on --s-code either way.
				themes: ['github-dark-dimmed'],
				// Plain frames: no terminal window, no dots. A block's title is its small label.
				defaultProps: { frame: 'code' },
				styleOverrides: {
					borderRadius: 'var(--s-radius-m)',
					borderColor: 'transparent',
					codeBackground: 'var(--s-code)',
					codeForeground: 'var(--s-on-code)',
					codeFontFamily: 'var(--s-font-mono)',
					codeFontSize: '0.90625rem',
					codeLineHeight: '1.6',
					codePaddingBlock: '1rem',
					codePaddingInline: '1.125rem',
					uiFontFamily: 'var(--s-font-text)',
					uiFontSize: '0.8125rem',
					uiFontWeight: '600',
					focusBorder: 'var(--s-link)',
					scrollbarThumbColor: 'var(--s-code-rule)',
					scrollbarThumbHoverColor: 'var(--s-code-label)',
					frames: {
						frameBoxShadowCssValue: 'none',
						editorBackground: 'var(--s-code)',
						editorTabBarBackground: 'var(--s-code)',
						editorTabBarBorderColor: 'transparent',
						editorTabBarBorderBottomColor: 'transparent',
						editorTabBorderRadius: '0',
						editorTabsMarginInlineStart: '0',
						editorTabsMarginBlockStart: '0',
						editorActiveTabBackground: 'var(--s-code)',
						editorActiveTabForeground: 'var(--s-code-label)',
						editorActiveTabBorderColor: 'transparent',
						editorActiveTabIndicatorHeight: '0',
						editorActiveTabIndicatorTopColor: 'transparent',
						editorActiveTabIndicatorBottomColor: 'transparent',
						terminalBackground: 'var(--s-code)',
						terminalTitlebarBackground: 'var(--s-code)',
						terminalTitlebarForeground: 'var(--s-code-label)',
						terminalTitlebarBorderBottomColor: 'var(--s-code-rule)',
						terminalTitlebarDotsOpacity: '0',
						// The copy button: quiet, in the label's color.
						inlineButtonForeground: 'var(--s-code-label)',
						inlineButtonBorder: 'var(--s-code-rule)',
						inlineButtonBorderOpacity: '1',
						inlineButtonBackground: 'var(--s-code-rule)',
						tooltipSuccessBackground: 'var(--s-wash)',
						tooltipSuccessForeground: 'var(--s-ink)',
					},
				},
			},
			sidebar: [
				{
					label: 'Getting started',
					items: [
						{ slug: 'getting-started/install' },
						{ slug: 'getting-started/play-testing' },
						{ slug: 'getting-started/setup' },
						{ slug: 'getting-started/parts' },
						{ slug: 'getting-started/basics' },
						{ slug: 'getting-started/gallery' },
					],
				},
				{
					label: 'Guides',
					items: [
						{ slug: 'guides/apps' },
						{ slug: 'guides/notifications' },
						{ slug: 'guides/media-and-controls' },
						{ slug: 'guides/feeds' },
						{ slug: 'guides/people' },
						{ slug: 'guides/productivity' },
						{ slug: 'guides/text-editor' },
						{ slug: 'guides/web-and-calculator' },
						{ slug: 'guides/widgets' },
						{ slug: 'guides/gestures' },
						{ slug: 'guides/themes-and-settings' },
						{ slug: 'guides/backup' },
						{ slug: 'guides/profiles' },
					],
				},
				{
					label: 'Scripting',
					items: [
						{ slug: 'scripting/overview' },
						{ slug: 'scripting/api' },
						{ slug: 'scripting/library' },
						{ slug: 'scripting/examples' },
						{ slug: 'scripting/editor' },
						{ slug: 'scripting/shaders' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ slug: 'reference/commands' },
						{ slug: 'reference/config' },
						{ slug: 'reference/privacy' },
						{ slug: 'reference/security' },
						{ slug: 'reference/faq' },
					],
				},
			],
		}),
	],
});
