package ink.harsh.opencardi.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ink.harsh.opencardi.ble.CardiService

/**
 * Entry point for Android Auto. The app code runs on the phone — the head unit
 * is a display and a touch surface — so the BLE link lives in [CardiService]
 * and this is only a view onto it.
 */
class CardiCarAppService : CarAppService() {

	override fun createHostValidator(): HostValidator {
		val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
		return if (debuggable) {
			// Lets the Desktop Head Unit and unsigned test hosts attach.
			HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
		} else {
			// Despite the name, this is the library's list of Google host
			// signatures — the only hosts a projected app should ever talk to.
			HostValidator.Builder(applicationContext)
				.addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
				.build()
		}
	}

	override fun onCreateSession(): Session = CardiSession()
}

class CardiSession : Session() {

	override fun onCreateScreen(intent: Intent): Screen {
		// The kit takes one connection at a time, so we only hold the radio
		// while the car is attached. Off-car the link is free for the PWA.
		CardiService.start(carContext)

		lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onDestroy(owner: LifecycleOwner) {
				CardiService.stop(carContext)
			}
		})

		return RootScreen(carContext)
	}
}
