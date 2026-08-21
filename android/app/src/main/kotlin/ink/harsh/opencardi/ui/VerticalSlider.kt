package ink.harsh.opencardi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose has no vertical Slider, so this rotates a real one a quarter turn and
 * swaps its measurement axes. It stays the Material slider — thumb behaviour,
 * the thumb-track gap, the press animation and accessibility all come along —
 * rather than a hand-drawn track that would have none of it.
 */
@Composable
fun VerticalSlider(
	value: Float,
	onValueChange: (Float) -> Unit,
	valueRange: ClosedFloatingPointRange<Float>,
	trackWidth: Dp,
	trackHeight: Dp,
	accent: Color,
	modifier: Modifier = Modifier,
	steps: Int = 0,
) {
	Box(modifier.size(width = trackWidth, height = trackHeight)) {
		Slider(
			value = value,
			onValueChange = onValueChange,
			valueRange = valueRange,
			steps = steps,
			colors = SliderDefaults.colors(
				thumbColor = Color.White,
				activeTrackColor = Color.White,
				inactiveTrackColor = accent.copy(alpha = 0.22f),
			),
			track = { sliderState ->
				// A fat track: still Material's own drawing, just thicker and
				// with matching corner rounding.
				SliderDefaults.Track(
					sliderState = sliderState,
					modifier = Modifier.height(trackWidth),
					trackCornerSize = trackWidth / 2,
				)
			},
			modifier = Modifier
				.graphicsLayer {
					rotationZ = 270f
					transformOrigin = TransformOrigin(0f, 0f)
				}
				.layout { measurable, constraints ->
					// Measure along the long axis, then report the swapped size.
					val placeable = measurable.measure(
						Constraints(
							minWidth = constraints.minHeight,
							maxWidth = constraints.maxHeight,
							minHeight = constraints.minWidth,
							maxHeight = constraints.maxWidth,
						)
					)
					layout(placeable.height, placeable.width) {
						placeable.place(-placeable.width, 0)
					}
				},
		)
	}
}
