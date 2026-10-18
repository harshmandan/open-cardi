@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ink.harsh.opencardi.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Dark, near-black, and low-chroma on purpose: the only saturated thing on
 * screen should be the colour the lights are actually set to.
 *
 * [MotionScheme.expressive] is what gives the spring-loaded feel — shapes
 * overshoot into place rather than easing. That is the whole point of the
 * expressive language, and it is one line.
 */
/** Near-black, lifted a few percent toward the active colour. */
fun tintedBackground(accent: Color): Color =
	accent.copy(alpha = 0.07f).compositeOver(Color(0xFF0B0B0D))

@Composable
fun OpenCardiTheme(accent: Color, content: @Composable () -> Unit) {
	val bg = tintedBackground(accent)
	MaterialExpressiveTheme(
		colorScheme = darkColorScheme(
			primary = accent,
			onPrimary = Color(0xFF101014),
			primaryContainer = accent,
			onPrimaryContainer = Color(0xFF101014),
			secondaryContainer = Color(0xFF26262C),
			onSecondaryContainer = Color(0xFFEDEDF2),
			surface = bg,
			onSurface = Color(0xFFEDEDF2),
			surfaceContainer = accent.copy(alpha = 0.07f).compositeOver(Color(0xFF161619)),
			surfaceContainerHigh = accent.copy(alpha = 0.08f).compositeOver(Color(0xFF1E1E23)),
			surfaceContainerHighest = Color(0xFF26262C),
			onSurfaceVariant = Color(0xFF9A9AA4),
			outline = Color(0xFF3A3A42),
			background = bg,
			onBackground = Color(0xFFEDEDF2),
			error = Color(0xFFFF6B6B),
		),
		motionScheme = MotionScheme.expressive(),
		content = content,
	)
}

/**
 * Pill by default, squaring off under the finger and settling to a soft
 * rectangle when checked — the expressive shape-change, built from the
 * ToggleButtonShapes constructor because the `ToggleButtonDefaults.shapes()`
 * factory does not resolve against material3 1.5.0-alpha26 under AGP 9.
 */
@Composable
fun pillShapes(): ToggleButtonShapes = ToggleButtonShapes(
	shape = RoundedCornerShape(50),
	pressedShape = RoundedCornerShape(14.dp),
	checkedShape = RoundedCornerShape(20.dp),
)

/** Rounded square that squishes on press — the transport-control shape. */
@Composable
fun squircleShapes(): ToggleButtonShapes = ToggleButtonShapes(
	shape = RoundedCornerShape(28.dp),
	pressedShape = RoundedCornerShape(40.dp),
	checkedShape = RoundedCornerShape(40.dp),
)

/**
 * Unchecked toggle buttons default to a transparent container, which leaves a
 * grid of them reading as loose text. These give every tile a surface, and let
 * the accent carry the selected one.
 */
@Composable
fun tileColors(): ToggleButtonColors = ToggleButtonColors(
	containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
	contentColor = MaterialTheme.colorScheme.onSurface,
	disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
	disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
	checkedContainerColor = MaterialTheme.colorScheme.primary,
	checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)
