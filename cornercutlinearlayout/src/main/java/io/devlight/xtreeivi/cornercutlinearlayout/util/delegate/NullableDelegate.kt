package io.devlight.xtreeivi.cornercutlinearlayout.util.delegate

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal open class NullableDelegate<T>(
    initialValue: T,
    private val beforeSetPredicate: (property: KProperty<*>, oldValue: T, newValue: T) -> T = { _, _, newValue -> newValue },
    private val afterSetPredicate: (property: KProperty<*>, oldValue: T, newValue: T) -> Unit = { _, _, _ -> },
    private val getPredicate: ((property: KProperty<*>, value: T) -> T)? = null
    ) : ReadWriteProperty<Any?, T> {
        private var value: T = initialValue

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return getPredicate?.invoke(property, value) ?: value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            val modifiedValue = beforeSetPredicate(property, this.value, value)
            if (this.value == modifiedValue) return
            val oldValue = this.value
            this.value = modifiedValue
            afterSetPredicate(property, oldValue, this.value)
        }
}