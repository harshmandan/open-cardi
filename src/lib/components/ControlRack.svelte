<script lang="ts">
	import BrightnessSlider from './BrightnessSlider.svelte';
	import FavoritesStrip from './FavoritesStrip.svelte';
	import HueSlider from './HueSlider.svelte';
	import SpeedSlider from './SpeedSlider.svelte';
	import {
		controls,
		setMaster,
		setMicMode,
		toggleMore,
		togglePatternMode
	} from '$lib/state/controls.svelte';
	import { ICON_CHEVRON, ICON_POWER, ICON_X } from './icons.svelte';
	import { MIC_MODES, PATTERNS } from '$lib/protocol/constants';

	const zone = $derived(controls.zones[controls.activeZone]);
	const isPattern = $derived(zone.mode === 'pattern');
	const micOn = $derived(controls.mic.on);

	const patternLabel = $derived(PATTERNS[zone.patternIndex]?.label ?? 'Pattern');
	const micLabel = $derived(MIC_MODES[controls.mic.mode]?.label ?? 'Voice');
</script>

<div class="flex flex-col gap-12">
	<div class:disabled={!controls.masterOn}>
		<div class="controls-area flex flex-col gap-12">
			{#if micOn}
				<div class="flex items-center gap-12">
					<button
						type="button"
						onclick={toggleMore}
						class="flex-1 min-w-0 border border-foreground/15 px-12 py-12 text-left text-12 font-500 uppercase tracking-wider truncate hover:bg-foreground/5"
					>
						<span class="opacity-50">Voice ·</span>
						{micLabel}
					</button>
					<button
						type="button"
						onclick={() => setMicMode(false)}
						aria-label="Disable voice / mic"
						class="size-20 shrink-0 opacity-70 hover:opacity-100"
					>
						{@html ICON_X}
					</button>
				</div>
			{:else if isPattern}
				<div class="flex items-center gap-12">
					<button
						type="button"
						onclick={toggleMore}
						class="flex-1 min-w-0 border border-foreground/15 px-12 py-12 text-left text-12 font-500 uppercase tracking-wider truncate hover:bg-foreground/5"
					>
						<span class="opacity-50">Pattern ·</span>
						{patternLabel}
					</button>
					<button
						type="button"
						onclick={togglePatternMode}
						aria-label="Disable pattern"
						class="size-20 shrink-0 opacity-70 hover:opacity-100"
					>
						{@html ICON_X}
					</button>
				</div>
				<SpeedSlider />
			{:else}
				<FavoritesStrip />
				<HueSlider />
				<BrightnessSlider />
			{/if}
		</div>
	</div>

	<div class="flex items-stretch gap-8">
		<button
			type="button"
			onclick={toggleMore}
			disabled={!controls.masterOn}
			class={[
				'more-btn flex flex-1 items-center justify-center gap-8 px-12 text-12 font-500 uppercase tracking-wider transition-colors',
				controls.masterOn ? 'hover:bg-foreground/5' : 'opacity-40 cursor-not-allowed'
			]}
		>
			<span>{controls.moreOpen ? 'Hide' : 'More'}</span>
			<span
				class="chevron size-12 opacity-70"
				class:open={controls.moreOpen}
				aria-hidden="true"
			>
				{@html ICON_CHEVRON}
			</span>
		</button>

		<button
			type="button"
			onclick={() => setMaster(!controls.masterOn)}
			aria-label="Master power"
			aria-pressed={controls.masterOn}
			class={[
				'master-btn shrink-0 size-44 flex items-center justify-center border transition-colors',
				controls.masterOn
					? 'bg-green/15 text-green border-foreground/15'
					: 'bg-background text-red/70 border-foreground/15'
			]}
		>
			<span class="size-20">{@html ICON_POWER}</span>
		</button>
	</div>
</div>

<style>
	.controls-area {
		min-height: 168px;
		justify-content: flex-end;
	}
	.disabled {
		opacity: 0.4;
		pointer-events: none;
	}
	.more-btn {
		height: 44px;
	}
	.chevron {
		display: inline-flex;
		transform: rotate(-90deg);
		transition: transform 200ms ease-out;
	}
	@media (min-width: 768px) {
		.chevron {
			transform: rotate(0deg);
		}
		.chevron.open {
			transform: rotate(180deg);
		}
	}
</style>
