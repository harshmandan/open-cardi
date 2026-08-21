package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager

const val GRID = ConstraintManager.CONTENT_LIMIT_TYPE_GRID
const val LIST = ConstraintManager.CONTENT_LIMIT_TYPE_LIST

/**
 * How many items this particular head unit will render. Defaults are six for
 * both grid and list, but a car may allow more — never hardcode it, or the host
 * silently truncates and the user never sees the tail.
 */
fun carLimit(carContext: CarContext, type: Int): Int =
	runCatching {
		carContext.getCarService(ConstraintManager::class.java).getContentLimit(type)
	}.getOrDefault(6).coerceAtLeast(2)
