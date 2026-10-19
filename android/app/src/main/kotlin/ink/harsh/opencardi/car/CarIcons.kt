package ink.harsh.opencardi.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import ink.harsh.opencardi.R
import ink.harsh.opencardi.ble.CardiState
import ink.harsh.opencardi.ble.Connection
import ink.harsh.opencardi.ble.Rgb

private const val SWATCH_PX = 128

/**
 * Colour swatches for the grid. The car has no colour-picker widget, so we draw
 * the swatch ourselves and hand it over as an image — that is the only way to
 * show an actual colour rather than the word for it.
 */
fun swatchIcon(rgb: Rgb, checked: Boolean = false): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(rgb.r, rgb.g, rgb.b)
	}
	val r = SWATCH_PX / 2f
	canvas.drawCircle(r, r, r, paint)
	if (checked) {
		// Drawn into the swatch rather than as a Badge: the host renders badge
		// icons tiny and dark, and the tick was nearly invisible on green.
		val luminance = 0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b
		drawTick(canvas, if (luminance > 150) android.graphics.Color.rgb(20, 20, 20) else android.graphics.Color.WHITE)
	}
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}

/**
 * A dark disc with a white − or +, the same size as the swatches, so step
 * buttons line up with the brightness levels beside them.
 */
fun stepIcon(plus: Boolean): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val r = SWATCH_PX / 2f
	canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(70, 70, 70)
	})
	val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.WHITE
		style = Paint.Style.STROKE
		strokeWidth = SWATCH_PX * 0.06f
		strokeCap = Paint.Cap.ROUND
	}
	val arm = SWATCH_PX * 0.14f
	canvas.drawLine(r - arm, r, r + arm, r, glyph)
	if (plus) canvas.drawLine(r, r - arm, r, r + arm, glyph)
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}

// Every link state gets its own look, and none of them move: the car app
// quality rules forbid animated elements (SA-1), so no flashing or spinning.
// Scanning for kits counts as searching too.
fun bluetoothIcon(carContext: Context, state: CardiState): CarIcon = when {
	state.connection == Connection.CONNECTED -> resIcon(carContext, R.drawable.ic_car_fab_bt_connected)
	state.connection != Connection.DISCONNECTED || state.discovering ->
		resIcon(carContext, R.drawable.ic_car_fab_bt_searching)
	else -> resIcon(carContext, R.drawable.ic_car_fab_bt_disabled, CarColor.RED)
}

/**
 * A drawable resource as a car icon, drawn into a bitmap here. Passed by
 * resource id, the host draws from its own cache of the app's resources, and
 * after an update that added icons it showed the wrong ones (the Bluetooth
 * icon on the Colour row, a zone icon on the power button).
 */
fun resIcon(carContext: Context, res: Int, tint: CarColor = CarColor.DEFAULT): CarIcon {
	val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
	ContextCompat.getDrawable(carContext, res)!!.mutate().apply {
		setBounds(0, 0, ICON_PX, ICON_PX)
		draw(Canvas(bitmap))
	}
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).setTint(tint).build()
}

private const val ICON_PX = 96

// [scale] shrinks the tick about the centre, for discs with a ring drawn
// round the edge, where a full-size tick crowds the ring.
private fun drawTick(canvas: Canvas, colour: Int, scale: Float = 1f) {
	val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = colour
		style = Paint.Style.STROKE
		strokeWidth = SWATCH_PX * 0.08f * scale
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
	}
	fun p(x: Float) = SWATCH_PX * (0.5f + (x - 0.5f) * scale)
	val path = android.graphics.Path().apply {
		moveTo(p(0.35f), p(0.51f))
		lineTo(p(0.46f), p(0.61f))
		lineTo(p(0.65f), p(0.40f))
	}
	canvas.drawPath(path, tick)
}

/** A dark disc with a white arc as long as [level] percent, for the 10–100% tiles. */
fun levelIcon(level: Int, checked: Boolean): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val r = SWATCH_PX / 2f
	canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(70, 70, 70)
	})
	val stroke = SWATCH_PX * 0.08f
	val inset = SWATCH_PX * 0.18f
	canvas.drawArc(
		inset, inset, SWATCH_PX - inset, SWATCH_PX - inset, -90f, level * 3.6f, false,
		Paint(Paint.ANTI_ALIAS_FLAG).apply {
			// Dimmed so the tick on the current level stands out.
			color = android.graphics.Color.argb(110, 255, 255, 255)
			style = Paint.Style.STROKE
			strokeWidth = stroke
			strokeCap = Paint.Cap.ROUND
		},
	)
	if (checked) drawTick(canvas, android.graphics.Color.WHITE, scale = 0.65f)
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}

/**
 * A still picture of a pattern, for the pattern tiles: a rainbow ring for a
 * sweep, a soft glow for breathing, rings or two halves for a pulse, red,
 * green and blue slices for a cycle.
 */
fun patternIcon(index: Int, checked: Boolean): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val r = SWATCH_PX / 2f
	val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(70, 70, 70)
	})
	fun c(red: Int, green: Int, blue: Int) = android.graphics.Color.rgb(red, green, blue)
	var tick = android.graphics.Color.WHITE
	fun glow(centre: Int) {
		paint.shader = android.graphics.RadialGradient(
			r, r, r,
			intArrayOf(android.graphics.Color.WHITE, centre, android.graphics.Color.TRANSPARENT),
			floatArrayOf(0f, 0.55f, 1f),
			android.graphics.Shader.TileMode.CLAMP,
		)
		canvas.drawCircle(r, r, r, paint)
		tick = c(20, 20, 20)
	}
	fun halves(a: Int, b: Int) {
		paint.color = a
		canvas.drawArc(0f, 0f, SWATCH_PX.toFloat(), SWATCH_PX.toFloat(), 90f, 180f, true, paint)
		paint.color = b
		canvas.drawArc(0f, 0f, SWATCH_PX.toFloat(), SWATCH_PX.toFloat(), -90f, 180f, true, paint)
	}
	when (index) {
		// Sweeps: a rainbow ring.
		0, 19 -> {
			val hues = IntArray(7) { android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
			paint.shader = android.graphics.SweepGradient(r, r, hues, null)
			paint.style = Paint.Style.STROKE
			paint.strokeWidth = SWATCH_PX * 0.16f
			canvas.drawCircle(r, r, r - paint.strokeWidth / 2, paint)
		}
		// Breathing: a soft glow in its colour.
		2 -> glow(c(120, 170, 255))
		3 -> glow(c(40, 80, 255))
		4 -> glow(c(255, 220, 0))
		5 -> glow(c(0, 220, 255))
		6 -> glow(c(255, 40, 160))
		// Pulses: rings, or the two colours it alternates.
		7 -> {
			paint.color = android.graphics.Color.WHITE
			paint.style = Paint.Style.STROKE
			paint.strokeWidth = SWATCH_PX * 0.07f
			canvas.drawCircle(r, r, r * 0.25f, paint)
			canvas.drawCircle(r, r, r * 0.6f, paint)
		}
		8 -> halves(c(255, 0, 0), c(0, 220, 60))
		9 -> halves(c(255, 0, 0), c(40, 80, 255))
		10 -> halves(c(0, 220, 60), c(40, 80, 255))
		// Cycles: red, green and blue slices.
		else -> listOf(c(255, 0, 0), c(0, 220, 60), c(40, 80, 255)).forEachIndexed { i, colour ->
			paint.color = colour
			canvas.drawArc(0f, 0f, SWATCH_PX.toFloat(), SWATCH_PX.toFloat(), -90f + i * 120f, 120f, true, paint)
		}
	}
	if (checked) drawTick(canvas, tick)
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}

/** A zone tile: [res]'s glyph, white on a dark disc. */
fun zoneIcon(carContext: Context, res: Int): CarIcon {
	val bitmap = Bitmap.createBitmap(SWATCH_PX, SWATCH_PX, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(bitmap)
	val r = SWATCH_PX / 2f
	canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = android.graphics.Color.rgb(70, 70, 70)
	})
	val inset = (SWATCH_PX * 0.28f).toInt()
	ContextCompat.getDrawable(carContext, res)!!.mutate().apply {
		setTint(android.graphics.Color.WHITE)
		setBounds(inset, inset, SWATCH_PX - inset, SWATCH_PX - inset)
		draw(canvas)
	}
	return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
}
