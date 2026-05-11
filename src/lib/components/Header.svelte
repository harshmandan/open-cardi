<script lang="ts">
	import { onMount } from 'svelte';
	import { CardiClient } from '$lib/ble';
	import { ICON_HELP, ICON_INSTALL } from './icons.svelte';
	import { connect, controls, disconnect, openHelp } from '$lib/state/controls.svelte';
	import { pushError } from '$lib/state/notifications.svelte';

	const supported = $derived(typeof navigator !== 'undefined' && CardiClient.isSupported());

	const dotClass = $derived.by(() => {
		switch (controls.connection) {
			case 'connected':
				return 'bg-green';
			case 'requesting':
			case 'connecting':
				return 'bg-foreground/60 pulse';
			default:
				return 'bg-red';
		}
	});

	const chipLabel = $derived.by(() => {
		switch (controls.connection) {
			case 'connected':
				return controls.deviceName ?? 'connected';
			case 'requesting':
				return 'choose…';
			case 'connecting':
				return 'connecting…';
			default:
				return 'connect';
		}
	});

	function onChipClick() {
		if (!supported) return;
		if (controls.connection === 'connected') void disconnect();
		else if (controls.connection === 'disconnected') void connect();
	}

	// PWA install prompt — captured from beforeinstallprompt. The event only
	// fires on browsers that detect an installable PWA (Chromium on Android,
	// desktop Chrome/Edge). When fired, the button becomes visible.
	type BeforeInstallPromptEvent = Event & {
		prompt: () => Promise<void>;
		userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
	};

	let deferredPrompt = $state<BeforeInstallPromptEvent | null>(null);

	onMount(() => {
		const onPromptable = (e: Event) => {
			e.preventDefault();
			deferredPrompt = e as BeforeInstallPromptEvent;
		};
		const onInstalled = () => {
			deferredPrompt = null;
		};
		window.addEventListener('beforeinstallprompt', onPromptable);
		window.addEventListener('appinstalled', onInstalled);
		return () => {
			window.removeEventListener('beforeinstallprompt', onPromptable);
			window.removeEventListener('appinstalled', onInstalled);
		};
	});

	async function onInstallClick() {
		if (!deferredPrompt) return;
		try {
			await deferredPrompt.prompt();
			const { outcome } = await deferredPrompt.userChoice;
			if (outcome === 'accepted') deferredPrompt = null;
		} catch (err) {
			pushError(err instanceof Error ? err.message : String(err));
		}
	}
</script>

<header class="relative z-10 flex items-center justify-between gap-12 bg-transparent px-16 py-12">
	<div class="flex items-center gap-8">
		<h1 class="text-14 font-700 lowercase">open-cardi</h1>
	</div>

	<div class="flex items-center gap-8">
		<button
			type="button"
			onclick={openHelp}
			aria-label="Help"
			class="flex size-28 items-center justify-center border border-foreground/15 hover:bg-foreground/5"
		>
			<span class="size-16" aria-hidden="true">{@html ICON_HELP}</span>
		</button>

		{#if deferredPrompt}
			<button
				type="button"
				onclick={onInstallClick}
				aria-label="Install"
				title="Install app"
				class="flex size-28 items-center justify-center border border-foreground/15 hover:bg-foreground/5"
			>
				<span class="size-16" aria-hidden="true">{@html ICON_INSTALL}</span>
			</button>
		{/if}

		<button
			type="button"
			onclick={onChipClick}
			disabled={!supported}
			title={supported ? '' : 'Web Bluetooth not available'}
			class="flex items-center gap-8 border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5 disabled:cursor-not-allowed disabled:opacity-50"
		>
			<span class={['size-8', dotClass]}></span>
			<span class="truncate text-right" style="min-width: 12ch; max-width: 160px;">
				{chipLabel}
			</span>
		</button>
	</div>
</header>

<style>
	@keyframes p {
		0%,
		100% {
			opacity: 1;
		}
		50% {
			opacity: 0.4;
		}
	}
	:global(.pulse) {
		animation: p 1.2s ease-in-out infinite;
	}
</style>
