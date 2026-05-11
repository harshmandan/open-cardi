<script lang="ts">
	import { controls, toggleShowAllZones } from '$lib/state/controls.svelte';
</script>

<div class="flex items-center justify-between">
	<span class="text-10 font-500 uppercase tracking-wider opacity-50">Show all zones</span>
	<button
		type="button"
		role="switch"
		aria-checked={controls.showAllZones}
		aria-label="Show all zones"
		onclick={toggleShowAllZones}
		class="flex items-center gap-8 border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5"
	>
		<span class={['size-8', controls.showAllZones ? 'bg-green' : 'bg-foreground/30']}></span>
		<span class="inline-block text-center" style="min-width: 3ch">
			{controls.showAllZones ? 'on' : 'off'}
		</span>
	</button>
</div>
