<script lang="ts">
	import { onMount } from 'svelte';
	import { pushError } from '$lib/state/notifications.svelte';
	import '../app.css';

	let { children } = $props();

	// Safety net: surface anything that slips past a local try/catch so users
	// see a notification instead of a silent console-only failure.
	onMount(() => {
		const onRejection = (e: PromiseRejectionEvent) => {
			const reason = e.reason;
			pushError(reason instanceof Error ? reason.message : String(reason));
		};
		const onError = (e: ErrorEvent) => {
			pushError(e.error instanceof Error ? e.error.message : e.message);
		};
		window.addEventListener('unhandledrejection', onRejection);
		window.addEventListener('error', onError);
		return () => {
			window.removeEventListener('unhandledrejection', onRejection);
			window.removeEventListener('error', onError);
		};
	});
</script>

{@render children()}
