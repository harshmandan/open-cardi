package ink.harsh.opencardi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Hue runs around the rim; saturation runs from the white centre outward. */
private val HUE_RING = listOf(
	Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
	Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
	Color(0xFFFF0000),
)

/**
 * A hue/saturation wheel. Two sliders made you solve for a colour one axis at a
 * time; the wheel shows the whole space at once and you point at what you want.
 * Brightness stays a separate control because it is not part of this plane.
 */
@Composable
fun ColorWheel(
	hue: Float,
	saturation: Float,
	onChange: (hue: Float, saturation: Float) -> Unit,
	modifier: Modifier = Modifier,
	diameter: androidx.compose.ui.unit.Dp = 240.dp,
) {
	fun resolve(position: Offset, widthPx: Float, heightPx: Float) {
		val cx = widthPx / 2f
		val cy = heightPx / 2f
		val dx = position.x - cx
		val dy = position.y - cy
		val radius = minOf(cx, cy)
		// atan2 gives -180..180 counter-clockwise; the sweep gradient starts at
		// 3 o'clock and runs clockwise, so this maps straight onto it.
		val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
		val distance = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
		onChange(degrees, distance)
	}

	Canvas(
		modifier
			.size(diameter)
			.pointerInput(Unit) {
				detectTapGestures { resolve(it, size.width.toFloat(), size.height.toFloat()) }
			}
			.pointerInput(Unit) {
				detectDragGestures(
					onDragStart = { resolve(it, size.width.toFloat(), size.height.toFloat()) },
				) { change, _ ->
					resolve(change.position, size.width.toFloat(), size.height.toFloat())
				}
			}
	) {
		val radius = size.minDimension / 2f
		drawCircle(brush = Brush.sweepGradient(HUE_RING, center = center), radius = radius)
		// White in the middle fading out is what turns the hue ring into a
		// saturation plane.
		drawCircle(
			brush = Brush.radialGradient(
				colors = listOf(Color.White, Color.Transparent),
				center = center,
				radius = radius,
			),
			radius = radius,
		)

		val angle = Math.toRadians(hue.toDouble())
		val marker = Offset(
			x = center.x + (cos(angle) * saturation * radius).toFloat(),
			y = center.y + (sin(angle) * saturation * radius).toFloat(),
		)
		drawCircle(Color.White, radius = 11.dp.toPx(), center = marker, style = Stroke(3.dp.toPx()))
		drawCircle(Color.Black.copy(alpha = 0.6f), radius = 14.dp.toPx(), center = marker, style = Stroke(1.dp.toPx()))
	}
}
