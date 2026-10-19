package ink.harsh.opencardi.car

import ink.harsh.opencardi.ble.Rgb

/**
 * The fixed colour tiles on each zone tab; a slot before White makes eight,
 * two rows of four on a 720p split screen. Picked to be told apart at a
 * glance on a dim head unit.
 */
val CAR_PALETTE: List<Pair<String, Rgb>> = listOf(
	"Red" to Rgb(255, 0, 0),
	"Amber" to Rgb(255, 130, 0),
	"Green" to Rgb(0, 255, 60),
	"Cyan" to Rgb(0, 220, 255),
	"Blue" to Rgb(40, 80, 255),
	"Purple" to Rgb(150, 0, 255),
	"White" to Rgb(255, 255, 255),
)

/** What the colour slot shows until a colour off the grid is chosen. */
val COLOUR_SLOT_DEFAULT: Pair<String, Rgb> = "Pink" to Rgb(255, 40, 160)

/** Finer shades for the More colours list, by hue, then the whites. */
val MORE_COLOURS: List<Pair<String, Rgb>> = listOf(
	"Red" to Rgb(255, 0, 0),
	"Coral" to Rgb(255, 60, 30),
	"Orange" to Rgb(255, 90, 0),
	"Amber" to Rgb(255, 130, 0),
	"Gold" to Rgb(255, 180, 0),
	"Yellow" to Rgb(255, 230, 0),
	"Lime" to Rgb(150, 255, 0),
	"Green" to Rgb(0, 255, 60),
	"Mint" to Rgb(0, 255, 140),
	"Turquoise" to Rgb(0, 255, 200),
	"Cyan" to Rgb(0, 220, 255),
	"Sky blue" to Rgb(0, 150, 255),
	"Blue" to Rgb(40, 80, 255),
	"Indigo" to Rgb(80, 0, 255),
	"Purple" to Rgb(150, 0, 255),
	"Violet" to Rgb(200, 0, 255),
	"Magenta" to Rgb(255, 0, 255),
	"Pink" to Rgb(255, 40, 160),
	"Rose" to Rgb(255, 0, 90),
	"Warm white" to Rgb(255, 170, 90),
	"White" to Rgb(255, 255, 255),
	"Ice white" to Rgb(170, 210, 255),
)

/** The name for a colour, from either list. */
fun colourName(rgb: Rgb?): String? =
	(CAR_PALETTE + MORE_COLOURS).firstOrNull { it.second == rgb }?.first

/** Brightness moves in steps; there is no slider in any car template. */
val BRIGHTNESS_STEP = 10

/** Dimmest the car's Dimmer tile goes; 0 would read as "off", which is the power tile's job. */
const val BRIGHTNESS_FLOOR = 10
