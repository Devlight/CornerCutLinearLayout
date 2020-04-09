package io.devlight.xtreeivi.sample.extensions

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams

val View.horizontalPadding: Int get() = this.paddingStart + this.paddingEnd
val View.verticalPadding: Int get() = this.paddingTop + this.paddingBottom

inline fun <reified T : View> T.doOnNextNonNullSizeLayout(crossinline action: (view: T) -> Unit) {
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

inline fun <reified T : View> T.doOnNonNullSizeLayout(crossinline action: (view: T) -> Unit) {
    if (ViewCompat.isLaidOut(this) && !isLayoutRequested && this.width > 0 && this.height > 0) {
        action(this)
    } else {
        doOnNextNonNullSizeLayout { action(it) }
    }
}

fun View.duplicateViewSizeContinuously(
    view: View,
    duplicateWidth: Boolean = true,
    duplicateHeight: Boolean = true,
    overrideMinimumWidth: Boolean = true,
    overrideMinimumHeight: Boolean = true,
    ignoreNullOrInvisibleValues: Boolean = true,
    fallbackLayoutParamWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    fallbackLayoutParamHeight: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    transformWidth: Int.() -> Int =  { this },
    transformHeight: Int.() -> Int = { this }
) {
    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val shouldFallbackToWidth = ignoreNullOrInvisibleValues && (!view.isVisible || view.width == 0)
        val shouldFallbackToHeight = ignoreNullOrInvisibleValues && (!view.isVisible || view.height == 0)
        updateLayoutParams<ViewGroup.LayoutParams> {
            if (duplicateWidth) width = when {
                shouldFallbackToWidth -> fallbackLayoutParamWidth
                overrideMinimumWidth -> transformWidth(view.width)
                else -> transformWidth(view.width.coerceAtLeast(minimumWidth))
            }
            if (duplicateHeight) height = when {
                shouldFallbackToHeight -> fallbackLayoutParamHeight
                overrideMinimumHeight -> transformHeight(view.height)
                else -> transformHeight(view.height.coerceAtLeast(minimumHeight))
            }
        }
    }
}
