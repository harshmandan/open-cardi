<script lang="ts">
	import '../app.css';
	let { children } = $props();
</script>

<svelte:head>
	<title>open-cardi</title>
</svelte:head>

{@render children()}
