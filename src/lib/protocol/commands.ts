import { PATTERN_OPCODES } from './constants';

export type Rgb = { r: number; g: number; b: number };

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

export function setColor(r: number, g: number, b: number, brightnessPct = 100): Uint8Array {
	const pct = clamp(brightnessPct, 0, 100);
	const rs = ((r * pct) / 100) & 0xff;
	const gs = ((g * pct) / 100) & 0xff;
	const bs = ((b * pct) / 100) & 0xff;
	return new Uint8Array([0xed, 0x49, rs, gs, bs, 0xe9]);
}

export function masterOn(): Uint8Array {
	return new Uint8Array([0xed, 0xf0, 0x00, 0x00, 0x00, 0xe9]);
}

export function masterOff(): Uint8Array {
	return new Uint8Array([0xed, 0x0f, 0x00, 0x00, 0x00, 0xe9]);
}

export function selectZone(value: number): Uint8Array {
	return new Uint8Array([0xfe, value & 0xff, 0xef]);
}

export function setPattern(index: number): Uint8Array {
	const op = PATTERN_OPCODES[index];
	if (op === undefined) {
		throw new Error(`pattern index ${index} out of range (0..${PATTERN_OPCODES.length - 1})`);
	}
	return new Uint8Array([0xed, op, 0x00, 0x00, 0x00, 0xe9]);
}

export function setSpeed(speed: number): Uint8Array {
	return new Uint8Array([0x30, clamp(speed, 1, 100), 0x03]);
}

export function setPatternBrightness(pct: number): Uint8Array {
	return new Uint8Array([0x31, clamp(pct, 1, 100), 0x13]);
}

export function setWarmWhite(pct: number): Uint8Array {
	return new Uint8Array([0xc3, clamp(pct, 0, 100), 0x3c]);
}

export function micOn(mode: number): Uint8Array {
	const m = (clamp(mode, 0, 3) + 1) & 0xff;
	return new Uint8Array([0xed, 0x34, m, m, m, 0xe9]);
}

export function micOff(): Uint8Array {
	return new Uint8Array([0xed, 0x33, 0x01, 0x01, 0x01, 0xe9]);
}

export function musicColor(r: number, g: number, b: number): Uint8Array {
	let rr = r & 0xff,
		gg = g & 0xff,
		bb = b & 0xff;
	if (rr === 0 && gg === 0 && bb === 0) {
		rr = 16;
		gg = 5;
		bb = 5;
	}
	return new Uint8Array([0xed, 0x51, rr, gg, bb, 0xe9]);
}

export function setAppMode(value: 1 | 2): Uint8Array {
	return new Uint8Array([0xfa, value & 0xff, 0xaf]);
}

export function fragranceOn(): Uint8Array {
	return new Uint8Array([0xf8, 0xf0, 0x8f]);
}

export function fragranceOff(): Uint8Array {
	return new Uint8Array([0xf8, 0x0f, 0x8f]);
}
