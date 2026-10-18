import { describe, expect, test } from 'bun:test';
import { decodeSequence, lockCodeSequence, type TimedFrame } from './sequences';

const hex = (u: Uint8Array) =>
	[...u].map((b) => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');

// Transcribed from Cardi-RGBW 1.2.2 BYLightActivity.suomaThread / jiemaThread.
const TAIL = [
	...Array.from({ length: 5 }, () => ['ED 0F 00 00 00 E9', 'ED F0 00 00 00 E9']).flat(),
	'ED F0 00 00 00 E9'
];

const wire = (seq: TimedFrame[]) => seq.map((f) => hex(f.bytes));
const delays = (seq: TimedFrame[]) => seq.map((f) => f.delayAfterMs);

describe('knock sequences', () => {
	test('lock code is red then five off/on pairs then on', () => {
		expect(wire(lockCodeSequence())).toEqual(['ED 49 FF 00 00 E9', ...TAIL]);
	});

	test('decode is green then five off/on pairs then on', () => {
		expect(wire(decodeSequence())).toEqual(['ED 49 00 FF 00 E9', ...TAIL]);
	});

	test('200 ms between every frame, nothing after the last', () => {
		for (const seq of [lockCodeSequence(), decodeSequence()]) {
			expect(delays(seq)).toEqual([...Array(seq.length - 1).fill(200), 0]);
		}
	});
});
