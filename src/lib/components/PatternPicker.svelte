<script lang="ts">
	import { slide } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { controls, setPattern, togglePatternMode } from '$lib/state/controls.svelte';
	import { PATTERNS } from '$lib/protocol/constants';
	import SpeedSlider from './SpeedSlider.svelte';

	const zone = $derived(controls.zones[controls.activeZone]);
	const isPattern = $derived(zone.mode === 'pattern');
</script>

<div class="flex flex-col gap-12">
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 uppercase tracking-wider opacity-50">Pattern</span>
		<button
			type="button"
			role="switch"
			aria-checked={isPattern}
			aria-label="Pattern mode"
			onclick={togglePatternMode}
			class="flex items-center gap-8 border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5"
		>
			<span class={['size-8', isPattern ? 'bg-green' : 'bg-foreground/30']}></span>
			<span class="inline-block text-center" style="min-width: 3ch">
				{isPattern ? 'on' : 'off'}
			</span>
		</button>
	</div>

	{#if isPattern}
		<div
			class="flex flex-col gap-12"
			transition:slide={{ duration: 220, easing: cubicOut }}
		>
			<SpeedSlider />

			<div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
				{#each PATTERNS as p (p.index)}
					{@const active = zone.patternIndex === p.index}
					<button
						type="button"
						onclick={() => setPattern(p.index)}
						class={[
							'border px-10 py-6 text-left text-12 font-500 transition-colors',
							active
								? 'border-foreground bg-foreground text-background'
								: 'border-foreground/15 hover:bg-foreground/5'
						]}
					>
						{p.label}
					</button>
				{/each}
			</div>
		</div>
	{/if}
</div>
