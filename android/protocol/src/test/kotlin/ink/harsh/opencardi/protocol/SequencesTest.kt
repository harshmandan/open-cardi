package ink.harsh.opencardi.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors src/lib/protocol/sequences.test.ts. */
class SequencesTest {

	private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

	// Transcribed from Cardi-RGBW 1.2.2 BYLightActivity.suomaThread / jiemaThread.
	private val tail =
		List(5) { listOf("ED 0F 00 00 00 E9", "ED F0 00 00 00 E9") }.flatten() +
			"ED F0 00 00 00 E9"

	@Test
	fun lockCodeIsRedThenFiveOffOnPairsThenOn() {
		assertEquals(listOf("ED 49 FF 00 00 E9") + tail, Sequences.lockCode().map { hex(it.bytes) })
	}

	@Test
	fun decodeIsGreenThenFiveOffOnPairsThenOn() {
		assertEquals(listOf("ED 49 00 FF 00 E9") + tail, Sequences.decode().map { hex(it.bytes) })
	}

	@Test
	fun twoHundredMsBetweenFramesNothingAfterTheLast() {
		for (seq in listOf(Sequences.lockCode(), Sequences.decode())) {
			assertEquals(List(seq.size - 1) { 200L } + 0L, seq.map { it.delayAfterMs })
		}
	}
}
