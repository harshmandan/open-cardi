package ink.harsh.opencardi.car

import ink.harsh.opencardi.ble.Rgb

/** The colours offered in the car, by hue, then the whites. */
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

/** The name for a colour, if it is one of [MORE_COLOURS]. */
fun colourName(rgb: Rgb?): String? =
	MORE_COLOURS.firstOrNull { it.second == rgb }?.first

// Tile titles; the full names are cut off at tile width.
private val SHORT_PATTERN_NAMES = mapOf(
	0 to "Sweep", 2 to "Breathing", 3 to "Blue", 4 to "Yellow", 5 to "Cyan", 6 to "Pink",
	7 to "Pulse", 8 to "Red/Green", 9 to "Red/Blue", 10 to "Green/Blue", 19 to "Fast sweep",
	21 to "Cycle",
)

fun shortPatternName(index: Int): String? = SHORT_PATTERN_NAMES[index]
