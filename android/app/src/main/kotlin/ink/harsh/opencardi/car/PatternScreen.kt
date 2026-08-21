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
	private val startIndex: Int = 0,
) : Screen(carContext) {

	override fun onGetTemplate(): Template {
		val limit = carLimit(carContext, LIST)
		val current = CardiController.state.value.patternIndex

		// The first page spends one slot on "Off".
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
					.setTitle("Off")
					.apply { if (current == null) addText("Selected") }
					.setOnClickListener {
						CardiController.clearPattern()
						screenManager.pop()
					}
					.build()
			)
		}

		for (pattern in page.take(take)) {
			list.addItem(
				Row.Builder()
					.setTitle(pattern.label)
					.apply { if (current == pattern.index) addText("Selected") }
					.setOnClickListener {
						CardiController.setPattern(pattern.index)
						screenManager.pop()
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
						screenManager.push(PatternScreen(carContext, startIndex + take))
					}
					.build()
			)
		}

		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Pattern")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(list.build())
			.build()
	}
}
