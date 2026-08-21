package ink.harsh.opencardi.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import ink.harsh.opencardi.ble.Rgb

private const val SWATCH_PX = 128

/**
 * Colour swatches for the grid. The car has no colour-picker widget, so we draw
 * the swatch ourselves and hand it over as an image — that is the only way to
 * show an actual colour rather than the word for it.
 */
fun swatchIcon(rgb: Rgb): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(rgb.r, rgb.g, rgb.b)
	}
	val r = SWATCH_PX / 2f
	canvas.drawCircle(r, r, r, paint)
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}
