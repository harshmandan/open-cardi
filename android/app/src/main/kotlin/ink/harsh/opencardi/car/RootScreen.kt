package ink.harsh.opencardi.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import ink.harsh.opencardi.ble.CardiState
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.TabContents
import androidx.car.app.model.Tab
import androidx.car.app.CarToast
import ink.harsh.opencardi.R
import androidx.car.app.model.GridItem
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.GridSection
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import ink.harsh.opencardi.ble.Rgb
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.Connection
import ink.harsh.opencardi.protocol.Patterns
import ink.harsh.opencardi.protocol.Zones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import ink.harsh.opencardi.ble.linkLabel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The car's home screen: a tab per zone (Overall, Light Guide, Floor). Each
 * tab has the colour grid, brightness steps and a Pattern row that opens the
 * pattern list for that zone; power and Bluetooth are floating buttons on
 * every tab. The tabs stay up while disconnected so the Bluetooth button is always
 * reachable.
 */
class RootScreen(carContext: CarContext) : Screen(carContext) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	init {
		// Re-render whenever the kit's state moves, including changes the user
		// made on the phone or that the device reported back over notify.
		scope.launch {
			// Only for what this screen draws: a refresh scrolls the page back
			// to the top, and a scan would otherwise refresh it for every kit
			// it saw.
			CardiController.state
				.map { it.copy(foundKits = emptyList(), favorites = emptyList(), customColors = emptyList(), speed = 0) }
				.distinctUntilChanged()
				.drop(1)
				.collect { invalidate() }
		}
		// The tab bar leaves no room for a title, so say link changes out loud.
		scope.launch {
			CardiController.state
				.map { it.connection to it.deviceName }
				.distinctUntilChanged()
				.drop(1)
				.collect {
					CarToast.makeText(
						carContext, CardiController.state.value.linkLabel(), CarToast.LENGTH_SHORT
					).show()
				}
		}
		// Say what an auto-scan found, since the kit list is a screen away.
		scope.launch {
			CardiController.state
				.map { it.discovering }
				.distinctUntilChanged()
				.drop(1)
				.collect { discovering ->
					val found = CardiController.state.value.foundKits.size
					if (!discovering && CardiController.rememberedAddress == null &&
						screenManager.top === this@RootScreen
					) {
						val text = when (found) {
							0 -> "No kits found"
							1 -> "Found 1 kit. Tap Bluetooth to choose it."
							else -> "Found $found kits. Tap Bluetooth to choose."
						}
						CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()
					}
				}
		}
		lifecycle.addObserver(
			object : androidx.lifecycle.DefaultLifecycleObserver {
				// Access may have been granted from the phone app while this
				// screen was away; don't leave the driver on the permission
				// message.
				override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
					invalidate()
					scanIfNoKit()
				}

				override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
					scope.cancel()
				}
			}
		)
	}

	// With no kit chosen nothing would ever connect, so look for kits as soon
	// as the screen shows; the Bluetooth button shows the searching icon
	// meanwhile. Never connects on its own: the kit in range may be another
	// car's.
	private fun scanIfNoKit() {
		val state = CardiController.state.value
		if (CardiController.rememberedAddress != null || state.discovering ||
			state.connection != Connection.DISCONNECTED
		) return
		val canScan = ContextCompat.checkSelfPermission(
			carContext, Manifest.permission.BLUETOOTH_SCAN
		) == android.content.pm.PackageManager.PERMISSION_GRANTED
		if (canScan) withBluetooth(carContext) { CardiController.startDiscovery() }
	}

	private fun hasBluetoothPermission(): Boolean =
		ContextCompat.checkSelfPermission(
			carContext, Manifest.permission.BLUETOOTH_CONNECT
		) == android.content.pm.PackageManager.PERMISSION_GRANTED

	// The tab on screen; selecting one selects that zone on the kit.
	private var activeTab: String = CardiController.state.value.zoneId
		.takeIf { it in ZONE_TABS } ?: "overall"

	override fun onGetTemplate(): Template = guarded(carContext) { template() }

	private fun template(): Template {
		if (!hasBluetoothPermission()) return permissionTemplate()

		val contents = zoneTab(CardiController.state.value)

		return TabTemplate.Builder(object : TabTemplate.TabCallback {
			override fun onTabSelected(tabContentId: String) {
				android.util.Log.i("CardiCar", "tab selected: $tabContentId")
				activeTab = tabContentId
				if (tabContentId in ZONE_TABS) CardiController.selectZone(tabContentId)
				invalidate()
			}
		})
			.setHeaderAction(Action.APP_ICON)
			.addTab(tab("overall", "Overall", R.drawable.ic_car_zone))
			.addTab(tab("guide", "Light Guide", R.drawable.ic_car_zone_guide))
			.addTab(tab("floor", "Floor", R.drawable.ic_car_zone_floor))
			.setTabContents(TabContents.Builder(contents).build())
			.setActiveTabContentId(activeTab)
			.build()
	}

	/**
	 * A zone tab, as a sectioned template (car API 8). A tappable row heads
	 * each grid and opens a full page: every colour, 10–100% brightness, every
	 * pattern. The grids under them hold the quick picks: eight colours, the
	 * ±1/±5 steps, solid colour and three patterns.
	 */
	private fun zoneTab(state: CardiState): Template {
		val zoneLabel = Zones.byId(activeTab)?.label ?: activeTab
		val current = state.color.takeIf { state.patternIndex == null && state.micMode == null }
		val colourTitle = when {
			current == null -> "Colour"
			else -> "Colour · ${colourName(current) ?: "Custom"}"
		}
		val brightnessTitle = "Brightness · ${state.brightness}%"
		val patternTitle = "Pattern · " + (
			state.patternIndex?.let { i -> Patterns.list.firstOrNull { it.index == i }?.label }
				?: "Solid colour"
			)
		val openColours = { screenManager.push(ColorScreen(carContext, zoneLabel)) }
		val openPatterns = { screenManager.push(PatternScreen(carContext, zoneLabel)) }

		val template = SectionedItemTemplate.Builder()
		template.addSection(heading(colourTitle, browsable = true, onClick = openColours))
		template.addSection(grid(colourTiles(colourGrid(current), current, zoneLabel), circles = true))
		template.addSection(heading(brightnessTitle, browsable = true) {
			screenManager.push(BrightnessScreen(carContext, zoneLabel))
		})
		template.addSection(grid(stepTiles(state.brightness)))
		template.addSection(heading(patternTitle, browsable = true, onClick = openPatterns))
		template.addSection(grid(listOf(solidTile(state, zoneLabel)) + quickPatternTiles(state.patternIndex, zoneLabel)))
		return template
			.addAction(powerFab(state))
			.addAction(bluetoothFab(state))
			.build()
	}

	private fun grid(items: List<GridItem>, circles: Boolean = false) =
		GridSection.Builder()
			.setItemSize(GridSection.ITEM_SIZE_SMALL)
			.apply { if (circles) setItemImageShape(GridSection.ITEM_IMAGE_SHAPE_CIRCLE) }
			.setItems(items)
			.build()

	// Rows rather than section titles: they can be tapped, and a row's title
	// is larger and whiter than a section title, so all three match.
	private fun heading(title: String, browsable: Boolean, onClick: () -> Unit) =
		RowSection.Builder()
			.addItem(
				Row.Builder()
					.setTitle(title)
					.setBrowsable(browsable)
					.setOnClickListener(onClick)
					.build()
			)
			.build()

	private fun tile(title: String, image: CarIcon, onClick: () -> Unit) =
		GridItem.Builder()
			// A title is required in practice: the library allows an untitled
			// grid item, but Android Auto 17.8 crashed on one ("A title must be
			// set on the row").
			.setTitle(title)
			// LARGE, not ICON: the host tints icon-type images, which turned
			// every swatch white.
			.setImage(image, GridItem.IMAGE_TYPE_LARGE)
			.setOnClickListener(onClick)
			.build()

	// The current colour has a tick drawn into it.
	private fun colourTiles(palette: List<Pair<String, Rgb>>, current: Rgb?, zoneLabel: String) =
		palette.map { (name, rgb) ->
			tile(name, swatchIcon(rgb, checked = rgb == current)) {
				CardiController.setColor(rgb)
				confirm(carContext, "$name on $zoneLabel")
			}
		}

	// Steps rather than presets: −5 −1 +1 +5 fit one row on a 1280×720 split
	// screen (four columns) and reach any exact value. Not as row actions:
	// this host draws only one action per row (the docs say two from car API
	// 8; tested).
	private fun stepTiles(b: Int) = BRIGHTNESS_STEPS.map { step ->
		tile(if (step > 0) "+$step" else "−${-step}", stepIcon(plus = step > 0)) {
			val next = (b + step).coerceIn(1, 100)
			CardiController.setBrightness(next)
			confirm(carContext, "Brightness $next%")
		}
	}

	// Eight: six fixed, a slot, White. The slot always shows the
	// current colour when it isn't one of the fixed seven, so what's lit is
	// always on screen and ticked; it keeps that colour until another
	// off-grid one replaces it.
	private fun colourGrid(current: Rgb?): List<Pair<String, Rgb>> {
		val fixed = CAR_PALETTE.dropLast(1)
		val white = CAR_PALETTE.last()
		if (current != null && (fixed + white).none { it.second == current }) {
			Recent.colour = (colourName(current) ?: "Custom") to current
		}
		return fixed + (Recent.colour ?: COLOUR_SLOT_DEFAULT) + white
	}

	// Two fixed patterns, then a slot like the colours': Cycle until a
	// pattern from the list is chosen, then that one. Strobes are never
	// offered in the car (see PatternScreen).
	private fun quickPatternTiles(active: Int?, zoneLabel: String): List<GridItem> {
		if (active != null && active !in FIXED_PATTERNS) Recent.pattern = active
		return (FIXED_PATTERNS + (Recent.pattern ?: PATTERN_SLOT_DEFAULT)).map { index ->
			val label = Patterns.list.first { it.index == index }.label
			tile(SHORT_PATTERN_NAMES[index] ?: label, patternIcon(index, checked = index == active)) {
				CardiController.setPattern(index)
				confirm(carContext, "$label on $zoneLabel")
			}
		}
	}

	// Back to a steady colour. Always a plain white disc, whatever the colour.
	private fun solidTile(state: CardiState, zoneLabel: String) =
		tile("Solid", swatchIcon(Rgb(255, 255, 255), checked = state.patternIndex == null)) {
			CardiController.clearPattern()
			confirm(carContext, "Solid colour on $zoneLabel")
		}

	// Floating buttons. Tested in the DHU (host car API 8): drawn on list and
	// sectioned templates, including inside tabs, but never on a GridTemplate,
	// which is why the zone tabs are sectioned rather than a plain grid. The
	// host restyles them, so the background colour is only what the API
	// requires, not what shows.
	private fun powerFab(state: CardiState) =
		Action.Builder()
			.setIcon(icon(R.drawable.ic_car_power))
			.setBackgroundColor(if (state.masterOn) CarColor.GREEN else CarColor.RED)
			.setOnClickListener {
				CardiController.setMaster(!state.masterOn)
				confirm(carContext, if (state.masterOn) "Lights turned off" else "Lights turned on")
			}
			.build()

	private fun bluetoothFab(state: CardiState) =
		Action.Builder()
			.setIcon(bluetoothIcon(carContext, state))
			.setBackgroundColor(CarColor.BLUE)
			.setOnClickListener { screenManager.push(DeviceScreen(carContext)) }
			.build()

	private fun tab(id: String, title: String, iconRes: Int) =
		Tab.Builder().setContentId(id).setTitle(title).setIcon(icon(iconRes)).build()

	private fun icon(res: Int) = carIcon(carContext, res)

	// Google's "grant permissions on phone" flow: a message template with a
	// primary and a secondary button, a toast pointing at the phone, then
	// back to the landing screen once granted (the callback invalidates, and
	// onGetTemplate sees the permission).
	private fun permissionTemplate(): Template =
		MessageTemplate.Builder("open-cardi needs Bluetooth permission to reach your lights")
			.setIcon(CarIcon.ALERT)
			.setHeader(
				Header.Builder()
					.setTitle("open-cardi")
					.setStartHeaderAction(Action.APP_ICON)
					.build()
			)
			.addAction(
				Action.Builder()
					.setTitle("Grant permissions")
					.setFlags(Action.FLAG_PRIMARY)
					.setOnClickListener { askOnPhone(carContext) { invalidate() } }
					.build()
			)
			.addAction(
				Action.Builder()
					.setTitle("Cancel")
					.setOnClickListener { carContext.finishCarApp() }
					.build()
			)
			.build()
}

private val ZONE_TABS = setOf("overall", "guide", "floor")
private val BRIGHTNESS_STEPS = listOf(-5, -1, 1, 5)

// Pattern indexes. The fixed two and the slot's default are guesses at
// favourites; we don't track which ones are used.
private val FIXED_PATTERNS = listOf(0, 2)
private const val PATTERN_SLOT_DEFAULT = 21
// Tile titles; the full names are cut off at tile width.
private val SHORT_PATTERN_NAMES = mapOf(
	0 to "Sweep", 2 to "Breathing", 3 to "Blue", 4 to "Yellow", 5 to "Cyan", 6 to "Pink",
	7 to "Pulse", 8 to "Red/Green", 9 to "Red/Blue", 10 to "Green/Blue", 19 to "Fast sweep",
	21 to "Cycle",
)

// What the grids' slots show, for as long as the app runs.
private object Recent {
	var colour: Pair<String, Rgb>? = null
	var pattern: Int? = null
}
