package app.olauncher.ui.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import app.olauncher.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class AppSearchColorWheel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val hsv = floatArrayOf(0f, 0f, 1f)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var radius = 0f
    private val markerRadius = 7 * resources.displayMetrics.density
    var onColorChanged: ((Int) -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(R.string.app_search_color_wheel)
    }

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        radius = (min(w, h) / 2f - markerRadius - 2).coerceAtLeast(0f)
        val colors = IntArray(7) { index -> Color.HSVToColor(floatArrayOf(index * 60f, 1f, 1f)) }
        huePaint.shader = SweepGradient(w / 2f, h / 2f, colors, null)
        if (radius > 0) saturationPaint.shader = RadialGradient(
            w / 2f, h / 2f, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        canvas.drawCircle(x, y, radius, huePaint)
        canvas.drawCircle(x, y, radius, saturationPaint)
        val radians = Math.toRadians(hsv[0].toDouble())
        val markerX = x + cos(radians).toFloat() * radius * hsv[1]
        val markerY = y + sin(radians).toFloat() * radius * hsv[1]
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 4 * resources.displayMetrics.density
        markerPaint.color = Color.BLACK
        canvas.drawCircle(markerX, markerY, markerRadius, markerPaint)
        markerPaint.strokeWidth = 2 * resources.displayMetrics.density
        markerPaint.color = Color.WHITE
        canvas.drawCircle(markerX, markerY, markerRadius, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || radius <= 0) return false
        val dx = event.x - width / 2f
        val dy = event.y - height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(dx, dy) > radius + markerRadius) return false
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> Unit
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            else -> return false
        }
        hsv[0] = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        hsv[1] = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        invalidate()
        hsv[2] = 1f
        onColorChanged?.invoke(Color.HSVToColor(hsv))
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> hsv[0] = (hsv[0] + 355) % 360
            KeyEvent.KEYCODE_DPAD_RIGHT -> hsv[0] = (hsv[0] + 5) % 360
            KeyEvent.KEYCODE_DPAD_UP -> hsv[1] = (hsv[1] + 0.05f).coerceAtMost(1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> hsv[1] = (hsv[1] - 0.05f).coerceAtLeast(0f)
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        hsv[2] = 1f
        onColorChanged?.invoke(Color.HSVToColor(hsv))
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
