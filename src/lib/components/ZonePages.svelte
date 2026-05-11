<script lang="ts">
	import { onMount } from 'svelte';
	import InteriorScene from './InteriorScene.svelte';
	import { controls, selectZone } from '$lib/state/controls.svelte';
	import { ZONES, ZONES_HIDDEN_BY_DEFAULT, type ZoneId } from '$lib/protocol/constants';

	const visibleZones = $derived(
		controls.showAllZones
			? ZONES
			: ZONES.filter((z) => !ZONES_HIDDEN_BY_DEFAULT.includes(z.id))
	);

	let scroller: HTMLDivElement;
	let scrollFraction = $state(0);
	let cellWidth = $state(220);

	function onScroll() {
		if (!scroller) return;
		const w = scroller.clientWidth;
		if (w <= 0) return;
		scrollFraction = scroller.scrollLeft / w;
	}

	onMount(() => {
		const mq = window.matchMedia('(min-width: 768px)');
		const updateCell = () => {
			cellWidth = mq.matches ? 260 : 220;
		};
		updateCell();
		mq.addEventListener('change', updateCell);

		// Land on the default zone (set by state) instead of the leftmost page.
		// Wait one frame so the scroller has a width to compute against.
		requestAnimationFrame(() => {
			if (!scroller) return;
			const idx = visibleZones.findIndex((z) => z.id === controls.activeZone);
			if (idx > 0) {
				scroller.scrollTo({ left: idx * scroller.clientWidth, behavior: 'instant' });
			}
		});

		return () => mq.removeEventListener('change', updateCell);
	});

	// Re-observe pages whenever the visible zone list changes (toggle show-all).
	$effect(() => {
		visibleZones; // dependency
		if (!scroller) return;
		const observer = new IntersectionObserver(
			(entries) => {
				const visible = entries
					.filter((e) => e.isIntersecting)
					.sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
				if (visible) {
					const id = (visible.target as HTMLElement).dataset.zone as ZoneId;
					if (id && id !== controls.activeZone) selectZone(id);
				}
			},
			{ root: scroller, threshold: [0.5, 0.75, 1] }
		);
		const pages = scroller.querySelectorAll('[data-zone]');
		pages.forEach((p) => observer.observe(p));
		return () => observer.disconnect();
	});
</script>

<div class="zone-pages flex flex-col flex-1 min-h-0">
	<div class="zone-frame anim-enter anim-enter-1">
		<div class="zone-subtext">Zone</div>
		<div class="title-bar">
			<div
				class="title-track"
				style:transform={`translateX(${-((scrollFraction + 0.5) * cellWidth)}px)`}
			>
				{#each visibleZones as zone, i (zone.id)}
					{@const dist = Math.abs(i - scrollFraction)}
					{@const opacity = Math.max(0.12, 1 - dist * 0.9)}
					<div class="title-cell" style:opacity style:width="{cellWidth}px">
						{zone.label}
					</div>
				{/each}
			</div>
		</div>
	</div>

	<div bind:this={scroller} onscroll={onScroll} class="scroller hide-scrollbar">
		{#each visibleZones as zone (zone.id)}
			<section data-zone={zone.id} class="zone-page">
				<div class="hero">
					<div class="anim-enter anim-enter-1 w-full flex justify-center">
						<InteriorScene pageZone={zone.id} />
					</div>
				</div>
			</section>
		{/each}
	</div>
</div>

<style>
	.zone-frame {
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		margin-top: 54px;
	}
	.zone-subtext {
		font-size: var(--text-10);
		font-weight: 500;
		letter-spacing: 0.15em;
		text-transform: uppercase;
		color: rgb(255 255 255 / 0.4);
	}
	.title-bar {
		overflow: hidden;
		position: relative;
		height: 32px;
		width: 100%;
	}
	.title-track {
		position: absolute;
		top: 0;
		left: 50%;
		height: 100%;
		display: flex;
		align-items: center;
		will-change: transform;
	}
	.title-cell {
		flex-shrink: 0;
		text-align: center;
		font-size: var(--text-18);
		letter-spacing: 0.04em;
		font-weight: 600;
		white-space: nowrap;
		transition: opacity 80ms linear;
	}
	.scroller {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: 100%;
		overflow-x: auto;
		overflow-y: hidden;
		scroll-snap-type: x mandatory;
		scroll-behavior: smooth;
		flex: 1;
		min-height: 0;
	}
	.zone-page {
		scroll-snap-align: center;
		scroll-snap-stop: always;
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		min-height: 0;
	}
	.hero {
		flex: 1;
		min-height: 0;
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}
</style>
