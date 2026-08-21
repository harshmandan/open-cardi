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
import ink.harsh.opencardi.protocol.DEVICE_NAME_PREFIXES
import ink.harsh.opencardi.protocol.HANDSHAKE_WRITES
import ink.harsh.opencardi.protocol.NOTIFY_CHARACTERISTIC
import ink.harsh.opencardi.protocol.SERVICE_UUID
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

/** CCCD — standard descriptor for enabling notifications. */
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * The official Cardi Tech app caps output at roughly 10 Hz. Push the MCU faster
 * and it drops frames or flashes. This floor applies to *every* write, not just
 * colour — the chip's processing pace is global.
 */
private const val MIN_INTERVAL_MS = 100L

private const val WRITE_ACK_TIMEOUT_MS = 500L

/**
 * GATT client for ConSmart-family kits. Port of `src/lib/ble/client.ts`, with
 * the latest-wins write coalescer from `controls.svelte.ts` folded in — on
 * Android an overflowing GATT queue surfaces as "GATT operation in progress"
 * and drops the link, so the coalescer is not optional here.
 */
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

	// --- scanning -----------------------------------------------------------

	private var scanCallback: ScanCallback? = null

	/** Scans for a kit and connects to the first match. */
	fun scanAndConnect(timeoutMs: Long = 15_000) {
		val scanner = adapter?.bluetoothLeScanner ?: run {
			Log.w(TAG, "no BLE scanner — is Bluetooth off?")
			onConnection(Connection.DISCONNECTED, null)
			return
		}
		stopScan()
		onConnection(Connection.SCANNING, null)

		val cb = object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult) {
				val name = result.device.name ?: result.scanRecord?.deviceName
				val serviceMatch =
					result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
				val nameMatch = name != null && DEVICE_NAME_PREFIXES.any { name.startsWith(it) }
				if (!serviceMatch && !nameMatch) return

				stopScan()
				connect(result.device)
			}

			override fun onScanFailed(errorCode: Int) {
				Log.w(TAG, "scan failed: $errorCode")
				stopScan()
				onConnection(Connection.DISCONNECTED, null)
			}
		}
		scanCallback = cb

		// Filter on the service UUID. Name prefixes can't be expressed as a
		// ScanFilter (it only does exact names), so those are matched above.
		val filters = listOf(
			ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
		)
		val settings = ScanSettings.Builder()
			.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
			.build()
		scanner.startScan(filters, settings, cb)

		scope.launch {
			delay(timeoutMs)
			if (scanCallback != null) {
				stopScan()
				onConnection(Connection.DISCONNECTED, null)
			}
		}
	}

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

	private suspend fun drain() {
		if (lock.withLock { if (draining) true else { draining = true; false } }) return
		try {
			while (true) {
				val entry = lock.withLock { pending.entries.firstOrNull()?.key } ?: break
				if (writeChar == null) break

				val elapsed = System.currentTimeMillis() - lastSendAt
				if (elapsed < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - elapsed)

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
			Log.i(WIRE, "connection state=$newState status=$status")
			when (newState) {
				BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
				BluetoothProfile.STATE_DISCONNECTED -> {
					writeChar = null
					onConnection(Connection.DISCONNECTED, g.device)
				}
			}
		}

		override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
			// A high-priority interval keeps the link alive under our 10 Hz
			// write cadence; the default balanced interval is slow enough that
			// the kit was timing the connection out mid-drag.
			g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
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

			service.getCharacteristic(NOTIFY_CHARACTERISTIC)?.let { notify ->
				g.setCharacteristicNotification(notify, true)
				notify.getDescriptor(CCCD)?.let { d ->
					g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
				}
			}

			scope.launch {
				// Replay the official app's connect handshake. The first write
				// doubles as a state query whose reply seeds master/zone.
				for (w in HANDSHAKE_WRITES) {
					writeNow(w)
					delay(MIN_INTERVAL_MS)
				}
				onConnection(Connection.CONNECTED, g.device)
			}
		}

		override fun onCharacteristicWrite(
			g: BluetoothGatt,
			c: BluetoothGattCharacteristic,
			status: Int,
		) {
			writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
		}

		override fun onCharacteristicChanged(
			g: BluetoothGatt,
			c: BluetoothGattCharacteristic,
			value: ByteArray,
		) {
			Log.i(WIRE, "← ${hex(value)}")
			onNotify(value)
		}
	}
}
