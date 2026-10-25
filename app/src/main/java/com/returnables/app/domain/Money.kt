package com.returnables.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

object Money {
    internal fun fractionDigits(currency: String): Int {
        require(Regex("[A-Z]{3}").matches(currency)) { "Use an uppercase three-letter currency code." }
        val digits = Currency.getInstance(currency).defaultFractionDigits
        require(digits in 0..4) { "Currency does not define supported minor units." }
        return digits
    }

    /** Plain decimal input only; no floating point, rounding, grouping or guessed locale. */
    fun parseMinor(text: String, currency: String): Long {
        val digits = fractionDigits(currency)
        val input = text.trim()
        require(input.length in 1..64 && Regex("[0-9]+(?:\\.[0-9]+)?").matches(input)) {
            "Enter a nonnegative decimal amount without grouping or currency symbols."
        }
        require(input.substringAfter('.', "").length <= digits) { "Amount has too many decimal places for $currency." }
        return try {
            BigDecimal(input).movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Amount is too large.")
        }
    }

    /** Stable, locale-independent representation suitable for review and editing. */
    fun format(minor: Long, currency: String): String {
        require(minor >= 0) { "Amount cannot be negative." }
        return BigDecimal.valueOf(minor, fractionDigits(currency)).toPlainString()
    }
}
