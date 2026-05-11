<script lang="ts">
	import { controls } from '$lib/state/controls.svelte';
	import { rgbToHex } from '$lib/protocol/color';
	import type { ZoneId } from '$lib/protocol/constants';

	let { pageZone }: { pageZone: ZoneId } = $props();

	type GlowDef = {
		d: string;
		sourceZone: ZoneId;
		kind?: 'stroke' | 'fill';
	};
	type PageVisual = {
		mode?: 'image' | 'blob';
		aspectRatio: string;
		// image mode fields
		image?: string;
		viewBox?: string;
		blur?: number;
		glowWidth?: number;
		coreWidth?: number;
		glows?: GlowDef[];
	};

	// All current images export at 2752×1536, so they share the same shape config.
	const SHARED = {
		viewBox: '0 0 2752 1536',
		aspectRatio: '2752 / 1536',
		blur: 80,
		glowWidth: 6,
		coreWidth: 1.5
	};

	// overall.webp shows every interior strip; guide & floor reuse this view
	// so users see how their change affects the whole cabin.
	const OVERALL_GLOWS: GlowDef[] = [
		// left + right door strips visible from back-seat POV
		{ d: 'M-1.5 907.5L552 730M2200 733.5L2759 916.5', sourceZone: 'doors' },
		// front dashboard strip, broken by the center screen and trim
		{
			d: 'M604.5 717.5H655.5M723 717.5L1003 720M1054 718.5H1131M1615 721L2153 717',
			sourceZone: 'guide'
		},
		// driver footwell pool
		{
			d: 'M751 1015.5H836.5H1233L1175 1253.5L793.5 1238.5L751 1015.5Z',
			sourceZone: 'floor',
			kind: 'fill'
		},
		// passenger footwell pool
		{
			d: 'M1990.5 1015.5H1528.5L1576.5 1240.5L1943.5 1234.5L1990.5 1015.5Z',
			sourceZone: 'floor',
			kind: 'fill'
		}
	];

	const OVERALL_VISUAL: PageVisual = {
		image: '/overall.webp',
		...SHARED,
		glows: OVERALL_GLOWS
	};

	const PAGE_VISUALS: Partial<Record<ZoneId, PageVisual>> = {
		overall: OVERALL_VISUAL,
		// Guide and Floor pages share the overall view — each changes only its
		// own zone, but the user sees the whole cabin context.
		guide: OVERALL_VISUAL,
		floor: OVERALL_VISUAL,
		doors: {
			image: '/door.webp',
			...SHARED,
			glows: [
				{ d: 'M181 1194.5L1247.5 676', sourceZone: 'doors' },
				{
					d: 'M1319 617L1652 631 M1758.5 636.5L2178.5 653.5 M2320.5 658L2384.5 660.5',
					sourceZone: 'guide'
				},
				{
					d: 'M2138.5 1547L2191 1171L1439.5 1081L1489.5 1547H2138.5Z',
					sourceZone: 'floor',
					kind: 'fill'
				}
			]
		},
		skylight: {
			image: '/skylight.webp',
			...SHARED,
			// thinner strokes + more blur — the moonroof outline is long and
			// straight so the default widths read as harsh lines
			glowWidth: 4,
			coreWidth: 1,
			blur: 110,
			glows: [
				{ d: 'M735 875.5L820.5 852M1932 850L2020 873.5', sourceZone: 'doors' },
				{
					d: 'M446 281.5L964.5 613.5M1787.5 613.5L2303.5 285M2552.5 132L2755.5 3.5M202 134L-1.5 8',
					sourceZone: 'skylight'
				}
			]
		},
		grill: {
			image: '/grill.webp',
			...SHARED,
			// grill light should bloom more — bump blur, slim core
			blur: 140,
			glowWidth: 6,
			coreWidth: 1.5,
			glows: [
				{
					d: 'M919 966.5L1015.5 968.5M1165.5 971H1615M1777 969.5L1863 967.5',
					sourceZone: 'grill'
				}
			]
		},
		// Hidden zones surfaced via "Show all zones" — these don't get a
		// dedicated photo, just a colored blob in the chosen color.
		'chassis-strobe': { mode: 'blob', aspectRatio: '2752 / 1536' },
		chassis: { mode: 'blob', aspectRatio: '2752 / 1536' },
		strobe: { mode: 'blob', aspectRatio: '2752 / 1536' }
	};

	const DEFAULT_VISUAL: PageVisual = OVERALL_VISUAL;

	const visual = $derived(PAGE_VISUALS[pageZone] ?? DEFAULT_VISUAL);

	function effective(id: ZoneId) {
		const z = controls.zones[id];
		return {
			color: rgbToHex(z.color),
			opacity: controls.masterOn ? z.brightness / 100 : 0
		};
	}

	// For blob mode the source zone is always the page's own zone.
	const blobFx = $derived(effective(pageZone));
</script>

<div class="scene" style:aspect-ratio={visual.aspectRatio}>
	{#if visual.mode === 'blob'}
		<div
			class="blob"
			style:--c={blobFx.color}
			style:--o={blobFx.opacity}
			aria-hidden="true"
		></div>
	{:else}
		{#if visual.image}
			<img src={visual.image} alt="" class="base" />
		{/if}

		{#if visual.glows && visual.glows.length > 0}
			<svg
				class="glow-svg"
				viewBox={visual.viewBox}
				preserveAspectRatio="none"
				aria-hidden="true"
			>
				<defs>
					<!--
						Stroke glow filter: generous filter region in userSpaceOnUse so
						the blur isn't cropped by thin paths' bounding boxes.
					-->
					<filter
						id="scene-glow-{pageZone}"
						filterUnits="userSpaceOnUse"
						x="-1500"
						y="-1500"
						width="6000"
						height="4500"
					>
						<feGaussianBlur stdDeviation={visual.blur} />
					</filter>
					<!--
						Fill glow filter: erode the polygon inward first, then blur the
						eroded version. Keeps the soft glow visibly inside the polygon
						shape instead of bleeding out to its boundary.
					-->
					<filter
						id="scene-fill-{pageZone}"
						filterUnits="userSpaceOnUse"
						x="-1500"
						y="-1500"
						width="6000"
						height="4500"
					>
						<feMorphology operator="erode" radius="30" />
						<feGaussianBlur stdDeviation="40" />
					</filter>
				</defs>
				{#each visual.glows as glow, i (i)}
					{@const fx = effective(glow.sourceZone)}
					{#if glow.kind === 'fill'}
						<!-- emissive fill: vertical gradient (light comes from the top
						     edge of the polygon where the LED strip sits, fading to
						     transparent at the bottom of the lit floor) + erode-then-blur
						     to soften the side edges + screen blend so the color tints
						     the photo instead of painting over it. -->
						<defs>
							<linearGradient
								id="fill-grad-{pageZone}-{i}"
								x1="0"
								y1="0"
								x2="0"
								y2="1"
							>
								<stop offset="0%" stop-color={fx.color} stop-opacity="1" />
								<stop offset="100%" stop-color={fx.color} stop-opacity="0" />
							</linearGradient>
						</defs>
						<path
							d={glow.d}
							fill="url(#fill-grad-{pageZone}-{i})"
							stroke="none"
							opacity={fx.opacity * 0.55}
							filter="url(#scene-fill-{pageZone})"
							class="fill-glow"
						/>
					{:else}
						<path
							d={glow.d}
							fill="none"
							stroke={fx.color}
							stroke-width={visual.glowWidth}
							stroke-linejoin="miter"
							stroke-linecap="butt"
							vector-effect="non-scaling-stroke"
							opacity={fx.opacity}
							filter="url(#scene-glow-{pageZone})"
						/>
						<path
							d={glow.d}
							fill="none"
							stroke={fx.color}
							stroke-width={visual.coreWidth}
							stroke-linejoin="miter"
							stroke-linecap="butt"
							vector-effect="non-scaling-stroke"
							opacity={fx.opacity}
						/>
					{/if}
				{/each}
			</svg>
		{/if}
	{/if}

	{#if !controls.masterOn}
		<div class="off-veil"></div>
	{/if}
</div>

<style>
	.scene {
		position: relative;
		width: 100%;
		max-width: 480px;
		margin: 0 auto;
		overflow: hidden;
		background: #000;
		mask-image:
			linear-gradient(to bottom, transparent 0%, #000 14%, #000 86%, transparent 100%),
			linear-gradient(to right, transparent 0%, #000 14%, #000 86%, transparent 100%);
		mask-composite: intersect;
		-webkit-mask-image:
			linear-gradient(to bottom, transparent 0%, #000 14%, #000 86%, transparent 100%),
			linear-gradient(to right, transparent 0%, #000 14%, #000 86%, transparent 100%);
		-webkit-mask-composite: source-in;
	}
	@media (min-width: 768px) {
		.scene {
			max-width: 720px;
		}
	}
	.base {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
		object-position: center;
		pointer-events: none;
		user-select: none;
		-webkit-user-select: none;
		-webkit-touch-callout: none;
		-webkit-user-drag: none;
		transition: opacity 200ms ease-out;
	}
	.glow-svg {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		overflow: visible;
		pointer-events: none;
	}
	.glow-svg path {
		transition: stroke 200ms ease-out, opacity 200ms ease-out, fill 200ms ease-out;
	}
	.fill-glow {
		mix-blend-mode: screen;
	}
	.blob {
		position: absolute;
		inset: 0;
		background: radial-gradient(
			circle at center,
			var(--c) 0%,
			var(--c) 2%,
			transparent 18%
		);
		filter: blur(45px);
		opacity: var(--o);
		pointer-events: none;
		transition: background 200ms ease-out, opacity 200ms ease-out;
	}
	.off-veil {
		position: absolute;
		inset: 0;
		background: rgba(0, 0, 0, 0.55);
		pointer-events: none;
		transition: background 200ms ease-out;
	}
</style>
