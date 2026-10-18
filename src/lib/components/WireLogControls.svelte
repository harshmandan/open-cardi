<script lang="ts">
	import { downloadLog, logMark, wirelog } from '$lib/state/wirelog.svelte';

	let justMarked = $state(false);
	let resetTimer: ReturnType<typeof setTimeout> | null = null;

	function mark() {
		logMark();
		justMarked = true;
		if (resetTimer) clearTimeout(resetTimer);
		resetTimer = setTimeout(() => (justMarked = false), 1500);
	}
</script>

<div class="flex flex-col gap-8">
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 tracking-wider uppercase opacity-50">BLE wire log</span>
		<div class="flex gap-8">
			<button
				type="button"
				onclick={mark}
				class="border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5"
			>
				<span class="inline-block text-center" style="min-width: 7ch">
					{justMarked ? `marked #${wirelog.marks}` : 'mark'}
				</span>
			</button>
			<button
				type="button"
				onclick={downloadLog}
				class="flex items-center gap-8 border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5"
			>
				<span class="opacity-60">{wirelog.count}</span>
				<span>save</span>
			</button>
		</div>
	</div>
	<p class="text-12 opacity-50">
		Lights look wrong? Tap mark right away, then save before reloading — the log is lost on reload.
	</p>
</div>
