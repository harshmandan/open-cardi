<script lang="ts">
	import { controls, setBrightness } from '$lib/state/controls.svelte';
	import { ICON_BRIGHTNESS } from './icons.svelte';

	const zone = $derived(controls.zones[controls.activeZone]);

	// Track smooth fractional value during drag so the thumb glides freely;
	// rounded value gets stored / displayed.
	let dragging = $state<number | null>(null);
	const value = $derived(dragging ?? zone.brightness);
	// State is clamped to [50, 100] for the wire (low values are visible in the
	// car but render near-black in the UI). Display 0–100 so the label keeps
	// its full familiar range.
	const display = $derived(Math.round((value - 50) * 2));

	function onInput(e: Event) {
		const t = e.target as HTMLInputElement;
		const v = Number(t.value);
		dragging = v;
		void setBrightness(Math.round(v));
	}

	function onChange() {
		dragging = null;
	}
</script>

<div class="flex items-center gap-12">
	<span class="size-16 shrink-0 opacity-70" aria-hidden="true">{@html ICON_BRIGHTNESS}</span>
	<input
		type="range"
		min="50"
		max="100"
		step="any"
		{value}
		oninput={onInput}
		onchange={onChange}
		class="bright-slider"
		aria-label="Brightness {display}%"
	/>
	<span
		class="brightness-num flex items-center justify-center border border-foreground text-12 font-500 tabular-nums shrink-0"
		style:background-color={`rgb(255 255 255 / ${display / 5}%)`}
	>
		{display}%
	</span>
</div>

<style>
	.brightness-num {
		width: 44px;
		min-width: 44px;
		height: 44px;
	}
	.bright-slider {
		appearance: none;
		-webkit-appearance: none;
		flex: 1;
		min-width: 0;
		height: 44px;
		background: transparent;
		outline: none;
		cursor: pointer;
		touch-action: pan-y;
	}
	.bright-slider::-webkit-slider-runnable-track {
		height: 2px;
		background: rgb(255 255 255 / 0.3);
		border: 0;
	}
	.bright-slider::-moz-range-track {
		height: 2px;
		background: rgb(255 255 255 / 0.3);
		border: 0;
	}
	.bright-slider::-webkit-slider-thumb {
		appearance: none;
		-webkit-appearance: none;
		margin-top: -21px;
		width: 4px;
		height: 44px;
		background: #fff;
		border: 1px solid #000;
		cursor: grab;
	}
	.bright-slider::-moz-range-thumb {
		width: 4px;
		height: 44px;
		background: #fff;
		border: 1px solid #000;
		border-radius: 0;
		cursor: grab;
	}
</style>
