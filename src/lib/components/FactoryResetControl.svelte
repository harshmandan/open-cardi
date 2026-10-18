<script lang="ts">
	import { controls, sendFactoryReset } from '$lib/state/controls.svelte';

	// First tap arms, second tap within a few seconds sends.
	let armed = $state(false);
	let disarmTimer: ReturnType<typeof setTimeout> | null = null;

	const connected = $derived(controls.connection === 'connected');

	function tap() {
		if (!connected || controls.knock) return;
		if (disarmTimer) clearTimeout(disarmTimer);
		if (!armed) {
			armed = true;
			disarmTimer = setTimeout(() => (armed = false), 3000);
			return;
		}
		armed = false;
		sendFactoryReset();
	}
</script>

<div class="flex flex-col gap-8">
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 tracking-wider uppercase opacity-50">Factory reset</span>
		<button
			type="button"
			onclick={tap}
			disabled={!connected || controls.knock !== null}
			class={[
				'border px-10 py-6 text-12 font-500 hover:bg-foreground/5 disabled:opacity-40 disabled:hover:bg-transparent',
				armed ? 'border-foreground/60' : 'border-foreground/15'
			]}
		>
			<span class="inline-block text-center" style="min-width: 14ch">
				{armed ? 'tap to confirm' : 'restore defaults'}
			</span>
		</button>
	</div>
	<p class="text-12 opacity-50">
		Restores the kit's factory settings, as in the vendor app. The kit restarts and the link drops.
	</p>
</div>
