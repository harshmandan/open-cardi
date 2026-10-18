import { masterOff, masterOn, setColor } from './commands';

// A frame plus the pause the official app leaves after sending it.
export type TimedFrame = { bytes: Uint8Array; delayAfterMs: number };

// Lock code (锁码) and Decode (解码) from the Cardi-RGBW 1.2.2 app
// (BYLightActivity.suomaThread / jiemaThread). Neither has an opcode of its
// own: the firmware recognises a colour followed by five master off/on pairs
// at a 200 ms beat. The colour picks the action, so keep the bytes and the
// timing exactly as the vendor sends them. See notes/RGBW_1.2.2_FINDINGS.md.
export const KNOCK_STEP_MS = 200;
const KNOCK_TOGGLES = 5;

function knock(color: Uint8Array): TimedFrame[] {
	const frames: TimedFrame[] = [{ bytes: color, delayAfterMs: KNOCK_STEP_MS }];
	for (let i = 0; i < KNOCK_TOGGLES; i++) {
		frames.push({ bytes: masterOff(), delayAfterMs: KNOCK_STEP_MS });
		frames.push({ bytes: masterOn(), delayAfterMs: KNOCK_STEP_MS });
	}
	frames.push({ bytes: masterOn(), delayAfterMs: 0 });
	return frames;
}

export function lockCodeSequence(): TimedFrame[] {
	return knock(setColor(255, 0, 0, 100));
}

export function decodeSequence(): TimedFrame[] {
	return knock(setColor(0, 255, 0, 100));
}
