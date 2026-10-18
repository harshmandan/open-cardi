<script lang="ts">
	import { controls } from '$lib/state/controls.svelte';
</script>

{#if controls.firmwareVersion}
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 tracking-wider uppercase opacity-50">Kit firmware</span>
		<span class="text-12 font-500 opacity-70">v{controls.firmwareVersion}</span>
	</div>
{/if}
