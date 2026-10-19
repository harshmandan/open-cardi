package ink.harsh.opencardi.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ink.harsh.opencardi.R
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.Connection
import ink.harsh.opencardi.ble.linkLabel
import ink.harsh.opencardi.protocol.Patterns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Opened from the Bluetooth button: the link and the lights at a glance, with
 * the current colour as the picture, and up to two buttons — connect or
 * disconnect, and Choose kit.
 *
 * A pane, not a list: it reads better, but its rows can't be tapped
 * (RowConstraints.ROW_CONSTRAINTS_PANE) and it takes at most two actions, so
 * the kits in range live on their own screen, [KitListScreen].
 */
class DeviceScreen(carContext: CarContext) : Screen(carContext) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	init {
		scope.launch { CardiController.state.drop(1).collect { invalidate() } }
		lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onDestroy(owner: LifecycleOwner) {
				scope.cancel()
			}
		})
	}

	private fun granted(permission: String) =
		ContextCompat.checkSelfPermission(carContext, permission) == PackageManager.PERMISSION_GRANTED

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		val state = CardiController.state.value
		val remembered = CardiController.rememberedAddress
		val canUseBluetooth = granted(Manifest.permission.BLUETOOTH_CONNECT) &&
			granted(Manifest.permission.BLUETOOTH_SCAN)

		val pane = Pane.Builder()
		pane.addRow(
			Row.Builder()
				.setTitle(state.linkLabel())
				.addText(state.deviceAddress ?: remembered ?: "No kit chosen yet")
				.setImage(bluetoothIcon(carContext, state))
				.build()
		)
		if (state.isConnected) {
			val look = state.patternIndex
				?.let { i -> Patterns.list.firstOrNull { it.index == i }?.label }
				?: CAR_PALETTE.firstOrNull { it.second == state.color }?.first
				?: "Custom colour"
			pane.addRow(
				Row.Builder()
					.setTitle(if (state.masterOn) "Lights on" else "Lights off")
					.addText("$look · ${state.brightness}%")
					.setImage(carIcon(carContext, R.drawable.ic_car_power))
					.build()
			)
			pane.setImage(swatchIcon(state.color))
		}
		if (!canUseBluetooth) {
			pane.addRow(
				Row.Builder()
					.setTitle("Bluetooth access needed")
					.addText("Continue on your phone")
					.setImage(carIcon(carContext, R.drawable.ic_car_bt_disabled))
					.build()
			)
		}

		actions(state.connection, remembered != null, canUseBluetooth).forEach(pane::addAction)

		return PaneTemplate.Builder(pane.build())
			.setHeader(
				Header.Builder()
					.setTitle("Lights")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.build()
	}

	// At most two (ActionsConstraints.ACTIONS_CONSTRAINTS_BODY). The first is
	// the one the driver most likely wants, so it is the primary button.
	private fun actions(connection: Connection, hasKit: Boolean, canUseBluetooth: Boolean): List<Action> {
		if (!canUseBluetooth) {
			return listOf(
				button("Allow", primary = true) { askOnPhone(carContext) { invalidate() } }
			)
		}
		val chooseKit = button("Choose kit", primary = !hasKit) {
			screenManager.push(KitListScreen(carContext))
		}
		val first = when {
			connection == Connection.CONNECTED -> button("Disconnect") {
				withBluetooth(carContext, ::invalidate) { CardiController.disconnect() }
			}
			connection != Connection.DISCONNECTED -> button("Stop") {
				withBluetooth(carContext, ::invalidate) { CardiController.disconnect() }
			}
			hasKit -> button("Connect", primary = true) {
				withBluetooth(carContext, ::invalidate) { CardiController.connect() }
			}
			else -> null
		}
		return listOfNotNull(first, chooseKit)
	}

	private fun button(title: String, primary: Boolean = false, onClick: () -> Unit) =
		Action.Builder()
			.setTitle(title)
			.apply { if (primary) setFlags(Action.FLAG_PRIMARY) }
			.setOnClickListener(onClick)
			.build()
}
