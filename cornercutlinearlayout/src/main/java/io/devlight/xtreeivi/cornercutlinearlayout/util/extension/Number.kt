package io.devlight.xtreeivi.cornercutlinearlayout.util.extension

val CYCLE_DOUBLE = 0.0..360.0
val CYCLE_FLOAT = 0.0F..360.0F
val CYCLE_INT = 0..360
internal inline fun <reified T : Number> T.normalizeDegree(): T {
    return when (this) {
        is Byte -> {
            if (this in CYCLE_INT) this
            else this.rem(360).let { if (it < 0) it + 360 else it }.toByte() as T
        }

        is Int -> {
            if (this in CYCLE_INT) this
            else this.rem(360).let { if (it < 0) it + 360 else it } as T
        }

        is Long -> {
            if (this in CYCLE_INT) this
            else this.rem(360L).let { if (it < 0L) it + 360L else it } as T
        }

        is Float -> {
            if (this in CYCLE_FLOAT) this
            else this.rem(360.0F).let { if (it < 0.0F) it + 360.0F else it } as T
        }

        is Double -> {
            if (this in CYCLE_DOUBLE) this
            else this.rem(360.0).let { if (it < 0.0) it + 360.0 else it } as T
        }

        else -> TODO("implement normalizeDegree for this type ${this::class.java.simpleName}")
    }
}