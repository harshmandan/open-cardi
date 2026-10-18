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

/** Six swatches, drawn as images because the car has no colour picker. */
class ColorScreen(carContext: CarContext) : Screen(carContext) {

	override fun onGetTemplate(): Template {
		val items = ItemList.Builder()
		for ((name, rgb) in CAR_PALETTE.take(gridLimit())) {
			items.addItem(
				GridItem.Builder()
					.setTitle(name)
					.setImage(swatchIcon(rgb))
					.setOnClickListener {
						CardiController.setColor(rgb)
						screenManager.pop()
					}
					.build()
			)
		}

		return GridTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Color")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(items.build())
			.build()
	}

	private fun gridLimit(): Int = carLimit(carContext, GRID)
}
