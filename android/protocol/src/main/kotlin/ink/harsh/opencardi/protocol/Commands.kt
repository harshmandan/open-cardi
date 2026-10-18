package ink.harsh.opencardi.protocol

/**
 * Wire commands for ConSmart-family RGB kits (Cardi K3 / K4 and friends).
 *
 * This is a direct port of `src/lib/protocol/commands.ts` in the web app. Both
 * implementations are checked against `protocol/vectors.json`, so neither can
 * drift without a test going red. Keep the two in step: if you change a builder
 * here, change it there, and regenerate the vectors.
 */
object Commands {

	private fun clamp(v: Int, lo: Int, hi: Int) = maxOf(lo, minOf(hi, v))

	/**
	 * Brightness is pre-multiplied into the RGB triple — the kit has no separate
	 * brightness opcode for colour mode. Integer division truncates, matching
	 * the JS `& 0xff` on a float result.
	 */
	fun setColor(r: Int, g: Int, b: Int, brightnessPct: Int = 100): ByteArray {
		val pct = clamp(brightnessPct, 0, 100)
		return byteArrayOf(
			0xED.toByte(),
			0x49,
			(r * pct / 100 and 0xFF).toByte(),
			(g * pct / 100 and 0xFF).toByte(),
			(b * pct / 100 and 0xFF).toByte(),
			0xE9.toByte(),
		)
	}

	fun masterOn(): ByteArray =
		byteArrayOf(0xED.toByte(), 0xF0.toByte(), 0x00, 0x00, 0x00, 0xE9.toByte())

	fun masterOff(): ByteArray =
		byteArrayOf(0xED.toByte(), 0x0F, 0x00, 0x00, 0x00, 0xE9.toByte())

	/** Scopes the next colour/pattern write to one zone. See [Zones]. */
	fun selectZone(value: Int): ByteArray =
		byteArrayOf(0xFE.toByte(), (value and 0xFF).toByte(), 0xEF.toByte())

	fun setPattern(index: Int): ByteArray {
		val op = Patterns.list.getOrNull(index)?.opcode
			?: throw IllegalArgumentException(
				"pattern index $index out of range (0..${Patterns.list.size - 1})"
			)
		return byteArrayOf(0xED.toByte(), op.toByte(), 0x00, 0x00, 0x00, 0xE9.toByte())
	}

	fun setSpeed(speed: Int): ByteArray =
		byteArrayOf(0x30, clamp(speed, 1, 100).toByte(), 0x03)

	fun setPatternBrightness(pct: Int): ByteArray =
		byteArrayOf(0x31, clamp(pct, 1, 100).toByte(), 0x13)

	/** No-op on K3 (RGB only). Real on kits with a separate W channel. */
	fun setWarmWhite(pct: Int): ByteArray =
		byteArrayOf(0xC3.toByte(), clamp(pct, 0, 100).toByte(), 0x3C)

	/** Wire value is mode + 1; [MicModes] ids are 0-based. */
	fun micOn(mode: Int): ByteArray {
		val m = ((clamp(mode, 0, 3) + 1) and 0xFF).toByte()
		return byteArrayOf(0xED.toByte(), 0x34, m, m, m, 0xE9.toByte())
	}

	fun micOff(): ByteArray =
		byteArrayOf(0xED.toByte(), 0x33, 0x01, 0x01, 0x01, 0xE9.toByte())

	/** Black is replaced with a dim floor so the kit never goes fully dark. */
	fun musicColor(r: Int, g: Int, b: Int): ByteArray {
		var rr = r and 0xFF
		var gg = g and 0xFF
		var bb = b and 0xFF
		if (rr == 0 && gg == 0 && bb == 0) {
			rr = 16; gg = 5; bb = 5
		}
		return byteArrayOf(
			0xED.toByte(), 0x51, rr.toByte(), gg.toByte(), bb.toByte(), 0xE9.toByte()
		)
	}

	/** 1 = Classic, 2 = Starlight. Changes how the MCU renders, not what we send. */
	fun setAppMode(value: Int): ByteArray =
		byteArrayOf(0xFA.toByte(), (value and 0xFF).toByte(), 0xAF.toByte())

	fun fragranceOn(): ByteArray = byteArrayOf(0xF8.toByte(), 0xF0.toByte(), 0x8F.toByte())

	fun fragranceOff(): ByteArray = byteArrayOf(0xF8.toByte(), 0x0F, 0x8F.toByte())

	/**
	 * Query that triggers a master-state notify (`33 ?? ?? 34`). Doubles as the
	 * first handshake write and as a post-colour probe.
	 */
	fun getLightData(): ByteArray = byteArrayOf(0x3C, 0xAA.toByte(), 0xAA.toByte(), 0x3D)
}
