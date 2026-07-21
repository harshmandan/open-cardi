<script lang="ts">
	import { browser } from '$app/environment';
	import { onMount } from 'svelte';
	import { fade, scale } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { closeHelp, controls, openHelp } from '$lib/state/controls.svelte';
	import { pushError } from '$lib/state/notifications.svelte';

	const SEEN_KEY = 'open-cardi.help-seen.v1';

	onMount(() => {
		if (!browser) return;
		try {
			if (!localStorage.getItem(SEEN_KEY)) openHelp();
		} catch (err) {
			pushError(
				`Couldn't read help-seen flag: ${err instanceof Error ? err.message : String(err)}`
			);
		}
	});

	function dismiss() {
		try {
			if (browser) localStorage.setItem(SEEN_KEY, '1');
		} catch (err) {
			pushError(
				`Couldn't save help-seen flag: ${err instanceof Error ? err.message : String(err)}`
			);
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
			class="relative flex max-h-full w-full max-w-512 flex-col gap-16 overflow-y-auto border border-foreground/15 bg-background p-20"
			transition:scale={{ start: 0.95, duration: 200, easing: cubicOut, opacity: 0 }}
		>
			<header class="flex flex-col gap-4">
				<span class="text-10 font-500 tracking-wider uppercase opacity-50">welcome</span>
				<h2 id="help-title" class="text-18 font-700 lowercase">open-cardi</h2>
			</header>

			<p class="text-12 leading-relaxed opacity-80">
				Control your car's ambient lighting from the browser.
			</p>

			<section class="flex flex-col gap-10">
				<h3 class="text-10 font-500 tracking-wider uppercase opacity-50">how to use</h3>
				<ul class="flex flex-col gap-8 text-14 leading-snug opacity-90">
					<li>1. Tap <span class="font-700">connect</span> to pair.</li>
					<li>2. Swipe left/right to switch zones.</li>
					<li>3. Drag sliders to set color.</li>
					<li>4. Tap <span class="font-700">+</span> to save favorites.</li>
					<li>5. Tap <span class="font-700">more</span> for patterns and mic modes.</li>
				</ul>
			</section>

			<section class="flex flex-col gap-6">
				<h3 class="text-10 font-500 tracking-wider uppercase opacity-50">supported devices</h3>
				<ul class="flex flex-col gap-2 text-12 leading-relaxed opacity-80">
					<li>· Cardi K3 Active Ultra</li>
					<li>· Cardi K4 Active Ultra</li>
					<li>· Any ConSmart-family kit (BLE service 0xFFF0)</li>
				</ul>
			</section>

			<section class="flex flex-col gap-6">
				<h3 class="text-10 font-500 tracking-wider uppercase opacity-50">install as app</h3>
				<ul class="flex flex-col gap-4 text-12 leading-relaxed opacity-80">
					<li>
						<span class="font-700">Android:</span> tap <span class="font-700">install</span> in the header.
					</li>
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
				<h3 class="text-10 font-500 tracking-wider uppercase opacity-50">open source</h3>
				<p class="text-12 leading-relaxed opacity-80">
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
					class="border border-foreground bg-foreground px-16 py-8 text-12 font-500 text-background lowercase hover:opacity-90"
				>
					got it
				</button>
			</div>
		</div>
	</div>
{/if}
