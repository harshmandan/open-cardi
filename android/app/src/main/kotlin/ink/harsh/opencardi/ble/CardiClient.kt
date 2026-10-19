package ink.harsh.opencardi.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import ink.harsh.opencardi.protocol.Commands
import ink.harsh.opencardi.protocol.DEVICE_NAME_PREFIXES
import ink.harsh.opencardi.protocol.HANDSHAKE_WRITES
import ink.harsh.opencardi.protocol.NOTIFY_CHARACTERISTIC
import ink.harsh.opencardi.protocol.SERVICE_UUID
import ink.harsh.opencardi.protocol.TimedFrame
import ink.harsh.opencardi.protocol.WRITE_CHARACTERISTIC
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CardiClient"

/**
 * Frame-level log, the Android counterpart of the PWA's wire log. Watch it with
 * `adb logcat -s CardiWire` while driving to see exactly what reached the kit.
 */
private const val WIRE = "CardiWire"

private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

/**
 * Android surfaces GATT failures as bare integers. The ones we actually see are
 * named here so the wire log says why a link dropped instead of "status=8".
 */
private fun gattStatus(code: Int): String = when (code) {
	0 -> "SUCCESS"
	8 -> "CONN_TIMEOUT (supervision timeout — peer stopped responding)"
	19 -> "TERMINATE_PEER_USER (the kit hung up)"
	22 -> "TERMINATE_LOCAL_HOST (the phone hung up)"
	34 -> "CONN_LMP_TIMEOUT"
	62 -> "CONN_FAIL_ESTABLISH"
	133 -> "GATT_ERROR (generic — usually a failed connect)"
	147 -> "CONN_TERMINATED (147)"
	257 -> "FAILURE"
	else -> "status=$code"
}

/** CCCD — standard descriptor for enabling notifications. */
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * The official Cardi Tech app caps output at roughly 10 Hz. Push the MCU faster
 * and it drops frames or flashes. This floor applies to *every* write, not just
 * colour — the chip's processing pace is global.
 */
private const val MIN_INTERVAL_MS = 100L

private const val WRITE_ACK_TIMEOUT_MS = 500L
private const val HEARTBEAT_MS = 3_000L
private const val MASTER_WINDOW_MS = 2_500L
private const val MASTER_MAX_IN_WINDOW = 4

/**
 * GATT client for ConSmart-family kits. Port of `src/lib/ble/client.ts`, with
 * the latest-wins write coalescer from `controls.svelte.ts` folded in — on
 * Android an overflowing GATT queue surfaces as "GATT operation in progress"
 * and drops the link, so the coalescer is not optional here.
 */
/** True for names our kits advertise (CarDiLits, iLits, BaoYin, …). */
fun isKitName(name: String?): Boolean =
	name != null && DEVICE_NAME_PREFIXES.any { name.startsWith(it) }

@SuppressLint("MissingPermission") // callers hold BLUETOOTH_SCAN / BLUETOOTH_CONNECT
class CardiClient(
	private val context: Context,
	private val scope: CoroutineScope,
	private val onConnection: (Connection, BluetoothDevice?) -> Unit,
	private val onNotify: (ByteArray) -> Unit,
) {
	private val manager = context.getSystemService(BluetoothManager::class.java)
	private val adapter get() = manager?.adapter

	private var gatt: BluetoothGatt? = null
	private var writeChar: BluetoothGattCharacteristic? = null

	/** Latest-wins: one pending payload per command kind. */
	private val pending = LinkedHashMap<String, ByteArray>()
	private val lock = Mutex()
	private var draining = false
	private var lastSendAt = 0L
	private var writeAck: CompletableDeferred<Boolean>? = null
	private var connectedAt = 0L
	private var rssiJob: kotlinx.coroutines.Job? = null
	private val masterSendTimes = ArrayList<Long>()

	// Heartbeat: ask for master state every few seconds and log when the kit
	// stops answering. The kit has no "I'm stuck" message, so otherwise the log
	// shows writes going into a void with no way to tell which one wedged it.
	// Any 33 … 34 reply counts as alive, whoever asked for it.
	private var heartbeatJob: kotlinx.coroutines.Job? = null
	@Volatile private var heartbeatPendingSince = 0L
	@Volatile private var heartbeatMisses = 0

	// --- scanning -----------------------------------------------------------

	private var scanCallback: ScanCallback? = null

	/**
	 * Scan and report every kit in range, without connecting — the device
	 * picker shows them so the user chooses, like the vendor app's list.
	 * Reports the same kit again as its signal strength changes.
	 */
	fun discover(onFound: (FoundKit) -> Unit, timeoutMs: Long = 15_000, onDone: () -> Unit = {}) {
		val scanner = adapter?.bluetoothLeScanner ?: run {
			Log.w(TAG, "no BLE scanner — is Bluetooth off?")
			onDone()
			return
		}
		stopScan()

		val cb = object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult) {
				val name = result.device.name ?: result.scanRecord?.deviceName
				// By name only. FFF0 is a generic vendor service that plenty of
				// unrelated BLE gadgets advertise — matching on it once had the
				// app remember a stranger's device ("FS-094125") as the kit.
				if (!isKitName(name)) return
				onFound(FoundKit(result.device.address, name, result.rssi))
			}

			override fun onScanFailed(errorCode: Int) {
				Log.w(TAG, "scan failed: $errorCode")
				stopScan()
				onDone()
			}
		}
		scanCallback = cb

		// No ScanFilter: names can't be prefix-matched by one, so the name
		// check above does the filtering.
		val settings = ScanSettings.Builder()
			.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
			.build()
		scanner.startScan(null, settings, cb)

		scope.launch {
			delay(timeoutMs)
			if (scanCallback === cb) {
				stopScan()
				onDone()
			}
		}
	}

	fun stopDiscovery() = stopScan()

	/** The cached name of a device we've seen, without connecting to it. */
	fun cachedName(address: String): String? =
		runCatching { adapter?.getRemoteDevice(address)?.name }.getOrNull()

	private fun stopScan() {
		scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
		scanCallback = null
	}

	/** Reconnect to a remembered kit — no chooser, no user gesture. */
	fun connect(address: String) {
		val device = adapter?.getRemoteDevice(address) ?: return
		connect(device)
	}

	// The non-deprecated replacement, connectGatt(BluetoothGattConnectionSettings,
	// Executor, BluetoothGattCallback), is API 37+ only — far above our minSdk, and
	// there is no compat shim. The four-arg overload is deprecated but universal,
	// so we keep it until API 37 is a reasonable floor.
	@Suppress("DEPRECATION")
	private fun connect(device: BluetoothDevice) {
		disconnect()
		onConnection(Connection.CONNECTING, device)
		// TRANSPORT_LE is explicit on purpose: these kits are BLE-only, and letting
		// the stack pick AUTO can land on BR/EDR and fail service discovery.
		// autoConnect = false: a direct, fast connection. With true the stack
		// opens a slow background link, which is what the wire log showed
		// dropping with status 8 (supervision timeout) every ten seconds and
		// re-running the handshake each time.
		gatt = device.connectGatt(
			context,
			/* autoConnect = */ false,
			callback,
			BluetoothDevice.TRANSPORT_LE,
		)
	}

	fun disconnect() {
		stopScan()
		rssiJob?.cancel()
		rssiJob = null
		stopHeartbeat()
		connectedAt = 0
		gatt?.close()
		gatt = null
		writeChar = null
		scope.launch { lock.withLock { pending.clear() } }
	}

	// --- writes -------------------------------------------------------------

	/**
	 * Queue a write. A newer payload for the same [kind] replaces an older one
	 * that hasn't gone out yet, so a slider drag sends its final value rather
	 * than every intermediate one.
	 */
	fun send(kind: String, bytes: ByteArray) {
		scope.launch {
			lock.withLock { pending[kind] = bytes }
			drain()
		}
	}

	/**
	 * Send frames in order with exact pauses, holding the write queue so
	 * nothing lands between them. The coalescer keeps one payload per kind,
	 * which would collapse a knock's alternating master writes into one.
	 */
	suspend fun sendSequence(frames: List<TimedFrame>): Boolean {
		while (lock.withLock { if (draining) true else { draining = true; false } }) delay(20)
		try {
			val gap = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSendAt)
			if (gap > 0) delay(gap)
			for (f in frames) {
				if (writeChar == null) return false
				writeNow(f.bytes)
				lastSendAt = System.currentTimeMillis()
				if (f.delayAfterMs > 0) delay(f.delayAfterMs)
			}
			return true
		} finally {
			lock.withLock { draining = false }
			drain()
		}
	}

	private suspend fun drain() {
		if (lock.withLock { if (draining) true else { draining = true; false } }) return
		try {
			while (true) {
				val entry = lock.withLock { pending.entries.firstOrNull()?.key } ?: break
				if (writeChar == null) break

				val elapsed = System.currentTimeMillis() - lastSendAt
				if (elapsed < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - elapsed)

				// Lock code and Decode are a colour followed by rapid master
				// off/on toggles, so hammering the power button could send one
				// by accident. The knock needs 11 master writes in ~2.2 s; this
				// allows 4 per 2.5 s.
				if (entry == "master") {
					val now = System.currentTimeMillis()
					while (masterSendTimes.isNotEmpty() && now - masterSendTimes.first() > MASTER_WINDOW_MS) {
						masterSendTimes.removeAt(0)
					}
					if (masterSendTimes.size >= MASTER_MAX_IN_WINDOW) {
						delay(MASTER_WINDOW_MS - (now - masterSendTimes.first()))
						masterSendTimes.removeAt(0)
					}
					masterSendTimes.add(System.currentTimeMillis())
				}

				// Re-read after the wait: a slider may have produced a fresher
				// payload for this kind while we slept. Latest-wins still holds.
				val payload = lock.withLock { pending.remove(entry) } ?: continue
				lastSendAt = System.currentTimeMillis()
				writeNow(payload)
			}
		} finally {
			lock.withLock { draining = false }
		}
	}

	private suspend fun writeNow(payload: ByteArray) {
		val g = gatt ?: return
		val c = writeChar ?: return
		val ack = CompletableDeferred<Boolean>()
		writeAck = ack

		Log.i(WIRE, "→ ${hex(payload)}")
		val status = g.writeCharacteristic(
			c, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
		)
		if (status != BluetoothStatusCodes.SUCCESS) {
			Log.w(TAG, "write rejected: $status")
			writeAck = null
			return
		}
		// Wait for the stack to drain the write before queueing the next one.
		withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
		writeAck = null
	}

	// --- gatt callback ------------------------------------------------------

	private val callback = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
			val state = when (newState) {
				BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
				BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
				else -> "state=$newState"
			}
			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> {
					Log.i(WIRE, "link: $state ${gattStatus(status)}")
					g.discoverServices()
				}
				BluetoothProfile.STATE_DISCONNECTED -> {
					// How long the link survived is the useful number when the
					// drops look periodic.
					val uptime =
						if (connectedAt > 0) "${System.currentTimeMillis() - connectedAt}ms" else "n/a"
					Log.w(WIRE, "link: $state after $uptime — ${gattStatus(status)}")
					connectedAt = 0
					writeChar = null
					stopHeartbeat()
					onConnection(Connection.DISCONNECTED, g.device)
				}
			}
		}

		override fun onDescriptorWrite(
			g: BluetoothGatt,
			d: BluetoothGattDescriptor,
			status: Int,
		) {
			Log.i(WIRE, "notifications enabled: ${gattStatus(status)}")
		}

		override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
			Log.i(WIRE, "mtu=$mtu ${gattStatus(status)}")
			// With LabFlags.bigMtu the subscription waits for the MTU exchange,
			// so the two GATT operations don't overlap.
			if (LabFlags.bigMtu && writeChar != null) subscribe(g)
		}

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
			// Link quality in the seconds before a drop is the thing worth
			// having when the disconnects look periodic rather than triggered.
			Log.i(WIRE, "rssi=${rssi}dBm")
		}

		override fun onServiceChanged(g: BluetoothGatt) {
			Log.w(WIRE, "kit reported its GATT services changed — re-discovering")
			g.discoverServices()
		}

		override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
			Log.i(WIRE, "services discovered: ${gattStatus(status)}")
			Log.i(WIRE, "lab: bigMtu=${LabFlags.bigMtu} highPriority=${LabFlags.highPriority}")
			// A high-priority interval keeps the link alive under our 10 Hz
			// write cadence; the default balanced interval is slow enough that
			// the kit was timing the connection out mid-drag.
			if (LabFlags.highPriority) {
				g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
			}
			val service = g.getService(SERVICE_UUID) ?: run {
				Log.w(TAG, "service ${SERVICE_UUID} not found")
				onConnection(Connection.DISCONNECTED, g.device)
				return
			}
			writeChar = service.getCharacteristic(WRITE_CHARACTERISTIC)

			// captures/PROTOCOL_v1.md records the two sources disagreeing: the
			// HCI snoop shows the official app using Write Command throughout,
			// the APK decompile shows colour drags ACK-gated with-response.
			// Log what the characteristic actually offers so the next session
			// settles it from the device rather than from the notes.
			writeChar?.let { c ->
				val p = c.properties
				Log.i(
					WIRE,
					"FFE2 properties=0x%02X write=%b writeNoResponse=%b".format(
						p,
						p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0,
						p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0,
					),
				)
			}

			if (LabFlags.bigMtu) {
				Log.i(WIRE, "lab: requesting MTU 517")
				g.requestMtu(517)
			} else {
				subscribe(g)
			}

			scope.launch {
				// Replay the vendor app's connect handshake. The first write
				// doubles as a state query whose reply seeds master/zone. Each
				// step is logged with how long the kit took to acknowledge it.
				Log.i(WIRE, "handshake: begin (${HANDSHAKE_WRITES.size} steps)")
				HANDSHAKE_WRITES.forEachIndexed { i, w ->
					val started = System.currentTimeMillis()
					writeNow(w)
					Log.i(
						WIRE,
						"handshake: step ${i + 1}/${HANDSHAKE_WRITES.size} ${hex(w)} " +
							"acked in ${System.currentTimeMillis() - started}ms",
					)
					delay(MIN_INTERVAL_MS)
				}
				Log.i(WIRE, "handshake: done")
				connectedAt = System.currentTimeMillis()
				onConnection(Connection.CONNECTED, g.device)

				rssiJob?.cancel()
				rssiJob = scope.launch {
					while (true) {
						delay(3_000)
						if (gatt == null) break
						gatt?.readRemoteRssi()
					}
				}
				startHeartbeat()
			}
		}

		override fun onCharacteristicWrite(
			g: BluetoothGatt,
			c: BluetoothGattCharacteristic,
			status: Int,
		) {
			if (status != BluetoothGatt.GATT_SUCCESS) {
				Log.w(WIRE, "write rejected by the kit: ${gattStatus(status)}")
			}
			writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
		}

		override fun onCharacteristicChanged(
			g: BluetoothGatt,
			c: BluetoothGattCharacteristic,
			value: ByteArray,
		) {
			Log.i(WIRE, "← ${hex(value)}")
			if (value.size == 4 && value[0] == 0x33.toByte() && value[3] == 0x34.toByte()) {
				heartbeatAnswered()
			}
			onNotify(value)
		}
	}

	private fun subscribe(g: BluetoothGatt) {
		g.getService(SERVICE_UUID)?.getCharacteristic(NOTIFY_CHARACTERISTIC)?.let { notify ->
			g.setCharacteristicNotification(notify, true)
			notify.getDescriptor(CCCD)?.let { d ->
				g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
			}
		}
	}

	// --- heartbeat ----------------------------------------------------------

	private fun startHeartbeat() {
		stopHeartbeat()
		heartbeatJob = scope.launch {
			while (true) {
				delay(HEARTBEAT_MS)
				if (writeChar == null) break
				val now = System.currentTimeMillis()
				if (heartbeatPendingSince != 0L) {
					heartbeatMisses += 1
					Log.w(
						WIRE,
						"heartbeat: no reply ($heartbeatMisses in a row, " +
							"silent ${now - heartbeatPendingSince}ms)",
					)
				} else {
					heartbeatPendingSince = now
				}
				send("heartbeat", Commands.getLightData())
			}
		}
	}

	private fun stopHeartbeat() {
		heartbeatJob?.cancel()
		heartbeatJob = null
		heartbeatPendingSince = 0L
		heartbeatMisses = 0
	}

	private fun heartbeatAnswered() {
		if (heartbeatMisses > 0 && heartbeatPendingSince != 0L) {
			Log.i(
				WIRE,
				"heartbeat: kit answered again after " +
					"${System.currentTimeMillis() - heartbeatPendingSince}ms",
			)
		}
		heartbeatPendingSince = 0L
		heartbeatMisses = 0
	}
}
