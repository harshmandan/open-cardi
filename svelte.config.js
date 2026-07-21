import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	compilerOptions: {
		// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
		runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
	},
	kit: {
		// Static site for GitHub Pages. SPA fallback since the app is client-rendered (Web Bluetooth).
		adapter: adapter({ fallback: '404.html' }),
		paths: {
			// Empty base: the app is served at the root of opencardi.harsh.ink, where a Cloudflare
			// worker rewrites "/…" → "/open-cardi/…" onto GitHub Pages.
			base: process.env.BASE_PATH ?? '',
			// Relative asset + service-worker URLs (./…) so the same build works at the public root
			// (opencardi.harsh.ink/) AND at the raw project path (…github.io/open-cardi/) — the SW
			// registers relative to the document, so its scope is correct on both.
			relative: true
		}
	}
};

export default config;
