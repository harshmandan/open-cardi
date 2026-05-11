<script lang="ts">
	import type { Rgb } from '$lib/protocol/commands';
	import { hslToRgb, rgbToHex, rgbToHsl } from '$lib/protocol/color';
	import { controls, setColor, setWarmWhite } from '$lib/state/controls.svelte';
	import { ICON_HUE } from './icons.svelte';

	// Slider t ∈ [0, 100]. Three discrete regions:
	//   [0, 5)   → white         (cmd.setColor(255, 255, 255))
	//   [5, 10)  → warm white    (cmd.setWarmWhite — separate HW channel)
	//   [10, 100] → hue ramp     (hue 0°→360°, red on both ends)
	// White and warm-white are flat blocks; sub-position within them doesn't
	// change the emitted color. Orange falls between t=10 (red) and t=25 (yellow).
	const WHITE: Rgb = { r: 255, g: 255, b: 255 };
	const S1 = 5;
	const S2 = 10;

	function sliderToHueRgb(t: number): Rgb {
		const h = ((t - S2) / (100 - S2)) * 360;
		return hslToRgb(h, 1, 0.5);
	}

	function zoneToSliderT(): number {
		if (zone.mode === 'warmwhite') return (S1 + S2) / 2;
		const { r, g, b } = zone.color;
		if (r === 255 && g === 255 && b === 255) return S1 / 2;
		const { h } = rgbToHsl({ r, g, b });
		const hh = h === 0 ? 360 : h;
		return S2 + (hh / 360) * (100 - S2);
	}

	const zone = $derived(controls.zones[controls.activeZone]);
	const storedT = $derived(zoneToSliderT());
	const swatch = $derived(rgbToHex(zone.color));

	let dragging = $state<number | null>(null);
	const value = $derived(dragging ?? storedT);

	function onInput(e: Event) {
		const t = Number((e.target as HTMLInputElement).value);
		dragging = t;
		if (t < S1) void setColor(WHITE);
		else if (t < S2) void setWarmWhite();
		else void setColor(sliderToHueRgb(t));
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
		max="100"
		step="any"
		{value}
		oninput={onInput}
		onchange={onChange}
		class="hue-slider"
		aria-label="Color"
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
			#ffffff 0%,
			#ffc882 5%,
			#ff0000 10%,
			#ffff00 25%,
			#00ff00 40%,
			#00ffff 55%,
			#0000ff 70%,
			#ff00ff 85%,
			#ff0000 100%
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
