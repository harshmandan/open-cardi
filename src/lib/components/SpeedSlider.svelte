<script lang="ts">
	import { controls, setSpeed } from '$lib/state/controls.svelte';
	import { ICON_SPEED } from './icons.svelte';

	let dragging = $state<number | null>(null);
	const value = $derived(dragging ?? controls.speed);
	const display = $derived(Math.round(value));

	function onInput(e: Event) {
		const t = e.target as HTMLInputElement;
		const v = Number(t.value);
		dragging = v;
		void setSpeed(Math.round(v));
	}

	function onChange() {
		dragging = null;
	}
</script>

<div class="flex items-center gap-12">
	<span class="size-16 shrink-0 opacity-70" aria-hidden="true">{@html ICON_SPEED}</span>
	<input
		type="range"
		min="1"
		max="100"
		step="any"
		{value}
		oninput={onInput}
		onchange={onChange}
		class="speed-slider"
		aria-label="Pattern speed {display}"
	/>
	<span class="text-12 font-500 tabular-nums opacity-70 shrink-0 w-36 text-right">
		{display}
	</span>
</div>

<style>
	.speed-slider {
		appearance: none;
		-webkit-appearance: none;
		flex: 1;
		min-width: 0;
		height: 24px;
		background: transparent;
		outline: none;
		cursor: pointer;
		touch-action: none;
	}
	.speed-slider::-webkit-slider-runnable-track {
		height: 2px;
		background: rgb(255 255 255 / 0.3);
		border: 0;
	}
	.speed-slider::-moz-range-track {
		height: 2px;
		background: rgb(255 255 255 / 0.3);
		border: 0;
	}
	.speed-slider::-webkit-slider-thumb {
		appearance: none;
		-webkit-appearance: none;
		margin-top: -11px;
		width: 4px;
		height: 24px;
		background: #fff;
		border: 1px solid #000;
		cursor: grab;
	}
	.speed-slider::-moz-range-thumb {
		width: 4px;
		height: 24px;
		background: #fff;
		border: 1px solid #000;
		border-radius: 0;
		cursor: grab;
	}
</style>
