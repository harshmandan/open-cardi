package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.model.GridItem
import ink.harsh.opencardi.R
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState

/** The zone colour, brightness and pattern apply to; the selected one says so. */
class ZoneScreen(carContext: CarContext) : EditGridScreen(carContext) {

	override val title get() = "Zone"

	override fun watched(state: CardiState) = state.zoneId

	override fun tiles(state: CardiState): List<GridItem> = ZONES.map { (id, label, iconRes) ->
		tile(label, zoneIcon(carContext, iconRes), SELECTED.takeIf { id == state.zoneId }) {
			CardiController.selectZone(id)
		}
	}
}

private val ZONES = listOf(
	Triple("overall", "Overall", R.drawable.ic_car_zone),
	Triple("guide", "Light Guide", R.drawable.ic_car_zone_guide),
	Triple("floor", "Floor", R.drawable.ic_car_zone_floor),
)
