package ink.harsh.opencardi.protocol

import java.util.UUID

/** Port of `src/lib/protocol/constants.ts`. */

private fun shortUuid(short: Int): UUID =
	UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", short))

/** Single GATT service the whole ConSmart family exposes. */
val SERVICE_UUID: UUID = shortUuid(0xFFF0)

/** Write Without Response. Every command goes here. */
val WRITE_CHARACTERISTIC: UUID = shortUuid(0xFFE2)

/** Notify. Handshake replies and state readbacks arrive here. */
val NOTIFY_CHARACTERISTIC: UUID = shortUuid(0xFFE1)

/** Advertised name prefixes, from the official app's COMPANY_NAME regex. */
val DEVICE_NAME_PREFIXES = listOf(
	"iLits", "CarDiLits", "BaoYin", "CaChuang", "CarL", "MTF-LIGHT", "AIKON-LIT", "Dream", "Flash",
)

/**
 * Replayed verbatim on every connect, 100 ms apart. The kit does not require it,
 * but the official app always sends it and the first write doubles as a state
 * query whose replies seed our local state.
 */
val HANDSHAKE_WRITES: List<ByteArray> = listOf(
	byteArrayOf(0x3C, 0xAA.toByte(), 0xAA.toByte(), 0x3D),
	byteArrayOf(0x0E, 0xAA.toByte(), 0xE0.toByte()),
	byteArrayOf(0x18, 0x00, 0x81.toByte()),
	byteArrayOf(0x17, 0x00, 0x71),
	byteArrayOf(0x16, 0x00, 0x61),
)

data class Zone(
	val id: String,
	val label: String,
	val value: Int,
	/** K3 kits only wire up "all" zones; the rest are K4+. */
	val k4Only: Boolean = false,
	val subtitle: String? = null,
)

object Zones {
	val list = listOf(
		Zone("overall", "Overall", 1),
		Zone("guide", "Light Guide", 2, subtitle = "Front + doors on K3"),
		Zone("doors", "Doors", 3, k4Only = true),
		Zone("floor", "Floor", 4, subtitle = "Footwell"),
		Zone("skylight", "Skylight", 5, k4Only = true),
		Zone("chassis-strobe", "Chassis/Strobe", 6, k4Only = true),
		Zone("grill", "Grill", 7, k4Only = true),
		Zone("chassis", "Chassis", 8, k4Only = true),
		Zone("strobe", "Strobe", 9, k4Only = true),
	)

	fun byId(id: String): Zone? = list.firstOrNull { it.id == id }

	fun byValue(value: Int): Zone? = list.firstOrNull { it.value == value }
}

data class Pattern(
	val index: Int,
	val opcode: Int,
	val label: String,
	/**
	 * Flashing patterns. Excluded from the car UI — a strobing cabin while
	 * driving is a safety problem and a car-app review rejection.
	 */
	val strobe: Boolean = false,
)

object Patterns {
	val list = listOf(
		Pattern(0, 0x35, "Smooth color sweep"),
		Pattern(1, 0x36, "Multi-color strobe", strobe = true),
		Pattern(2, 0x37, "Breathing"),
		Pattern(3, 0x38, "Blue breathing"),
		Pattern(4, 0x39, "Yellow breathing"),
		Pattern(5, 0x3A, "Cyan breathing"),
		Pattern(6, 0x3B, "Pink breathing"),
		Pattern(7, 0x3C, "White pulse"),
		Pattern(8, 0x3D, "Red ↔ green pulse"),
		Pattern(9, 0x3E, "Red ↔ blue pulse"),
		Pattern(10, 0x3F, "Green ↔ blue pulse"),
		Pattern(11, 0x40, "Random strobe", strobe = true),
		Pattern(12, 0x41, "Red strobe", strobe = true),
		Pattern(13, 0x42, "Green strobe", strobe = true),
		Pattern(14, 0x43, "Blue strobe", strobe = true),
		Pattern(15, 0x44, "Yellow strobe", strobe = true),
		Pattern(16, 0x45, "Cyan strobe", strobe = true),
		Pattern(17, 0x46, "Pink strobe", strobe = true),
		Pattern(18, 0x47, "White strobe", strobe = true),
		Pattern(19, 0x48, "Fast color sweep"),
		// 0x49 is skipped — that opcode is setColor.
		Pattern(20, 0x4A, "RGB flash", strobe = true),
		Pattern(21, 0x4B, "Smooth color cycle"),
		Pattern(22, 0x4C, "RGB strobe", strobe = true),
	)

	/** Safe to offer on a head unit. */
	val calm = list.filterNot { it.strobe }
}

data class MicMode(val id: Int, val label: String, val subtitle: String)

object MicModes {
	val list = listOf(
		MicMode(0, "Classic", "Smooth color changes"),
		MicMode(1, "Soft", "Softer transitions"),
		MicMode(2, "Jump", "Off when silent, flash on sound"),
		MicMode(3, "Dance", "Like Jump, more sensitive"),
	)
}
