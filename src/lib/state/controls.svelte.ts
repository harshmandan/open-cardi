import { browser } from '$app/environment';
import { CardiClient, type ConnectionState } from '$lib/ble';
import * as cmd from '$lib/protocol/commands';
import { ZONES, type ZoneId, type MicModeId } from '$lib/protocol/constants';
import { pushError } from '$lib/state/notifications.svelte';
import { logError, logInfo, logRecv, logSend, logState } from '$lib/state/wirelog.svelte';

export type Rgb = { r: number; g: number; b: number };

export type ZoneState = {
	mode: 'color' | 'pattern' | 'warmwhite';
	color: Rgb;
	brightness: number;
	patternIndex: number;
};

export type Favorite = {
	id: string;
	color: Rgb;
	brightness: number;
};

// The car's lower brightness range is perceptually still bright while the UI
// preview goes near-black; cap the floor so what the user sees roughly tracks
// what the car looks like.
const MIN_BRIGHTNESS = 50;

const clampBrightness = (pct: number) => Math.max(MIN_BRIGHTNESS, Math.min(100, pct));

const normalizeZone = (z: ZoneState): ZoneState => ({
	...z,
	brightness: clampBrightness(z.brightness)
});

const initialZone = (): ZoneState => ({
	mode: 'color',
	color: { r: 0, g: 0, b: 0 },
	brightness: 100,
	patternIndex: 0
});

const FAV_KEY = 'open-cardi.favorites.v1';
const SESSION_KEY = 'open-cardi.session.v1';

function loadFavorites(): Favorite[] {
	if (!browser) return [];
	try {
		const raw = localStorage.getItem(FAV_KEY);
		if (!raw) return [];
		const parsed = JSON.parse(raw);
		return Array.isArray(parsed) ? parsed : [];
	} catch (err) {
		pushError(`Couldn't load favorites: ${err instanceof Error ? err.message : String(err)}`);
		return [];
	}
}

function saveFavorites(list: Favorite[]) {
	if (!browser) return;
	try {
		localStorage.setItem(FAV_KEY, JSON.stringify(list));
	} catch (err) {
		pushError(`Couldn't save favorites: ${err instanceof Error ? err.message : String(err)}`);
	}
}

type SessionSnapshot = {
	zones: Record<ZoneId, ZoneState>;
	speed: number;
	mic: { on: boolean; mode: MicModeId };
	masterOn: boolean;
	showAllZones: boolean;
	moreOpen: boolean;
};

function loadSession(): Partial<SessionSnapshot> {
	if (!browser) return {};
	try {
		const raw = localStorage.getItem(SESSION_KEY);
		if (!raw) return {};
		const parsed = JSON.parse(raw);
		return typeof parsed === 'object' && parsed !== null ? parsed : {};
	} catch (err) {
		pushError(`Couldn't restore session: ${err instanceof Error ? err.message : String(err)}`);
		return {};
	}
}

function persistSession() {
	if (!browser) return;
	try {
		const snapshot: SessionSnapshot = {
			zones: $state.snapshot(controls.zones) as Record<ZoneId, ZoneState>,
			speed: controls.speed,
			mic: { on: controls.mic.on, mode: controls.mic.mode },
			masterOn: controls.masterOn,
			showAllZones: controls.showAllZones,
			moreOpen: controls.moreOpen
		};
		localStorage.setItem(SESSION_KEY, JSON.stringify(snapshot));
	} catch (err) {
		pushError(`Couldn't save session: ${err instanceof Error ? err.message : String(err)}`);
	}
}

const session = loadSession();
const restoredZones = (session.zones ?? null) as Partial<Record<ZoneId, ZoneState>> | null;

export const controls = $state({
	connection: 'disconnected' as ConnectionState,
	deviceName: null as string | null,
	masterOn: session.masterOn ?? true,
	activeZone: 'guide' as ZoneId,
	speed: session.speed ?? 50,
	mic: {
		on: session.mic?.on ?? false,
		mode: (session.mic?.mode ?? 0) as MicModeId
	},
	zones: Object.fromEntries(
		ZONES.map((z) => {
			const restored = restoredZones?.[z.id];
			return [z.id, restored ? normalizeZone(restored) : initialZone()];
		})
	) as Record<ZoneId, ZoneState>,
	favorites: loadFavorites(),
	moreOpen: session.moreOpen ?? false,
	helpOpen: false,
	showAllZones: session.showAllZones ?? false,
	lastNotify: null as Uint8Array | null
});

export function toggleMore() {
	controls.moreOpen = !controls.moreOpen;
	persistSession();
}

export function openHelp() {
	controls.helpOpen = true;
}

export function closeHelp() {
	controls.helpOpen = false;
}

export function toggleShowAllZones() {
	controls.showAllZones = !controls.showAllZones;
	persistSession();
}

let client: CardiClient | null = null;

// Latest-wins coalescer for BLE writes. Slider drags fire many events per
// second; without coalescing they'd pile up in the GATT queue, lag the device,
// and on Android the overflow surfaces as "GATT operation in progress" and
// drops the link. We keep one pending payload per command kind and only drain
// the freshest value once the prior write completes.
const pendingWrites = new Map<string, Uint8Array>();
let draining = false;

// The official Cardi Tech Android app caps RGB output to ~10 Hz; the MCU drops
// frames / flashes black when pushed faster than that. Throttle 'color' writes
// to a 100 ms floor; other kinds (zone, master, mic, pattern, speed) go through
// as fast as the GATT queue allows.
const COLOR_MIN_INTERVAL_MS = 100;
let lastColorSendAt = 0;

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

function sendCoalesced(kind: string, bytes: Uint8Array) {
	if (!client?.connected) return;
	pendingWrites.set(kind, bytes);
	if (draining) return;
	void drainWrites();
}

async function drainWrites() {
	if (draining) return;
	draining = true;
	try {
		while (pendingWrites.size > 0 && client?.connected) {
			const it = pendingWrites.entries().next();
			if (it.done) break;
			const [kind, initialPayload] = it.value;
			let payload = initialPayload;

			if (kind === 'color') {
				const elapsed = performance.now() - lastColorSendAt;
				if (elapsed < COLOR_MIN_INTERVAL_MS) {
					await sleep(COLOR_MIN_INTERVAL_MS - elapsed);
					// Pick up the freshest color the slider produced while we waited.
					payload = pendingWrites.get('color') ?? payload;
				}
				lastColorSendAt = performance.now();
			}

			pendingWrites.delete(kind);
			try {
				await client.send(payload);
			} catch (err) {
				pushError(err instanceof Error ? err.message : String(err));
			}
		}
	} finally {
		draining = false;
	}
}

function ensureClient(): CardiClient {
	if (client) return client;
	client = new CardiClient({
		onState: (s) => {
			controls.connection = s;
			logState(s);
			if (s === 'disconnected') {
				controls.deviceName = null;
				pendingWrites.clear();
			}
		},
		onNotify: (data) => {
			controls.lastNotify = data;
			logRecv(data);
		},
		onError: (err) => {
			logError(err.message);
			pushError(err.message);
		},
		onSend: (data) => {
			logSend(data);
		}
	});
	return client;
}

const zoneValue = (id: ZoneId): number => ZONES.find((z) => z.id === id)?.value ?? 1;

export async function connect() {
	try {
		const c = ensureClient();
		await c.connect();
		controls.deviceName = c.deviceName;
		// Tell the kit which zone the UI is targeting. Without this the MCU
		// keeps applying our color writes to whatever zone it had selected
		// before connect (e.g. doors), so changes appear to land on the
		// wrong physical strip.
		logInfo(`zone sync after connect → ${controls.activeZone}`);
		sendCoalesced('zone', cmd.selectZone(zoneValue(controls.activeZone)));
	} catch (err) {
		// requestDevice() rejects with NotFoundError when the user closes the
		// chooser without picking anything — translate that to plain English.
		if (err instanceof DOMException && err.name === 'NotFoundError') {
			pushError('No device selected.');
		} else {
			pushError(err instanceof Error ? err.message : String(err));
		}
	}
}

export async function disconnect() {
	await client?.disconnect();
}

function applyZoneToWire(id: ZoneId) {
	const z = controls.zones[id];
	if (z.mode === 'warmwhite') {
		// Warm-white is a separate hardware channel; the kit ignores RGB while
		// this command drives the warm LEDs at the given intensity.
		sendCoalesced('color', cmd.setWarmWhite(z.brightness));
	} else if (z.mode === 'color') {
		sendCoalesced('color', cmd.setColor(z.color.r, z.color.g, z.color.b, z.brightness));
	}
}

export async function selectZone(id: ZoneId) {
	controls.activeZone = id;
	if (!client?.connected) return;
	sendCoalesced('zone', cmd.selectZone(zoneValue(id)));
}

export async function setMaster(on: boolean) {
	controls.masterOn = on;
	sendCoalesced('master', on ? cmd.masterOn() : cmd.masterOff());
	persistSession();
}

export async function setColor(rgb: Rgb) {
	const z = controls.zones[controls.activeZone];
	z.mode = 'color';
	z.color = rgb;
	if (controls.activeZone === 'overall') {
		// Overall broadcasts to every zone — supportedOn is a hardware-compat
		// label, not a broadcast filter. The UI should reflect the broadcast
		// even on K3 (where some zones are BLE-silent).
		for (const zone of ZONES) {
			controls.zones[zone.id].color = rgb;
			controls.zones[zone.id].brightness = z.brightness;
			controls.zones[zone.id].mode = 'color';
		}
	}
	applyZoneToWire(controls.activeZone);
	persistSession();
}

export async function setWarmWhite() {
	const z = controls.zones[controls.activeZone];
	z.mode = 'warmwhite';
	// Mirror the warm-white preview color into RGB state for the swatch.
	z.color = { r: 255, g: 200, b: 130 };
	if (controls.activeZone === 'overall') {
		for (const zone of ZONES) {
			controls.zones[zone.id].mode = 'warmwhite';
			controls.zones[zone.id].color = { r: 255, g: 200, b: 130 };
			controls.zones[zone.id].brightness = z.brightness;
		}
	}
	// Warm-white supersedes mic on the kit; sync UI.
	if (controls.mic.on) controls.mic.on = false;
	applyZoneToWire(controls.activeZone);
	persistSession();
}

export async function setBrightness(pct: number) {
	const clamped = clampBrightness(pct);
	const z = controls.zones[controls.activeZone];
	z.brightness = clamped;
	if (controls.activeZone === 'overall') {
		for (const zone of ZONES) {
			controls.zones[zone.id].brightness = clamped;
		}
	}
	if (z.mode === 'color' || z.mode === 'warmwhite') applyZoneToWire(controls.activeZone);
	persistSession();
}

export async function applyFavorite(fav: Favorite) {
	await setColor(fav.color);
	await setBrightness(fav.brightness);
}

export function saveCurrentAsFavorite() {
	const z = controls.zones[controls.activeZone];
	const exists = controls.favorites.some(
		(f) =>
			f.color.r === z.color.r &&
			f.color.g === z.color.g &&
			f.color.b === z.color.b &&
			f.brightness === z.brightness
	);
	if (exists) return;
	const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
	const fav: Favorite = { id, color: { ...z.color }, brightness: z.brightness };
	controls.favorites = [fav, ...controls.favorites];
	saveFavorites(controls.favorites);
}

export function removeFavorite(id: string) {
	controls.favorites = controls.favorites.filter((f) => f.id !== id);
	saveFavorites(controls.favorites);
}

export async function setPattern(index: number) {
	const z = controls.zones[controls.activeZone];
	z.mode = 'pattern';
	z.patternIndex = index;
	// Pattern and mic are mutually exclusive on the MCU — the pattern command
	// implicitly cancels mic mode, just sync the UI.
	if (controls.mic.on) controls.mic.on = false;
	sendCoalesced('pattern', cmd.setPattern(index));
	sendCoalesced('speed', cmd.setSpeed(controls.speed));
	persistSession();
}

export async function togglePatternMode() {
	const z = controls.zones[controls.activeZone];
	if (z.mode === 'pattern') {
		z.mode = 'color';
		sendCoalesced('color', cmd.setColor(z.color.r, z.color.g, z.color.b, z.brightness));
	} else {
		z.mode = 'pattern';
		if (controls.mic.on) controls.mic.on = false;
		sendCoalesced('pattern', cmd.setPattern(z.patternIndex));
		sendCoalesced('speed', cmd.setSpeed(controls.speed));
	}
	persistSession();
}

export async function setSpeed(speed: number) {
	controls.speed = speed;
	sendCoalesced('speed', cmd.setSpeed(speed));
	persistSession();
}

export async function setMicMode(on: boolean, mode: MicModeId = controls.mic.mode) {
	controls.mic.on = on;
	controls.mic.mode = mode;
	if (on) {
		// Mic supersedes pattern on the MCU. Clear pattern mode on every zone so
		// the UI doesn't claim a pattern is active while the kit is mic-reactive.
		for (const zone of ZONES) {
			controls.zones[zone.id].mode = 'color';
		}
	}
	sendCoalesced('mic', on ? cmd.micOn(mode) : cmd.micOff());
	persistSession();
}
