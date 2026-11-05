package app.olauncher.ui.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.AppCommand

internal open class SearchCommandChipSpan(context: Context, code: String, icon: Int) :
    ImageSpan(chip(context, code, icon), ALIGN_BOTTOM) {
    constructor(context: Context, letter: Char, icon: Int) : this(context, letter.toString(), icon)
}

internal class AppCommandChipSpan(context: Context, command: AppCommand, icon: Int) :
    SearchCommandChipSpan(context, command.code, icon)

internal class EngineEditChipSpan(context: Context, icon: Int) :
    SearchCommandChipSpan(context, 'e', icon)

private fun chip(context: Context, code: String, icon: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = ((if (code.length > 1) 64 else 52) * density).toInt().coerceAtLeast(1)
    val height = (26 * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.search_surface_pressed)
    }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 6 * density, 6 * density, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = density
    paint.color = ContextCompat.getColor(context, R.color.search_outline)
    canvas.drawRoundRect(density / 2, density / 2, width - density / 2, height - density / 2,
        6 * density, 6 * density, paint)
    ContextCompat.getDrawable(context, icon)?.mutate()?.apply {
        setTint(Color.WHITE)
        setBounds((7 * density).toInt(), (5 * density).toInt(), (23 * density).toInt(), (21 * density).toInt())
        draw(canvas)
    }
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    val textX = if (code.length > 1) 29 * density else 31 * density
    val textSize = if (code.length > 1) 10 else 12
    paint.textSize = textSize * context.resources.displayMetrics.scaledDensity
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText(code.uppercase(), textX,
        height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap).apply { setBounds(0, 0, width, height) }
}