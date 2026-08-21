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
import ink.harsh.opencardi.protocol.Zones

/** K4-only zones are hidden: on a K3 they are silent on the wire. */
class ZoneScreen(carContext: CarContext) : Screen(carContext) {

	override fun onGetTemplate(): Template {
		val limit = carLimit(carContext, LIST)
		val current = CardiController.state.value.zoneId

		val list = ItemList.Builder()
		for (zone in Zones.list.filterNot { it.k4Only }.take(limit)) {
			list.addItem(
				Row.Builder()
					.setTitle(zone.label)
					.apply {
						zone.subtitle?.let { addText(it) }
						if (current == zone.id) addText("Selected")
					}
					.setOnClickListener {
						CardiController.selectZone(zone.id)
						screenManager.pop()
					}
					.build()
			)
		}

		return ListTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle("Zone")
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setSingleList(list.build())
			.build()
	}
}
