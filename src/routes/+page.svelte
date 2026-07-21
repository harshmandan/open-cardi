<script lang="ts">
	import { onMount } from 'svelte';
	import ControlRack from '$lib/components/ControlRack.svelte';
	import Header from '$lib/components/Header.svelte';
	import HelpDialog from '$lib/components/HelpDialog.svelte';
	import MoreSection from '$lib/components/MoreSection.svelte';
	import Notifications from '$lib/components/Notifications.svelte';
	import ZonePages from '$lib/components/ZonePages.svelte';
	import { controls } from '$lib/state/controls.svelte';

	let isDesktop = $state(false);

	onMount(() => {
		const mq = window.matchMedia('(min-width: 768px)');
		const update = () => {
			isDesktop = mq.matches;
		};
		update();
		mq.addEventListener('change', update);
		return () => mq.removeEventListener('change', update);
	});

	const hasSidebar = $derived(isDesktop && controls.moreOpen);
</script>

<div class="app" class:has-sidebar={hasSidebar}>
	<div class="main">
		<Header />

		<main class="flex min-h-0 flex-1 flex-col">
			<ZonePages />
			<div class="controls-pad anim-enter anim-enter-2 shrink-0">
				<ControlRack />
			</div>
		</main>
	</div>

	{#if isDesktop}
		<div class="sidebar-slot">
			<MoreSection mode="sidebar" />
		</div>
	{/if}
</div>

{#if !isDesktop}
	<MoreSection mode="sheet" />
{/if}

<HelpDialog />

<Notifications />

<style>
	.app {
		display: grid;
		grid-template-columns: 1fr 0fr;
		min-height: 100dvh;
		margin: 0 auto;
		background: var(--background);
		max-width: 768px;
		width: 100%;
		transition:
			grid-template-columns 260ms cubic-bezier(0.4, 0, 0.2, 1),
			max-width 260ms cubic-bezier(0.4, 0, 0.2, 1);
	}
	.app.has-sidebar {
		grid-template-columns: 1fr 1fr;
		max-width: 1536px;
	}
	.main,
	.sidebar-slot {
		min-width: 0;
		overflow: hidden;
	}
	.main {
		display: flex;
		flex-direction: column;
	}
	/* 16px padding on all sides; on mobile bump the bottom to 32px (plus any
	   iOS safe-area inset) so the controls clear the home bar. */
	.controls-pad {
		padding: 16px;
	}
	@media (max-width: 767px) {
		.controls-pad {
			padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 2rem);
		}
	}
</style>
