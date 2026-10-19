package ink.harsh.opencardi.car

import android.Manifest
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Builds a screen's template, and shows a message instead if that throws. An
 * exception here otherwise kills the app and drops the driver out of it.
 */
fun guarded(carContext: CarContext, build: () -> Template): Template = try {
	build()
} catch (e: RuntimeException) {
	Log.e("CardiCar", "template failed", e)
	MessageTemplate.Builder("Something went wrong. Go back and try again, or use the phone app.")
		.setHeader(
			Header.Builder()
				.setTitle("open-cardi")
				.setStartHeaderAction(Action.BACK)
				.build()
		)
		.build()
}

/**
 * Asks for Bluetooth access. Android shows the dialog on the phone, never on
 * the car screen, so say so.
 */
fun askOnPhone(carContext: CarContext, done: () -> Unit) {
	CarToast.makeText(carContext, "Continue on your phone", CarToast.LENGTH_LONG).show()
	carContext.requestPermissions(
		listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
	) { _, _ -> done() }
}

/** Runs a Bluetooth action; if Android refuses it for want of permission, asks on the phone. */
fun withBluetooth(carContext: CarContext, done: () -> Unit = {}, action: () -> Unit) {
	try {
		action()
	} catch (e: SecurityException) {
		Log.w("CardiCar", "bluetooth permission missing", e)
		askOnPhone(carContext, done)
	}
}

/**
 * Confirms a tap with a toast, as Google's IoT sample does. The writes are
 * fire-and-forget, so with no link there's nothing to confirm: say that
 * instead.
 */
fun confirm(carContext: CarContext, message: String) {
	val text = if (ink.harsh.opencardi.ble.CardiController.state.value.isConnected) message
	else "Not connected to your lights"
	CarToast.makeText(carContext, text, CarToast.LENGTH_SHORT).show()
}
