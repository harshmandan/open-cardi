<script lang="ts">
	import { fly, fade } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import Credits from './Credits.svelte';
	import MicModeSelector from './MicModeSelector.svelte';
	import PatternPicker from './PatternPicker.svelte';
	import ShowAllZonesToggle from './ShowAllZonesToggle.svelte';
	import { controls, toggleMore } from '$lib/state/controls.svelte';
	import { ICON_CHEVRON } from './icons.svelte';

	let { mode = 'sheet' }: { mode?: 'sheet' | 'sidebar' } = $props();
</script>

<style>
	.chevron-down {
		display: inline-flex;
		transform: rotate(90deg);
	}
</style>

{#if mode === 'sidebar'}
	{#if controls.moreOpen}
		<aside
			class="flex h-full w-full flex-col gap-20 bg-background p-16 overflow-y-auto"
			transition:fly={{ x: 300, duration: 250, easing: cubicOut }}
		>
			<PatternPicker />
			<MicModeSelector />
			<ShowAllZonesToggle />
			<Credits />
		</aside>
	{/if}
{:else if controls.moreOpen}
	<div
		class="fixed inset-0 z-50 flex items-end"
		role="dialog"
		aria-modal="true"
	>
		<button
			type="button"
			aria-label="Close"
			onclick={toggleMore}
			transition:fade={{ duration: 150 }}
			class="absolute inset-0 cursor-default bg-black/60 backdrop-blur-xs"
		></button>

		<div
			class="relative w-full max-h-[80dvh] bg-background border-t border-foreground/10 flex flex-col"
			transition:fly={{ y: 400, duration: 220, easing: cubicOut }}
		>
			<div class="mx-auto w-full max-w-720 shrink-0 flex items-center justify-center p-16">
				<button
					type="button"
					onclick={toggleMore}
					aria-label="Close"
					class="chevron-down size-20 opacity-70 hover:opacity-100"
				>
					{@html ICON_CHEVRON}
				</button>
			</div>
			<div class="mx-auto w-full max-w-720 flex-1 min-h-0 overflow-y-auto flex flex-col gap-16 px-16 pb-16">
				<PatternPicker />
				<MicModeSelector />
				<ShowAllZonesToggle />
				<Credits />
			</div>
		</div>
	</div>
{/if}
