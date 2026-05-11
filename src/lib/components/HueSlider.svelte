<script lang="ts">
	import { hslToRgb, rgbToHex, rgbToHsl } from '$lib/protocol/color';
	import { controls, setColor } from '$lib/state/controls.svelte';
	import { ICON_HUE } from './icons.svelte';

	const zone = $derived(controls.zones[controls.activeZone]);
	const storedHue = $derived(rgbToHsl(zone.color).h);
	const swatch = $derived(rgbToHex(zone.color));

	let dragging = $state<number | null>(null);
	const value = $derived(dragging ?? storedHue);

	function onInput(e: Event) {
		const t = e.target as HTMLInputElement;
		const h = Number(t.value);
		dragging = h;
		void setColor(hslToRgb(h, 1, 0.5));
	}

	function onChange() {
		dragging = null;
	}
</script>

<div class="flex items-center gap-12">
	<span class="size-16 shrink-0 opacity-70" aria-hidden="true">{@html ICON_HUE}</span>
	<input
		type="range"
		min="0"
		max="359"
		step="any"
		{value}
		oninput={onInput}
		onchange={onChange}
		class="hue-slider"
		aria-label="Hue {Math.round(value)}°"
	/>
	<span
		class="shrink-0 size-44 border border-foreground/15"
		style:background-color={swatch}
		aria-hidden="true"
	></span>
</div>

<style>
	.hue-slider {
		appearance: none;
		-webkit-appearance: none;
		flex: 1;
		min-width: 0;
		height: 44px;
		background: linear-gradient(
			to right,
			#ff0000,
			#ffff00,
			#00ff00,
			#00ffff,
			#0000ff,
			#ff00ff,
			#ff0000
		);
		outline: none;
		cursor: pointer;
		touch-action: pan-y;
	}
	.hue-slider::-webkit-slider-thumb {
		appearance: none;
		-webkit-appearance: none;
		width: 4px;
		height: 44px;
		background: #fff;
		border: 1px solid #000;
		cursor: grab;
	}
	.hue-slider::-moz-range-thumb {
		width: 4px;
		height: 44px;
		background: #fff;
		border: 1px solid #000;
		border-radius: 0;
		cursor: grab;
	}
</style>
