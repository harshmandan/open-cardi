package ink.harsh.opencardi.protocol

/** A frame plus the pause the vendor app leaves after sending it. */
class TimedFrame(val bytes: ByteArray, val delayAfterMs: Long)

/**
 * Lock code (锁码) and Decode (解码) from the Cardi-RGBW 1.2.2 app
 * (BYLightActivity.suomaThread / jiemaThread). Neither has an opcode of its
 * own: the firmware recognises a colour followed by five master off/on pairs
 * at a 200 ms beat, and the colour picks the action. Keep the bytes and the
 * timing exactly as the vendor sends them; `src/lib/protocol/sequences.ts` is
 * the web twin. See notes/RGBW_1.2.2_FINDINGS.md.
 */
object Sequences {
	const val KNOCK_STEP_MS = 200L
	private const val KNOCK_TOGGLES = 5

	private fun knock(color: ByteArray): List<TimedFrame> = buildList {
		add(TimedFrame(color, KNOCK_STEP_MS))
		repeat(KNOCK_TOGGLES) {
			add(TimedFrame(Commands.masterOff(), KNOCK_STEP_MS))
			add(TimedFrame(Commands.masterOn(), KNOCK_STEP_MS))
		}
		add(TimedFrame(Commands.masterOn(), 0))
	}

	fun lockCode(): List<TimedFrame> = knock(Commands.setColor(255, 0, 0, 100))

	fun decode(): List<TimedFrame> = knock(Commands.setColor(0, 255, 0, 100))
}
