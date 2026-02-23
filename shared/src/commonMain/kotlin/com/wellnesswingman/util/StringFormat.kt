package com.wellnesswingman.util

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Formats a Double to a string with the specified number of decimal places.
 * KMP-compatible replacement for String.format("%.Nf", value).
 */
fun Double.formatDecimal(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToLong() / factor
    val parts = rounded.toString().split(".")
    val intPart = parts[0]
    val fracPart = if (parts.size > 1) parts[1] else ""
    return if (decimals == 0) {
        intPart
    } else {
        "$intPart.${fracPart.padEnd(decimals, '0').take(decimals)}"
    }
}

/**
 * Zero-pads an integer to the specified length.
 * KMP-compatible replacement for String.format("%0Nd", value).
 */
fun Int.padZero(length: Int): String = this.toString().padStart(length, '0')
