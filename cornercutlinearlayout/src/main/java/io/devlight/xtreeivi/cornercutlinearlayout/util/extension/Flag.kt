package io.devlight.xtreeivi.cornercutlinearlayout.util.extension

internal infix fun Int.containsFlag(flag: Int) = this or flag == this

internal fun Int.containsAnyFlag(vararg flag: Int) = flag.any { this or it == this }
internal fun Int.containsAllFlag(vararg flag: Int) = flag.all { this or it == this }

infix fun Int.addFlag(flag: Int) = this.or(flag)

infix fun Int.toggleFlag(flag: Int) = this.xor(flag)

infix fun Int.removeFlag(flag: Int) = this.and(flag.inv())

fun combineFlags(vararg flags: Int) = flags.fold(0) { composedFlag, flag -> composedFlag addFlag flag }