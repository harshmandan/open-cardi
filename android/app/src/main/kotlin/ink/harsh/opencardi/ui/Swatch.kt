@file:OptIn(
	androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
	androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package ink.harsh.opencardi.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import ink.harsh.opencardi.ble.Rgb

private val SWATCH_SIZE = 64.dp
private val BADGE_BOX = 34.dp

/**
 * Shapes the active swatch can morph into. Every swatch rests as a circle, so
 * the odd shape *is* the selection indicator — and it is redrawn from this list
 * on each pick, so selecting never looks the same twice.
 */
val SELECTABLE_SHAPES: List<RoundedPolygon> = listOf(
	MaterialShapes.Sunny,
	MaterialShapes.Cookie7Sided,
	MaterialShapes.Clover4Leaf,
	MaterialShapes.Pentagon,
	MaterialShapes.Cookie9Sided,
	MaterialShapes.Gem,
	MaterialShapes.VerySunny,
	MaterialShapes.Cookie6Sided,
)

/**
 * Morph between two polygons at [progress]. Scaling here rather than at the
 * call site is what keeps the shape growing from its own centre instead of
 * sliding out of one corner.
 */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
	override fun createOutline(
		size: Size,
		layoutDirection: LayoutDirection,
		density: Density,
	): Outline {
		val path = morph.toPath(progress)
		path.transform(Matrix().apply { scale(size.width, size.height) })
		return Outline.Generic(path)
	}
}

@Composable
fun Swatch(
	rgb: Rgb,
	selected: Boolean,
	pinned: Boolean,
	brightnessLabel: Int? = null,
	selectedShape: RoundedPolygon,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// Rebuilding the morph on every shape change is what lets the *next*
	// selection animate into a different shape than the last one.
	val morph = remember(selectedShape) { Morph(MaterialShapes.Circle, selectedShape) }
	val progress by animateFloatAsState(
		targetValue = if (selected) 1f else 0f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioMediumBouncy,
			stiffness = Spring.StiffnessLow,
		),
		label = "swatchMorph",
	)

	// A half turn per selection, accumulated rather than toggled, so picking the
	// same swatch twice spins onward instead of unwinding. Duration is governed
	// by the spring's stiffness and damping, not by how far it travels.
	var turns by remember { mutableIntStateOf(0) }
	LaunchedEffect(selected, selectedShape) { if (selected) turns++ }
	val rotation by animateFloatAsState(
		targetValue = turns * 180f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioLowBouncy,
			stiffness = Spring.StiffnessVeryLow,
		),
		label = "swatchSpin",
	)

	val badgeScale by animateFloatAsState(
		targetValue = if (pinned) 1f else 0f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioMediumBouncy,
			stiffness = Spring.StiffnessMediumLow,
		),
		label = "badgeScale",
	)

	Box(modifier = modifier.size(SWATCH_SIZE)) {
		Box(
			Modifier
				.matchParentSize()
				.graphicsLayer { rotationZ = rotation }
				.clip(MorphShape(morph, progress))
				.background(Color(rgb.r, rgb.g, rgb.b))
				.combinedClickable(onClick = onClick, onLongClick = onLongClick)
		)

		if (brightnessLabel != null) {
			// A scalloped disc rather than a plain chip, so the number reads as
			// part of the swatch's own language.
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.align(Alignment.Center)
					.size(36.dp)
					.clip(MaterialShapes.Cookie12Sided.toShape())
					.background(Color.Black.copy(alpha = 0.5f)),
			) {
				Text(
					"$brightnessLabel",
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					color = Color.White,
				)
			}
		}

		if (badgeScale > 0.01f) {
			// A pill at 45 degrees, filled with the swatch's inverse so it
			// always separates from whatever colour it sits on.
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.offset(x = 4.dp, y = (-4).dp)
					.size(BADGE_BOX)
					.graphicsLayer {
						scaleX = badgeScale
						scaleY = badgeScale
					}
					.clip(RotatedPillShape(28.dp, 20.dp, -45f))
					.background(Color(255 - rgb.r, 255 - rgb.g, 255 - rgb.b)),
			) {
				Icon(
					Icons.Filled.Star,
					contentDescription = "Pinned",
					// The swatch's own colour on its darkened inverse: the star
					// reads as belonging to the swatch rather than stamped on.
					tint = Color(rgb.r, rgb.g, rgb.b),
					modifier = Modifier.size(14.dp),
				)
			}
		}
	}
}
