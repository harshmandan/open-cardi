# open-cardi

An open-source [Web Bluetooth](https://developer.mozilla.org/en-US/docs/Web/API/Web_Bluetooth_API) replacement for the broken official "Cardi RGBW" Android app that ships with **CarDiLits** multi-zone car ambient lighting kits (Guangzhou Cardi Auto Parts, Beken BK343x BLE chipset).

The hardware is fine — the official app isn't. This project reverse engineers the BLE protocol on `0xFFF0`/`0xFFE2`/`0xFFE1` and exposes it through a SvelteKit web app you can open in Chrome on Android, macOS, Windows, or Linux. iOS users can use Bluefy.

See [`notes/CARDILITS_PROJECT_BRIEF.md`](notes/CARDILITS_PROJECT_BRIEF.md) for the full project context, GATT map, and reverse engineering plan.

## Status

Early — SvelteKit scaffold is in place. Protocol decoding (capture + APK static analysis) is the next step before the UI is wired up.

## Develop

```sh
bun install
bun run dev
```

## Build

```sh
bun run build
bun run preview
```

## License

[MIT](LICENSE).
