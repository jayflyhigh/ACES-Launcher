package app.olauncher.ui.quickactions

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** FrameLayout whose height always matches its width, used for the rounded-square menu tiles. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
