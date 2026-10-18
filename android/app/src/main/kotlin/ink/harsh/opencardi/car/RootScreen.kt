package ink.harsh.opencardi.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.core.content.ContextCompat
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.Connection
import ink.harsh.opencardi.protocol.Patterns
import ink.harsh.opencardi.protocol.Zones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The car's home screen. Five rows — under the host's six-row cap, with no
 * paging needed at this level.
 */
class RootScreen(carContext: CarContext) : Screen(carContext) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	init {
		// Re-render whenever the kit's state moves, including changes the user
		// made on the phone or that the device reported back over notify.
		scope.launch {
			CardiController.state.drop(1).collect { invalidate() }
		}
		lifecycle.addObserver(
			object : androidx.lifecycle.DefaultLifecycleObserver {
				override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
					scope.cancel()
				}
			}
		)
	}

	private fun hasBluetoothPermission(): Boolean =
		ContextCompat.checkSelfPermission(
			carContext, Manifest.permission.BLUETOOTH_CONNECT
		) == android.content.pm.PackageManager.PERMISSION_GRANTED

	override fun onGetTemplate(): Template {
		if (!hasBluetoothPermission()) return permissionTemplate()

		val state = CardiController.state.value
		if (!state.isConnected) return connectingTemplate(state.connection)

		val zoneLabel = Zones.byId(state.zoneId)?.label ?: state.zoneId
		val patternLabel = state.patternIndex
			?.let { Patterns.list.getOrNull(it)?.label }
			?: "Off"

		val list = ItemList.Builder()
			.addItem(masterRow(state.masterOn))
			.addItem(brightnessRow(state.brightness))
			.addItem(browseRow("Color", null) { screenManager.push(ColorScreen(carContext)) })
			.addItem(browseRow("Pattern", patternLabel) {
				screenManager.push(PatternScreen(carContext))
			})
			.addItem(browseRow("Zone", zoneLabel) { screenManager.push(ZoneScreen(carContext)) })
			.build()

		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle(state.deviceName ?: "open-cardi")
					.setStartHeaderAction(Action.APP_ICON)
					.build()
			)
			.setSingleList(list)
			.build()
	}

	private fun masterRow(on: Boolean) = Row.Builder()
		.setTitle("Lights")
		.setToggle(
			Toggle.Builder { checked -> CardiController.setMaster(checked) }
				.setChecked(on)
				.build()
		)
		.build()

	/**
	 * No car template has a slider, so brightness is a value with −/+ actions
	 * beside it. That keeps it to a single glance and two taps rather than a
	 * drill-in and a list.
	 */
	private fun brightnessRow(pct: Int) = Row.Builder()
		.setTitle("Brightness")
		.addText("$pct%")
		.addAction(
			Action.Builder()
				.setTitle("−")
				.setOnClickListener { CardiController.setBrightness(pct - BRIGHTNESS_STEP) }
				.build()
		)
		.addAction(
			Action.Builder()
				.setTitle("+")
				.setOnClickListener { CardiController.setBrightness(pct + BRIGHTNESS_STEP) }
				.build()
		)
		.build()

	private fun browseRow(title: String, value: String?, onClick: () -> Unit) =
		Row.Builder()
			.setTitle(title)
			.apply { value?.let { addText(it) } }
			.setBrowsable(true)
			.setOnClickListener { onClick() }
			.build()

	private fun permissionTemplate(): Template =
		MessageTemplate.Builder("Grant Bluetooth access to reach your lights.")
			.setHeader(
				Header.Builder()
					.setTitle("open-cardi")
					.setStartHeaderAction(Action.APP_ICON)
					.build()
			)
			.addAction(
				Action.Builder()
					.setTitle("Grant")
					.setOnClickListener {
						// On Android Auto this dialog appears on the phone — the
						// platform gives us no way to grant it on the car screen.
						carContext.requestPermissions(
							listOf(
								Manifest.permission.BLUETOOTH_CONNECT,
								Manifest.permission.BLUETOOTH_SCAN,
							)
						) { _, _ -> invalidate() }
					}
					.build()
			)
			.build()

	private fun connectingTemplate(connection: Connection): Template =
		MessageTemplate.Builder(
			when (connection) {
				Connection.SCANNING -> "Looking for your lights…"
				Connection.CONNECTING -> "Connecting…"
				Connection.RECONNECTING -> "Reconnecting…"
				else -> "Not connected."
			}
		)
			.setHeader(
				Header.Builder()
					.setTitle("open-cardi")
					.setStartHeaderAction(Action.APP_ICON)
					.build()
			)
			.setLoading(connection != Connection.DISCONNECTED)
			.apply {
				if (connection == Connection.DISCONNECTED) {
					addAction(
						Action.Builder()
							.setTitle("Retry")
							.setOnClickListener { CardiController.connect() }
							.build()
					)
				}
			}
			.build()
}
