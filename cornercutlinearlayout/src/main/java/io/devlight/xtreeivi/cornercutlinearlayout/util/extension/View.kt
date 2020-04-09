package io.devlight.xtreeivi.cornercutlinearlayout.util.extension

import android.view.View
import androidx.core.view.ViewCompat
import java.lang.reflect.Method

internal inline fun <reified T : View> T.doOnNonNullSizeLayout(crossinline action: (view: T) -> Unit) {
    if (ViewCompat.isLaidOut(this) && !isLayoutRequested && this.width > 0 && this.height > 0) {
        action(this)
    } else {
        doOnNextNonNullSizeLayout { action(it) }
    }
}

internal inline fun <reified T : View> T.doOnNextNonNullSizeLayout(crossinline action: (view: T) -> Unit) {
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            view: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            if (view.width == 0 || view.height == 0) return
            view.removeOnLayoutChangeListener(this)
            action(view as T)
        }
    })
}

internal inline val View.isRtl get() = layoutDirection == View.LAYOUT_DIRECTION_RTL
internal inline val View.isLtr get() = !isRtl