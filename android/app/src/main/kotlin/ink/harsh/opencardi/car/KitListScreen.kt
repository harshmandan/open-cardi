package ink.harsh.opencardi.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ink.harsh.opencardi.ble.CardiController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The kits in range, scanned while this screen is open, to pick from by name
 * and MAC like the phone app's picker. Picking one connects to it and goes
 * back.
 */
class KitListScreen(carContext: CarContext) : Screen(carContext) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	init {
		scope.launch { CardiController.state.drop(1).collect { invalidate() } }
		lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onStart(owner: LifecycleOwner) {
				if (canScan()) withBluetooth(carContext) { CardiController.startDiscovery() }
			}

			override fun onStop(owner: LifecycleOwner) {
				CardiController.stopDiscovery()
			}

			override fun onDestroy(owner: LifecycleOwner) {
				scope.cancel()
			}
		})
	}

	private fun canScan() = ContextCompat.checkSelfPermission(
		carContext, Manifest.permission.BLUETOOTH_SCAN
	) == PackageManager.PERMISSION_GRANTED

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		val state = CardiController.state.value
		val remembered = CardiController.rememberedAddress

		val kits = ItemList.Builder()
		when {
			!canScan() -> kits.addItem(
				Row.Builder()
					.setTitle("Allow Bluetooth scanning")
					.addText("Continue on your phone")
					.setOnClickListener { askOnPhone(carContext) { invalidate() } }
					.build()
			)
			state.foundKits.isEmpty() -> kits.addItem(
				Row.Builder()
					.setTitle(if (state.discovering) "Scanning…" else "No kits found")
					.apply { if (!state.discovering) addText("Is the car's power on?") }
					.build()
			)
		}
		state.foundKits.forEach { kit ->
			val current = kit.address == (state.deviceAddress ?: remembered)
			kits.addItem(
				Row.Builder()
					.setTitle(kit.name ?: "Unnamed kit")
					.addText(
						kit.address + "  ·  ${kit.rssi} dBm" +
							if (current) "  ·  this kit" else ""
					)
					.setOnClickListener {
						withBluetooth(carContext, ::invalidate) {
							CardiController.disconnect()
							CardiController.choose(kit.address)
							CardiController.connect()
							screenManager.popToRoot()
						}
					}
					.build()
			)
		}

		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Choose kit")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(kits.build())
			.build()
	}
}
