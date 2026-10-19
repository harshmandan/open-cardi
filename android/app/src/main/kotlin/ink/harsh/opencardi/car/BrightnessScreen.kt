package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import ink.harsh.opencardi.ble.CardiController

/** Every tenth brightness level, to jump straight to one. */
class BrightnessScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone it applies to.
	private val zoneLabel: String,
) : Screen(carContext) {

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		val current = CardiController.state.value.brightness
		val items = ItemList.Builder()
		for (level in LEVELS.take(carLimit(carContext, GRID))) {
			items.addItem(
				GridItem.Builder()
					.setTitle("$level%")
					// LARGE, not ICON: the host tints icon-type images.
					.setImage(levelIcon(level, checked = level == current), GridItem.IMAGE_TYPE_LARGE)
					.setOnClickListener {
						CardiController.setBrightness(level)
						confirm(carContext, "Brightness $level%")
						invalidate()
					}
					.build()
			)
		}
		return GridTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Brightness · $zoneLabel")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(items.build())
			.build()
	}
}

private val LEVELS = (10..100 step 10).toList()
