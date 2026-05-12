import { browser } from '$app/environment';

export type WireLogKind = 'send' | 'recv' | 'state' | 'error' | 'info';

export type WireLogEntry = {
	t: number;
	kind: WireLogKind;
	label: string;
	payload?: string;
};

const MAX_ENTRIES = 2000;
const sessionStart = browser ? performance.now() : 0;
const buffer: WireLogEntry[] = [];

export const wirelog = $state({ count: 0 });

function record(kind: WireLogKind, label: string, payload?: string) {
	const t = browser ? performance.now() - sessionStart : 0;
	buffer.push({ t, kind, label, payload });
	if (buffer.length > MAX_ENTRIES) buffer.splice(0, buffer.length - MAX_ENTRIES);
	wirelog.count = buffer.length;
}

const hex = (bytes: Uint8Array) =>
	Array.from(bytes)
		.map((b) => b.toString(16).padStart(2, '0'))
		.join(' ');

function inferLabel(bytes: Uint8Array): string {
	if (bytes.length === 0) return 'empty';
	const a = bytes[0];
	const b = bytes[1];
	if (a === 0xed && bytes.length === 6) {
		if (b === 0x49) return 'color';
		if (b === 0xf0) return 'master_on';
		if (b === 0x0f) return 'master_off';
		if (b === 0x34) return 'mic_on';
		if (b === 0x33) return 'mic_off';
		if (b === 0x51) return 'music_color';
		if (b !== undefined && b >= 0x35 && b <= 0x4c && b !== 0x49) return 'pattern';
		return 'ed_unknown';
	}
	if (a === 0xfe && bytes.length === 3) return 'zone';
	if (a === 0xfa && bytes.length === 3) return 'app_mode';
	if (a === 0xc3 && bytes.length === 3) return 'warm_white';
	if (a === 0x30 && bytes.length === 3) return 'speed';
	if (a === 0x31 && bytes.length === 3) return 'pattern_br';
	if (a === 0xf8 && bytes.length === 3) return 'fragrance';
	if (a === 0x3c && b === 0xaa) return 'hs_getlight';
	if (a === 0x0e && b === 0xaa) return 'hs_readzone';
	if (a === 0x18) return 'hs_18';
	if (a === 0x17) return 'hs_17';
	if (a === 0x16) return 'hs_16';
	return 'raw';
}

export function logSend(bytes: Uint8Array) {
	record('send', inferLabel(bytes), hex(bytes));
}

export function logRecv(bytes: Uint8Array) {
	record('recv', 'notify', hex(bytes));
}

export function logState(state: string) {
	record('state', state);
}

export function logError(message: string) {
	record('error', message);
}

export function logInfo(message: string) {
	record('info', message);
}

function fmt(e: WireLogEntry): string {
	const ts = `[+${e.t.toFixed(0).padStart(7, '0')}ms]`;
	const kind = e.kind.toUpperCase().padEnd(6);
	const label = e.label.padEnd(12);
	return e.payload ? `${ts} ${kind} ${label} ${e.payload}` : `${ts} ${kind} ${label}`;
}

export function exportLog(): string {
	const header = [
		`open-cardi BLE wire log`,
		`exported: ${new Date().toISOString()}`,
		`entries: ${buffer.length}`,
		`useragent: ${browser ? navigator.userAgent : 'n/a'}`,
		``
	].join('\n');
	return header + buffer.map(fmt).join('\n') + '\n';
}

export function downloadLog() {
	if (!browser) return;
	const text = exportLog();
	const blob = new Blob([text], { type: 'text/plain' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = `open-cardi-log-${new Date().toISOString().replace(/[:.]/g, '-')}.txt`;
	document.body.appendChild(a);
	a.click();
	a.remove();
	setTimeout(() => URL.revokeObjectURL(url), 100);
}

export function clearLog() {
	buffer.length = 0;
	wirelog.count = 0;
}
