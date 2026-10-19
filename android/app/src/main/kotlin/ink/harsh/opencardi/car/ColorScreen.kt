package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.model.GridItem
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState

/** Every colour in [MORE_COLOURS]; the lit one says so. */
class ColorScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone the colour goes to.
	private val zoneLabel: String,
) : EditGridScreen(carContext) {

	override val title get() = "Colour · $zoneLabel"

	override fun watched(state: CardiState) = current(state) ?: Unit

	private fun current(state: CardiState) =
		state.color.takeIf { state.patternIndex == null && state.micMode == null }

	override fun tiles(state: CardiState): List<GridItem> {
		val current = current(state)
		return MORE_COLOURS.map { (name, rgb) ->
			tile(name, swatchIcon(rgb), SELECTED.takeIf { rgb == current }) {
				CardiController.setColor(rgb)
			}
		}
	}
}
