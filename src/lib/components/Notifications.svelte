<script lang="ts">
	import { scale } from 'svelte/transition';
	import { flip } from 'svelte/animate';
	import { cubicOut } from 'svelte/easing';
	import { dismissNotification, notifications } from '$lib/state/notifications.svelte';
	import { ICON_X } from './icons.svelte';
</script>

<div class="pointer-events-none fixed top-64 inset-x-16 z-[70] flex flex-col gap-8">
	{#each notifications.list as n (n.id)}
		<div
			animate:flip={{ duration: 180, easing: cubicOut }}
			transition:scale={{ duration: 180, start: 0.92, easing: cubicOut }}
			class="pointer-events-auto flex items-start gap-12 border border-red bg-red/10 px-12 py-8 text-12 backdrop-blur-xs origin-top"
			role="alert"
		>
			<span class="flex-1 min-w-0">{n.message}</span>
			<button
				type="button"
				class="shrink-0 size-24 -mr-4 -my-4 opacity-70 hover:opacity-100"
				aria-label="Dismiss"
				onclick={() => dismissNotification(n.id)}
			>
				{@html ICON_X}
			</button>
		</div>
	{/each}
</div>
