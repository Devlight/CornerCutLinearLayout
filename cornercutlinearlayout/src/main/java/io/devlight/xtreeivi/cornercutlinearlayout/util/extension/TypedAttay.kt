package io.devlight.xtreeivi.cornercutlinearlayout.util.extension

import android.content.res.TypedArray
import androidx.annotation.StyleableRes
import java.lang.IllegalStateException

internal inline fun <reified T> TypedArray.get(@StyleableRes attr: Int, default: T): T {
    return when(default) {
        is Int -> if (hasValue(attr)) (getInt(attr, default) as T) else default
        is Boolean -> if (hasValue(attr)) (getBoolean(attr, default) as T) else default
        else -> default
    }
}

internal fun TypedArray.getDimensionIfHas(@StyleableRes attr: Int): Float? {
    return if (hasValue(attr)) getDimension(attr, 0.0F /*non null stub*/) else null
}