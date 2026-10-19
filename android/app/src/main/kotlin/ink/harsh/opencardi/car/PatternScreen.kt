package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.model.GridItem
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState
import ink.harsh.opencardi.ble.Rgb
import ink.harsh.opencardi.protocol.Patterns

/**
 * Solid colour and every calm pattern; the running one says so.
 *
 * Only [Patterns.calm] is offered — the eleven strobe patterns are deliberately
 * absent. A flashing cabin at speed is a safety problem and a car-app review
 * rejection; they stay available on the phone and in the PWA.
 */
class PatternScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone the pattern goes to.
	private val zoneLabel: String,
) : EditGridScreen(carContext) {

	override val title get() = "Pattern · $zoneLabel"

	override fun watched(state: CardiState) = state.patternIndex ?: -1

	override fun tiles(state: CardiState): List<GridItem> {
		val active = state.patternIndex
		val solid = tile("Solid", swatchIcon(Rgb(255, 255, 255)), SELECTED.takeIf { active == null }) {
			CardiController.clearPattern()
		}
		return listOf(solid) + Patterns.calm.map { p ->
			tile(shortPatternName(p.index) ?: p.label, patternIcon(p.index, checked = false),
				SELECTED.takeIf { active == p.index }) {
				CardiController.setPattern(p.index)
			}
		}
	}
}
