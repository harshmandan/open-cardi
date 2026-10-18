package ink.harsh.opencardi.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * A pill of the given size, centred in the node and rotated about its middle.
 *
 * Rotating the *shape* rather than the node is deliberate: a node rotated with
 * graphicsLayer also rotates its draw scope, which would mean counter-rotating
 * every coordinate used to line the backdrop up underneath. This keeps the
 * badge axis-aligned so that maths stays a plain translation.
 */
class RotatedPillShape(
	private val pillWidth: Dp,
	private val pillHeight: Dp,
	private val degrees: Float,
) : Shape {
	override fun createOutline(
		size: Size,
		layoutDirection: LayoutDirection,
		density: Density,
	): Outline {
		val w = with(density) { pillWidth.toPx() }
		val h = with(density) { pillHeight.toPx() }
		val left = (size.width - w) / 2f
		val top = (size.height - h) / 2f

		val path = Path().apply {
			addRoundRect(
				RoundRect(
					rect = Rect(left, top, left + w, top + h),
					cornerRadius = CornerRadius(h / 2f),
				)
			)
		}
		path.transform(
			Matrix().apply {
				translate(size.width / 2f, size.height / 2f)
				rotateZ(degrees)
				translate(-size.width / 2f, -size.height / 2f)
			}
		)
		return Outline.Generic(path)
	}
}
