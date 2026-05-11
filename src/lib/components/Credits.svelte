<script lang="ts">
	import { ICON_GITHUB } from './icons.svelte';
</script>

<footer class="flex items-center justify-between gap-12 pt-12 text-12 opacity-70">
	<span>
		by
		<a
			href="https://harsh.ink"
			target="_blank"
			rel="noopener noreferrer"
			class="font-500 hover:underline"
		>
			Harsh Mandan ↗
		</a>
	</span>
	<a
		href="https://github.com/harshmandan/open-cardi"
		target="_blank"
		rel="noopener noreferrer"
		aria-label="Star on GitHub"
		class="flex shrink-0 items-center gap-8 border border-foreground/15 px-10 py-6 text-16 font-500 hover:bg-foreground/5"
	>
		<span>star on</span>
		<span class="size-14">{@html ICON_GITHUB}</span>
	</a>
</footer>
