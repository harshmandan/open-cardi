package ink.harsh.opencardi.car

import ink.harsh.opencardi.ble.Rgb

/**
 * Exactly six, because the host caps a grid at six items by default
 * (`content_limit_grid`). Picked to be distinguishable at a glance on a dim
 * head unit rather than to cover the colour space.
 */
val CAR_PALETTE: List<Pair<String, Rgb>> = listOf(
	"Red" to Rgb(255, 0, 0),
	"Amber" to Rgb(255, 130, 0),
	"Green" to Rgb(0, 255, 60),
	"Cyan" to Rgb(0, 220, 255),
	"Blue" to Rgb(40, 80, 255),
	"White" to Rgb(255, 255, 255),
)

/** Brightness moves in steps; there is no slider in any car template. */
val BRIGHTNESS_STEP = 25
