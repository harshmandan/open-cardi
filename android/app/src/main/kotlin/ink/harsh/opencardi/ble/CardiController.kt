package ink.harsh.opencardi.ble

import android.content.Context
import android.content.SharedPreferences
import ink.harsh.opencardi.protocol.Commands
import ink.harsh.opencardi.protocol.Zones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the kit. The phone UI and the Android Auto screens
 * both read [state] and call these methods — there is no second copy of this
 * anywhere, which is the whole reason this app is native rather than a WebView.
 */
object CardiController {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val _state = MutableStateFlow(CardiState())
	val state: StateFlow<CardiState> = _state.asStateFlow()

	private var client: CardiClient? = null
	private var prefs: SharedPreferences? = null

	private const val PREFS = "open-cardi"
	private const val KEY_ADDRESS = "device-address"
	private const val KEY_FAVORITES = "favorites"
	private const val KEY_LAST = "last-color"
	private const val KEY_CUSTOM = "custom-colors"

	fun init(context: Context) {
		if (client != null) return
		val app = context.applicationContext
		prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		_state.update {
			val last = loadLast()
			it.copy(
				favorites = loadFavorites(),
				customColors = loadList(KEY_CUSTOM),
				color = last?.color ?: it.color,
				brightness = last?.brightness ?: it.brightness,
			)
		}
		client = CardiClient(
			context = app,
			scope = scope,
			onConnection = { connection, device ->
				_state.update {
					it.copy(
						connection = connection,
						deviceName = device?.name ?: it.deviceName,
						deviceAddress = device?.address ?: it.deviceAddress,
					)
				}
				if (connection == Connection.CONNECTED && device != null) {
					prefs?.edit()?.putString(KEY_ADDRESS, device.address)?.apply()
				}
			},
			onNotify = ::applyNotify,
		)
	}

	/** Address of the last kit we connected to, if any. */
	val rememberedAddress: String? get() = prefs?.getString(KEY_ADDRESS, null)

	/** Reconnect silently if we know the kit, otherwise scan. */
	fun connect() {
		val c = client ?: return
		val known = rememberedAddress
		if (known != null) c.connect(known) else c.scanAndConnect()
	}

	fun forgetAndRescan() {
		prefs?.edit()?.remove(KEY_ADDRESS)?.apply()
		client?.scanAndConnect()
	}

	fun disconnect() {
		client?.disconnect()
		_state.update { it.copy(connection = Connection.DISCONNECTED) }
	}

	/** Last applied colour, so reopening the app does not reset to white. */
	private fun loadLast(): Favorite? =
		prefs?.getString(KEY_LAST, null)
			?.split(",")
			?.mapNotNull(String::toIntOrNull)
			?.takeIf { it.size == 4 }
			?.let { Favorite(Rgb(it[0], it[1], it[2]), it[3]) }

	private fun saveLast() {
		val s = _state.value
		prefs?.edit()
			?.putString(KEY_LAST, "${s.color.r},${s.color.g},${s.color.b},${s.brightness}")
			?.apply()
	}

	// --- favorites -----------------------------------------------------------

	/**
	 * Stored as "r,g,b,brightness;…" — a list this short does not need a
	 * database. Three-field entries from the pre-brightness format are still
	 * read, defaulting to full brightness.
	 */
	private fun loadFavorites(): List<Favorite> =
		prefs?.getString(KEY_FAVORITES, null)
			?.split(";")
			?.filter { it.isNotBlank() }
			?.mapNotNull { entry ->
				val p = entry.split(",").mapNotNull(String::toIntOrNull)
				when (p.size) {
					4 -> Favorite(Rgb(p[0], p[1], p[2]), p[3])
					3 -> Favorite(Rgb(p[0], p[1], p[2]), 100)
					else -> null
				}
			}
			.orEmpty()

	private fun saveFavorites(list: List<Favorite>) = saveList(KEY_FAVORITES, list)

	private fun loadList(key: String): List<Favorite> =
		prefs?.getString(key, null)
			?.split(";")
			?.filter { it.isNotBlank() }
			?.mapNotNull { entry ->
				val p = entry.split(",").mapNotNull(String::toIntOrNull)
				if (p.size == 4) Favorite(Rgb(p[0], p[1], p[2]), p[3]) else null
			}
			.orEmpty()

	private fun saveList(key: String, list: List<Favorite>) {
		prefs?.edit()
			?.putString(
				key,
				list.joinToString(";") { "${it.color.r},${it.color.g},${it.color.b},${it.brightness}" },
			)
			?.apply()
	}

	/** Remember a mixed colour so it stays in the grid, without starring it. */
	fun addCustomColor(favorite: Favorite) {
		if (favorite in _state.value.customColors || favorite in _state.value.favorites) return
		val next = listOf(favorite) + _state.value.customColors
		saveList(KEY_CUSTOM, next)
		_state.update { it.copy(customColors = next) }
	}

	/** Pin if absent, unpin if already pinned. Newest pin goes first. */
	fun toggleFavorite(favorite: Favorite) {
		val next = _state.value.favorites.toMutableList()
		if (!next.remove(favorite)) next.add(0, favorite)
		saveFavorites(next)
		_state.update { it.copy(favorites = next) }
	}

	/** Apply a pinned colour and the brightness it was pinned at, in one write. */
	fun applyFavorite(favorite: Favorite) {
		_state.update {
			it.copy(
				color = favorite.color,
				brightness = favorite.brightness,
				patternIndex = null,
				micMode = null,
			)
		}
		pushColor()
	}

	/** Set colour and brightness together — one write, not two. */
	fun setColorAndBrightness(color: Rgb, brightness: Int) {
		_state.update {
			it.copy(
				color = color,
				brightness = brightness.coerceIn(0, 100),
				patternIndex = null,
				micMode = null,
			)
		}
		pushColor()
	}

	// --- actions ------------------------------------------------------------

	fun setMaster(on: Boolean) {
		_state.update { it.copy(masterOn = on) }
		client?.send("master", if (on) Commands.masterOn() else Commands.masterOff())
	}

	fun selectZone(zoneId: String) {
		val zone = Zones.byId(zoneId) ?: return
		_state.update { it.copy(zoneId = zone.id) }
		client?.send("zone", Commands.selectZone(zone.value))
	}

	fun setColor(color: Rgb) {
		_state.update { it.copy(color = color, patternIndex = null, micMode = null) }
		pushColor()
	}

	fun setBrightness(pct: Int) {
		val clamped = pct.coerceIn(0, 100)
		_state.update { it.copy(brightness = clamped) }
		// Pattern mode has its own brightness opcode; colour mode bakes it in.
		if (_state.value.patternIndex != null) {
			client?.send("brightness", Commands.setPatternBrightness(clamped.coerceAtLeast(1)))
		} else {
			pushColor()
		}
	}

	private fun pushColor() {
		val s = _state.value
		saveLast()
		client?.send("color", Commands.setColor(s.color.r, s.color.g, s.color.b, s.brightness))
	}

	fun setPattern(index: Int) {
		// Pattern and mic are mutually exclusive on the MCU; the pattern write
		// implicitly cancels mic, so we only need to sync our own state.
		_state.update { it.copy(patternIndex = index, micMode = null) }
		client?.send("pattern", Commands.setPattern(index))
		client?.send("speed", Commands.setSpeed(_state.value.speed))
	}

	fun clearPattern() {
		_state.update { it.copy(patternIndex = null) }
		pushColor()
	}

	fun setSpeed(speed: Int) {
		_state.update { it.copy(speed = speed.coerceIn(1, 100)) }
		client?.send("speed", Commands.setSpeed(_state.value.speed))
	}

	fun setMic(mode: Int?) {
		_state.update { it.copy(micMode = mode, patternIndex = null) }
		client?.send("mic", if (mode != null) Commands.micOn(mode) else Commands.micOff())
	}

	// --- device → app -------------------------------------------------------

	/**
	 * Reflect the notify frames documented in `captures/PROTOCOL_v1.md` back into
	 * state. The kit never echoes RGB, brightness or pattern, so those stay
	 * locally owned; only master and zone come back from the device.
	 */
	private fun applyNotify(data: ByteArray) {
		val u = data.map { it.toInt() and 0xFF }
		when {
			// 33 ?? F0/0F 34 — master readback
			u.size == 4 && u[0] == 0x33 && u[3] == 0x34 ->
				_state.update { it.copy(masterOn = u[2] == 0xF0) }

			// 0B <zone> A1 B0 — zone readback
			u.size == 4 && u[0] == 0x0B && u[3] == 0xB0 ->
				Zones.byValue(u[1])?.let { z -> _state.update { it.copy(zoneId = z.id) } }
		}
	}
}
