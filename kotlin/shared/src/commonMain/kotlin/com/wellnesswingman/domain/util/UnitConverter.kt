package com.wellnesswingman.domain.util

/**
 * Utility functions for unit conversion and formatting.
 */
object UnitConverter {

    fun kgToLbs(kg: Double): Double = kg * 2.20462

    fun lbsToKg(lbs: Double): Double = lbs / 2.20462

    fun cmToInches(cm: Double): Double = cm / 2.54

    fun inchesToCm(inches: Double): Double = inches * 2.54

    fun formatWeight(value: Double, unit: String): String {
        return "%.1f %s".format(value, unit)
    }

    fun formatHeight(value: Double, unit: String): String {
        return if (unit == "in") {
            val totalInches = value.toInt()
            val feet = totalInches / 12
            val inches = totalInches % 12
            "${feet}ft ${inches}in"
        } else {
            "%.0f cm".format(value)
        }
    }
}
