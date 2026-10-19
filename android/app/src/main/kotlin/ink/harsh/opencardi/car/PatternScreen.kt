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
import ink.harsh.opencardi.ble.Rgb
import ink.harsh.opencardi.protocol.Patterns

/**
 * Patterns, paged to whatever this head unit will render.
 *
 * Only [Patterns.calm] is offered — the eleven strobe patterns are deliberately
 * absent. A flashing cabin at speed is a safety problem and a car-app review
 * rejection; they stay available on the phone and in the PWA.
 */
class PatternScreen(
	carContext: CarContext,
	// Named in the header so it's clear which zone the pattern goes to.
	private val zoneLabel: String,
	private val startIndex: Int = 0,
) : Screen(carContext) {

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		val limit = carLimit(carContext, LIST)
		val current = CardiController.state.value.patternIndex

		// The first page spends one slot on "Solid colour".
		val offSlot = if (startIndex == 0) 1 else 0
		val page = Patterns.calm.drop(startIndex)
		val slots = limit - offSlot
		val needsMore = page.size > slots
		// When there is a tail, the last slot becomes the "More…" row.
		val take = if (needsMore) slots - 1 else slots

		val list = ItemList.Builder()

		if (offSlot == 1) {
			list.addItem(
				Row.Builder()
					.setTitle("Solid colour")
					// The tick goes in the picture: this host ignores Row.setEndImage.
					.setImage(swatchIcon(Rgb(255, 255, 255), checked = current == null), Row.IMAGE_TYPE_SMALL)
					.setOnClickListener {
						CardiController.clearPattern()
						confirm(carContext, "Solid colour on $zoneLabel")
						invalidate()
					}
					.build()
			)
		}

		for (pattern in page.take(take)) {
			list.addItem(
				Row.Builder()
					.setTitle(pattern.label)
					.setImage(patternIcon(pattern.index, checked = current == pattern.index), Row.IMAGE_TYPE_SMALL)
					.setOnClickListener {
						CardiController.setPattern(pattern.index)
						confirm(carContext, "${pattern.label} on $zoneLabel")
						invalidate()
					}
					.build()
			)
		}

		if (needsMore) {
			val rest = page.size - take
			list.addItem(
				Row.Builder()
					.setTitle("More…")
					.addText("$rest more")
					.setBrowsable(true)
					.setOnClickListener {
						screenManager.push(PatternScreen(carContext, zoneLabel, startIndex + take))
					}
					.build()
			)
		}

		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Pattern · $zoneLabel")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(list.build())
			.build()
	}
}
