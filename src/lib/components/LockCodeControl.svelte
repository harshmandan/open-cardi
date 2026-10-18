<script lang="ts">
	import { controls, sendDecode, sendLockCode } from '$lib/state/controls.svelte';

	type Action = 'lock' | 'decode';

	// First tap arms, second tap within a few seconds sends. Both change a
	// setting the kit keeps, so a stray tap shouldn't fire one.
	let armed = $state<Action | null>(null);
	let disarmTimer: ReturnType<typeof setTimeout> | null = null;

	const connected = $derived(controls.connection === 'connected');
	const busy = $derived(controls.knock !== null);

	function tap(action: Action) {
		if (!connected || busy) return;
		if (disarmTimer) clearTimeout(disarmTimer);
		if (armed !== action) {
			armed = action;
			disarmTimer = setTimeout(() => (armed = null), 3000);
			return;
		}
		armed = null;
		void (action === 'lock' ? sendLockCode() : sendDecode());
	}

	function label(action: Action) {
		if (controls.knock === action) return 'sending…';
		if (armed === action) return 'tap to confirm';
		return action === 'lock' ? 'lock code' : 'decode';
	}
</script>

<div class="flex flex-col gap-8">
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 tracking-wider uppercase opacity-50">Lock code</span>
		<div class="flex gap-8">
			{#each ['lock', 'decode'] as const as action (action)}
				<button
					type="button"
					onclick={() => tap(action)}
					disabled={!connected || busy}
					class={[
						'border px-10 py-6 text-12 font-500 hover:bg-foreground/5 disabled:opacity-40 disabled:hover:bg-transparent',
						armed === action ? 'border-foreground/60' : 'border-foreground/15'
					]}
				>
					<span class="inline-block text-center" style="min-width: 11ch">{label(action)}</span>
				</button>
			{/each}
		</div>
	</div>
	<p class="text-12 opacity-50">
		Lock stops your lights following other Cardi cars nearby. Decode undoes it. The lights end on
		red or green; pick your colour again after.
	</p>
</div>
