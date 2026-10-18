package ink.harsh.opencardi.ble

/**
 * Connection knobs for isolating the Light Guide freeze (captures/STUCK-MODE.md,
 * issue B). Set from launch extras, e.g.
 *
 *     adb shell am start -n ink.harsh.opencardi/.ui.MainActivity \
 *       --ez bigMtu true --ez highPriority false
 *
 * The OEM app uses neither: default MTU 23 and the default (balanced)
 * connection priority. With the car on and the headlamps on AUTO the kit
 * resets on colour changes whatever the client; at MTU 23 it recovers, at
 * MTU 131 (Chrome, macOS, or bigMtu here) a reset leaves it locked in a blink
 * only a power cycle clears. Keep bigMtu off.
 */
object LabFlags {
	/** Request ATT MTU 517 after discovery, like Chrome does. */
	@Volatile var bigMtu = false

	/**
	 * Request CONNECTION_PRIORITY_HIGH (15 ms interval). Off by default since
	 * 0.9.0: the vendor app never asks for it, and the 23:06 run that locked
	 * the kit had it on.
	 */
	@Volatile var highPriority = false
}
