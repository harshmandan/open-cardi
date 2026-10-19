package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import ink.harsh.opencardi.ble.CardiController

/** More colours: [MORE_COLOURS] as a list, each with its swatch. */
class ColorScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone the colour goes to.
	private val zoneLabel: String,
) : Screen(carContext) {

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		val state = CardiController.state.value
		val current = state.color.takeIf { state.patternIndex == null && state.micMode == null }
		val items = ItemList.Builder()
		for ((name, rgb) in MORE_COLOURS.take(carLimit(carContext, LIST))) {
			items.addItem(
				Row.Builder()
					.setTitle(name)
					// SMALL, not ICON: the host tints icon-type images.
					.setImage(swatchIcon(rgb, checked = rgb == current), Row.IMAGE_TYPE_SMALL)
					.setOnClickListener {
						CardiController.setColor(rgb)
						confirm(carContext, "$name on $zoneLabel")
						invalidate()
					}
					.build()
			)
		}
		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Colour · $zoneLabel")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(items.build())
			.build()
	}
}
