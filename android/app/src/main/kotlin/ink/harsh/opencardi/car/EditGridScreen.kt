package ink.harsh.opencardi.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A page for changing one thing (colour, brightness, pattern), live: the
 * current choice is the tile whose subtitle says so.
 *
 * A plain grid page, not the home screen's sectioned one. Android Auto 17.8
 * rebuilds a sectioned page from blank on any change, but updates a grid
 * page's subtitles in place (tested in the DHU). The selection is text, not a
 * tick in the picture: the library treats every bitmap as equal, so the host
 * never redraws a changed one.
 */
abstract class EditGridScreen(carContext: CarContext) : Screen(carContext) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	init {
		scope.launch {
			CardiController.state
				.map(::watched)
				.distinctUntilChanged()
				.drop(1)
				// Let the tap's ripple finish first: redrawn straight away, the
				// tapped tile was replaced mid-ripple and the tap looked dead.
				.collectLatest {
					delay(RIPPLE_MS)
					invalidate()
				}
		}
		lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onDestroy(owner: LifecycleOwner) {
				scope.cancel()
			}
		})
	}

	/** The part of the state this page shows; it refreshes only when that changes. */
	protected abstract fun watched(state: CardiState): Any

	protected abstract val title: String

	protected abstract fun tiles(state: CardiState): List<GridItem>

	override fun onGetTemplate(): Template = guarded(carContext) {
		val items = ItemList.Builder()
		tiles(CardiController.state.value).take(carLimit(carContext, GRID)).forEach(items::addItem)
		GridTemplate.Builder()
			.setHeader(
				Header.Builder()
					.setTitle(title)
					.setStartHeaderAction(Action.BACK)
					.build()
			)
			.setItemSize(GridTemplate.ITEM_SIZE_SMALL)
			.setSingleList(items.build())
			.build()
	}

	protected fun tile(
		title: String,
		image: CarIcon,
		subtitle: String? = null,
		onClick: (() -> Unit)? = null,
	): GridItem =
		GridItem.Builder()
			.setTitle(title)
			// Every tile has a second line, blank unless it's the choice:
			// otherwise "Selected" added a line and the tiles below shifted.
			// A no-break space, since the host drops an empty one.
			.setText(subtitle ?: "\u00A0")
			// LARGE, not ICON: the host tints icon-type images.
			.setImage(image, GridItem.IMAGE_TYPE_LARGE)
			.apply { if (onClick != null) setOnClickListener(onClick) }
			.build()
}

internal const val RIPPLE_MS = 500L

/** Shown under the tile that is the current choice. */
const val SELECTED = "Selected"
