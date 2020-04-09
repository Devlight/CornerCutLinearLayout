package io.devlight.xtreeivi.sample.extensions

import android.view.View
import android.view.ViewGroup
import androidx.core.view.get

fun <T: View> ViewGroup.getOrNull(index: Int): T? {
    @Suppress("UNCHECKED_CAST")
    return if (this.childCount > 0 && index in 0 until this.childCount) this[index] as T
    else null
}