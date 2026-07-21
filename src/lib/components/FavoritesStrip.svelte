<script lang="ts">
	import { rgbToHex } from '$lib/protocol/color';
	import {
		applyFavorite,
		controls,
		removeFavorite,
		saveCurrentAsFavorite
	} from '$lib/state/controls.svelte';
	import type { Favorite } from '$lib/state/controls.svelte';
	import { ICON_PLUS, ICON_STAR } from './icons.svelte';

	const HOLD_MS = 3000;

	const zone = $derived(controls.zones[controls.activeZone]);

	let deletingId = $state<string | null>(null);
	let deleteTimer: number | null = null;

	function isSelected(fav: Favorite): boolean {
		return (
			zone.mode === 'color' &&
			zone.color.r === fav.color.r &&
			zone.color.g === fav.color.g &&
			zone.color.b === fav.color.b &&
			zone.brightness === fav.brightness
		);
	}

	function startHold(fav: Favorite) {
		cancelHold();
		deletingId = fav.id;
		deleteTimer = window.setTimeout(() => {
			removeFavorite(fav.id);
			deletingId = null;
			deleteTimer = null;
		}, HOLD_MS);
	}

	function cancelHold() {
		if (deleteTimer !== null) {
			clearTimeout(deleteTimer);
			deleteTimer = null;
		}
		deletingId = null;
	}

	function onContext(e: MouseEvent, fav: Favorite) {
		e.preventDefault();
		removeFavorite(fav.id);
	}
</script>

<div class="flex items-center gap-12">
	<span class="size-16 shrink-0 opacity-70" aria-hidden="true">{@html ICON_STAR}</span>
	<div
		class="hide-scrollbar flex min-w-0 flex-1 items-center gap-8 overflow-x-auto py-4"
		style="margin-right: -16px;"
	>
		<button
			type="button"
			onclick={saveCurrentAsFavorite}
			aria-label="Save current as favorite"
			class="favorite favorite-add shrink-0"
		>
			<span class="size-12 opacity-70">{@html ICON_PLUS}</span>
		</button>

		{#each controls.favorites as fav (fav.id)}
			<button
				type="button"
				onclick={() => applyFavorite(fav)}
				oncontextmenu={(e) => onContext(e, fav)}
				onpointerdown={() => startHold(fav)}
				onpointerup={cancelHold}
				onpointerleave={cancelHold}
				onpointercancel={cancelHold}
				title="Tap to apply · hold 3s or right-click to remove"
				class="favorite shrink-0"
				class:selected={isSelected(fav)}
				class:deleting={deletingId === fav.id}
			>
				<div
					class="fav-color"
					style:background-color={rgbToHex(fav.color)}
					style:opacity={fav.brightness / 100}
				></div>
				<div class="fav-num">
					<span aria-hidden="true">☀</span>{fav.brightness}%
				</div>
				<div class="delete-overlay" aria-hidden="true"></div>
			</button>
		{/each}
	</div>
</div>

<style>
	.favorite {
		position: relative;
		width: 44px;
		height: 44px;
		display: flex;
		flex-direction: column;
		border: 1px solid rgb(255 255 255 / 0.15);
		background: #000;
		overflow: hidden;
	}
	.favorite:hover {
		border-color: rgb(255 255 255 / 0.4);
	}
	.favorite.selected {
		outline: 1px solid #fff;
		outline-offset: 1px;
	}
	.fav-color {
		flex: 1;
		min-height: 0;
	}
	.fav-num {
		flex-shrink: 0;
		height: 16px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 2px;
		font-size: 9px;
		font-weight: 500;
		font-variant-numeric: tabular-nums;
		color: rgb(255 255 255 / 0.7);
		background: #000;
		line-height: 1;
	}
	.favorite-add {
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.delete-overlay {
		position: absolute;
		inset: 0;
		background: #ff0000;
		opacity: 0;
		pointer-events: none;
		transition: opacity 200ms linear;
		z-index: 2;
	}
	.favorite.deleting .delete-overlay {
		animation: fill-red 3s ease-in forwards;
	}
	@keyframes fill-red {
		0% {
			opacity: 0;
		}
		66.6667% {
			opacity: 1;
		}
		100% {
			opacity: 1;
		}
	}
</style>
