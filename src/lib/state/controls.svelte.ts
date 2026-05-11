import { browser } from '$app/environment';
import { CardiClient, type ConnectionState } from '$lib/ble';
import * as cmd from '$lib/protocol/commands';
import { ZONES, type ZoneId, type MicModeId } from '$lib/protocol/constants';

export type Rgb = { r: number; g: number; b: number };

export type ZoneState = {
	mode: 'color' | 'pattern';
	color: Rgb;
	brightness: number;
	patternIndex: number;
};

export type Favorite = {
	id: string;
	color: Rgb;
	brightness: number;
};

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
	} catch {
		return [];
	}
}

function saveFavorites(list: Favorite[]) {
	if (!browser) return;
	try {
		localStorage.setItem(FAV_KEY, JSON.stringify(list));
	} catch {
		// quota or serialization issue — ignore
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
	} catch {
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
	} catch {
		// ignore
	}
}

const session = loadSession();
const restoredZones = (session.zones ?? null) as Partial<Record<ZoneId, ZoneState>> | null;

export const controls = $state({
	connection: 'disconnected' as ConnectionState,
	deviceName: null as string | null,
	masterOn: session.masterOn ?? true,
	activeZone: 'overall' as ZoneId,
	speed: session.speed ?? 50,
	mic: {
		on: session.mic?.on ?? false,
		mode: (session.mic?.mode ?? 0) as MicModeId
	},
	zones: Object.fromEntries(
		ZONES.map((z) => [z.id, restoredZones?.[z.id] ?? initialZone()])
	) as Record<ZoneId, ZoneState>,
	favorites: loadFavorites(),
	moreOpen: session.moreOpen ?? false,
	helpOpen: false,
	showAllZones: session.showAllZones ?? false,
	lastNotify: null as Uint8Array | null,
	error: null as string | null
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

function ensureClient(): CardiClient {
	if (client) return client;
	client = new CardiClient({
		onState: (s) => {
			controls.connection = s;
			if (s === 'disconnected') controls.deviceName = null;
		},
		onNotify: (data) => {
			controls.lastNotify = data;
		},
		onError: (err) => {
			controls.error = err.message;
		}
	});
	return client;
}

const zoneValue = (id: ZoneId): number => ZONES.find((z) => z.id === id)?.value ?? 1;

export async function connect() {
	controls.error = null;
	try {
		const c = ensureClient();
		await c.connect();
		controls.deviceName = c.deviceName;
	} catch (err) {
		controls.error = err instanceof Error ? err.message : String(err);
	}
}

export async function disconnect() {
	await client?.disconnect();
}

async function send(bytes: Uint8Array) {
	if (!client?.connected) return;
	try {
		await client.send(bytes);
	} catch (err) {
		controls.error = err instanceof Error ? err.message : String(err);
	}
}

function applyZoneColorToWire(id: ZoneId) {
	const z = controls.zones[id];
	send(cmd.setColor(z.color.r, z.color.g, z.color.b, z.brightness));
}

export async function selectZone(id: ZoneId) {
	controls.activeZone = id;
	if (!client?.connected) return;
	await send(cmd.selectZone(zoneValue(id)));
}

export async function setMaster(on: boolean) {
	controls.masterOn = on;
	await send(on ? cmd.masterOn() : cmd.masterOff());
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
	applyZoneColorToWire(controls.activeZone);
	persistSession();
}

export async function setBrightness(pct: number) {
	const z = controls.zones[controls.activeZone];
	z.brightness = pct;
	if (controls.activeZone === 'overall') {
		for (const zone of ZONES) {
			controls.zones[zone.id].brightness = pct;
		}
	}
	if (z.mode === 'color') applyZoneColorToWire(controls.activeZone);
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
	await send(cmd.setPattern(index));
	await send(cmd.setSpeed(controls.speed));
	persistSession();
}

export async function togglePatternMode() {
	const z = controls.zones[controls.activeZone];
	if (z.mode === 'pattern') {
		z.mode = 'color';
		await send(cmd.setColor(z.color.r, z.color.g, z.color.b, z.brightness));
	} else {
		z.mode = 'pattern';
		if (controls.mic.on) controls.mic.on = false;
		await send(cmd.setPattern(z.patternIndex));
		await send(cmd.setSpeed(controls.speed));
	}
	persistSession();
}

export async function setSpeed(speed: number) {
	controls.speed = speed;
	await send(cmd.setSpeed(speed));
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
	await send(on ? cmd.micOn(mode) : cmd.micOff());
	persistSession();
}
