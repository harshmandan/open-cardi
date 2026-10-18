package ink.harsh.opencardi.ble

/** What the phone UI and the car UI both render. Owned by [CardiService]. */
data class CardiState(
	val connection: Connection = Connection.DISCONNECTED,
	val deviceName: String? = null,
	val deviceAddress: String? = null,
	val masterOn: Boolean = true,
	val zoneId: String = "guide",
	val color: Rgb = Rgb(255, 255, 255),
	val brightness: Int = 100,
	val patternIndex: Int? = null,
	val speed: Int = 50,
	val micMode: Int? = null,
	/** Pinned colours, newest first. Rendered ahead of everything else. */
	val favorites: List<Favorite> = emptyList(),
	/**
	 * Colours mixed in the picker. They persist so they stay reachable, but
	 * they are not favourites — starring is a separate, deliberate act.
	 */
	val customColors: List<Favorite> = emptyList(),
	/** Kits seen by the picker's scan, strongest signal first. */
	val foundKits: List<FoundKit> = emptyList(),
	val discovering: Boolean = false,
	/** "lock" or "decode" while that sequence is being sent. */
	val knock: String? = null,
) {
	val isConnected: Boolean get() = connection == Connection.CONNECTED
}

enum class Connection { DISCONNECTED, SCANNING, CONNECTING, RECONNECTING, CONNECTED }

/** A kit seen while scanning, for the device picker. */
data class FoundKit(val address: String, val name: String?, val rssi: Int)

data class Rgb(val r: Int, val g: Int, val b: Int)

/** A pinned colour together with the brightness it was pinned at. */
data class Favorite(val color: Rgb, val brightness: Int)
