<script lang="ts">
	import { browser } from '$app/environment';
	import { onMount } from 'svelte';
	import { fade, scale } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { closeHelp, controls, openHelp } from '$lib/state/controls.svelte';

	const SEEN_KEY = 'open-cardi.help-seen.v1';

	onMount(() => {
		if (!browser) return;
		try {
			if (!localStorage.getItem(SEEN_KEY)) openHelp();
		} catch {
			// localStorage unavailable; just skip auto-open
		}
	});

	function dismiss() {
		try {
			if (browser) localStorage.setItem(SEEN_KEY, '1');
		} catch {
			// ignore
		}
		closeHelp();
	}
</script>

{#if controls.helpOpen}
	<div
		class="fixed inset-0 z-[60] flex items-center justify-center p-16"
		role="dialog"
		aria-modal="true"
		aria-labelledby="help-title"
	>
		<button
			type="button"
			aria-label="Close"
			onclick={dismiss}
			transition:fade={{ duration: 150 }}
			class="absolute inset-0 cursor-default bg-black/70 backdrop-blur-xs"
		></button>

		<div
			class="relative w-full max-w-512 max-h-full overflow-y-auto bg-background border border-foreground/15 p-20 flex flex-col gap-16"
			transition:scale={{ start: 0.95, duration: 200, easing: cubicOut, opacity: 0 }}
		>
			<header class="flex flex-col gap-4">
				<span class="text-10 font-500 uppercase tracking-wider opacity-50">welcome</span>
				<h2 id="help-title" class="text-18 font-700 lowercase">open-cardi</h2>
			</header>

			<p class="text-12 opacity-80 leading-relaxed">
				Control your car's ambient lighting from the browser.
			</p>

			<section class="flex flex-col gap-10">
				<h3 class="text-10 font-500 uppercase tracking-wider opacity-50">how to use</h3>
				<ul class="text-14 opacity-90 leading-snug flex flex-col gap-8">
					<li>1. Tap <span class="font-700">connect</span> to pair.</li>
					<li>2. Swipe left/right to switch zones.</li>
					<li>3. Drag sliders to set color.</li>
					<li>4. Tap <span class="font-700">+</span> to save favorites.</li>
					<li>5. Tap <span class="font-700">more</span> for patterns and mic modes.</li>
				</ul>
			</section>

			<section class="flex flex-col gap-6">
				<h3 class="text-10 font-500 uppercase tracking-wider opacity-50">supported devices</h3>
				<ul class="text-12 opacity-80 leading-relaxed flex flex-col gap-2">
					<li>· Cardi K3 Active Ultra</li>
					<li>· Cardi K4 Active Ultra</li>
					<li>· Any ConSmart-family kit (BLE service 0xFFF0)</li>
				</ul>
			</section>

			<section class="flex flex-col gap-6">
				<h3 class="text-10 font-500 uppercase tracking-wider opacity-50">install as app</h3>
				<ul class="text-12 opacity-80 leading-relaxed flex flex-col gap-4">
					<li><span class="font-700">Android:</span> tap <span class="font-700">install</span> in the header.</li>
					<li>
						<span class="font-700">iOS:</span> open in
						<a
							href="https://apps.apple.com/us/app/bluefy-web-ble-browser/id1492822055"
							target="_blank"
							rel="noopener noreferrer"
							class="font-700 underline">Bluefy</a
						> (Safari has no Web Bluetooth).
					</li>
				</ul>
			</section>

			<section class="flex flex-col gap-6">
				<h3 class="text-10 font-500 uppercase tracking-wider opacity-50">open source</h3>
				<p class="text-12 opacity-80 leading-relaxed">
					Built with love, reverse-engineered from scratch. Star, fork, or send a PR on
					<a
						href="https://github.com/harshmandan/open-cardi"
						target="_blank"
						rel="noopener noreferrer"
						class="font-700 underline">GitHub</a
					>.
				</p>
			</section>

			<div class="flex justify-end pt-4">
				<button
					type="button"
					onclick={dismiss}
					class="border border-foreground bg-foreground text-background px-16 py-8 text-12 font-500 lowercase hover:opacity-90"
				>
					got it
				</button>
			</div>
		</div>
	</div>
{/if}
