<script lang="ts">
	import { slide } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { controls, setMicMode } from '$lib/state/controls.svelte';
	import { MIC_MODES, type MicModeId } from '$lib/protocol/constants';
</script>

<div class="flex flex-col gap-12">
	<div class="flex items-center justify-between">
		<span class="text-10 font-500 tracking-wider uppercase opacity-50">Voice / mic</span>
		<button
			type="button"
			role="switch"
			aria-checked={controls.mic.on}
			aria-label="Device microphone"
			onclick={() => setMicMode(!controls.mic.on)}
			class="flex items-center gap-8 border border-foreground/15 px-10 py-6 text-12 font-500 hover:bg-foreground/5"
		>
			<span class={['size-8', controls.mic.on ? 'bg-green' : 'bg-foreground/30']}></span>
			<span class="inline-block text-center" style="min-width: 3ch">
				{controls.mic.on ? 'on' : 'off'}
			</span>
		</button>
	</div>

	{#if controls.mic.on}
		<div transition:slide={{ duration: 220, easing: cubicOut }}>
			<div class="grid grid-cols-2 gap-4">
				{#each MIC_MODES as m (m.id)}
					{@const active = controls.mic.mode === m.id}
					<button
						type="button"
						onclick={() => setMicMode(true, m.id as MicModeId)}
						class={[
							'border px-10 py-6 text-left text-12 font-500 transition-colors',
							active
								? 'border-foreground bg-foreground text-background'
								: 'border-foreground/15 hover:bg-foreground/5'
						]}
					>
						<div>{m.label}</div>
						<div class={['text-10', active ? 'opacity-70' : 'opacity-50']}>{m.subtitle}</div>
					</button>
				{/each}
			</div>
		</div>
	{/if}
</div>
