import { describe, expect, test } from 'bun:test';
import vectors from '../../../protocol/vectors.json';
import * as cmd from './commands';

// protocol/vectors.json is the shared contract between this TypeScript
// implementation and the Kotlin port in android/. Both sides run these
// vectors, so neither can drift without a test going red.

const hex = (u: Uint8Array) =>
	[...u].map((b) => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');

describe('protocol vectors', () => {
	for (const v of vectors) {
		const label = `${v.fn}(${v.args.join(', ')}) → ${v.bytes}`;
		test(label, () => {
			const fn = (cmd as Record<string, unknown>)[v.fn] as (...a: number[]) => Uint8Array;
			expect(fn).toBeFunction();
			expect(hex(fn(...v.args))).toBe(v.bytes);
		});
	}

	test('every exported command is covered', () => {
		const covered = new Set(vectors.map((v) => v.fn));
		const exported = Object.keys(cmd).filter(
			(k) => typeof (cmd as Record<string, unknown>)[k] === 'function'
		);
		expect(exported.filter((name) => !covered.has(name))).toEqual([]);
	});
});
