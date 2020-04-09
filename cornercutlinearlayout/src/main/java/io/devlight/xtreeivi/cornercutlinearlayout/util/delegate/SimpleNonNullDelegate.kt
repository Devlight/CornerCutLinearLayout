package io.devlight.xtreeivi.cornercutlinearlayout.util.delegate

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal open class SimpleNonNullDelegate<T : Any>(
    initialValue: T,
    private val beforeSetPredicate: T.() -> T? = { this },
    private val afterSetPredicate: T.() -> Unit = { },
    private val getPredicate: (T.() -> T)? = null
    ) : NonNullDelegate<T>(
    initialValue,
    { _, _, newValue -> beforeSetPredicate(newValue) },
    { _, _, newValue -> afterSetPredicate(newValue) },
    { _, value -> getPredicate?.invoke(value) ?: value }
)
