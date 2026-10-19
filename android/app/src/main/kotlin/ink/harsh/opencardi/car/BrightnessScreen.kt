package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.model.GridItem
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState

/** Every tenth brightness level, to jump straight to one; the ±1/±5 steps are on home. */
class BrightnessScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone it applies to.
	private val zoneLabel: String,
) : EditGridScreen(carContext) {

	override val title get() = "Brightness · $zoneLabel"

	override fun watched(state: CardiState) = state.brightness

	override fun tiles(state: CardiState): List<GridItem> = (10..100 step 10).map { level ->
		tile("$level%", levelIcon(level, checked = false), SELECTED.takeIf { level == state.brightness }) {
			CardiController.setBrightness(level)
		}
	}
}
