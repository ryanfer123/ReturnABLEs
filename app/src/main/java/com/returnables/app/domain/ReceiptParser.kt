package com.returnables.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

data class DateCandidate(val date: LocalDate, val sourceLine: String, val ambiguous: Boolean)
data class AmountCandidate(val minor: Long, val currency: String?, val sourceLine: String)
data class ReceiptSuggestions(
    val merchantGuess: String?,
    val dateCandidates: List<DateCandidate>,
    val totalCandidates: List<AmountCandidate>,
)

/** Conservative proposals only. The user confirms every value; return policies are never inferred. */
object ReceiptParser {
    private val yearFirst = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    private val yearLast = Regex("(?<![\\d/.-])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})(?!\\d)")
    private val namedDate = Regex("\\b\\d{1,2}[- ](?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[- ]\\d{4}\\b", RegexOption.IGNORE_CASE)
    private val totalLabel = Regex("\\b(?:total|amount\\s+(?:paid|due)|balance\\s+due|paid)\\b", RegexOption.IGNORE_CASE)
    private val excludedTotal = Regex("\\b(?:sub\\s*total|tax|savings|discount|change|quantity)\\b", RegexOption.IGNORE_CASE)
    private val number = Regex("(?<![\\w.,+−-])(?:\\d{1,2}(?:,\\d{2})*,\\d{3}|\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,4})?(?![\\w.,])")
    private val currencyCode = Regex("\\b(?:INR|USD|EUR|GBP|JPY|CAD|AUD|KWD|BHD|CHF)\\b", RegexOption.IGNORE_CASE)

    fun suggest(text: String): ReceiptSuggestions {
        val lines = text.take(100_000).lineSequence().take(1000).map { it.trim().take(500) }.filter { it.isNotEmpty() }.toList()
        val dates = mutableListOf<DateCandidate>()
        val totals = mutableListOf<AmountCandidate>()
        for (line in lines) {
            yearFirst.findAll(line).forEach { match ->
                date(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())?.let {
                    dates += DateCandidate(it, line, false)
                }
            }
            yearLast.findAll(line).forEach { match ->
                val a = match.groupValues[1].toInt()
                val b = match.groupValues[2].toInt()
                val year = match.groupValues[3].toInt()
                val options = listOfNotNull(date(year, b, a), date(year, a, b)).distinct()
                dates += options.map { DateCandidate(it, line, options.size > 1) }
            }
            namedDate.findAll(line).forEach { match ->
                for (pattern in listOf("d MMM uuuu", "d MMMM uuuu")) {
                    val formatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
                    val parsed = runCatching { LocalDate.parse(match.value.replace('-', ' '), formatter) }.getOrNull()
                    if (parsed != null && parsed.year in 1900..2100) {
                        dates += DateCandidate(parsed, line, false)
                        break
                    }
                }
            }
            if (totalLabel.containsMatchIn(line) && !excludedTotal.containsMatchIn(line)) {
                val currency = currencyCode.find(line)?.value?.uppercase(Locale.ROOT) ?: when {
                    '₹' in line || Regex("\\bRs\\.?", RegexOption.IGNORE_CASE).containsMatchIn(line) -> "INR"
                    '€' in line -> "EUR"
                    '£' in line -> "GBP"
                    '¥' in line -> "JPY"
                    // A bare dollar sign is ambiguous across multiple currencies.
                    else -> null
                }
                val afterLabel = line.substring(totalLabel.find(line)!!.range.last + 1)
                number.findAll(afterLabel).forEach { match ->
                    val value = runCatching { Money.parseMinor(match.value.replace(",", ""), currency ?: "INR") }.getOrNull()
                    if (value != null) totals += AmountCandidate(value, currency, line)
                }
            }
        }
        val merchant = lines.take(8).firstOrNull { line ->
            line.length in 2..120 && line.count { it.isLetter() } >= 2 &&
                !Regex("\\b(receipt|invoice|tax|date|total|thank|phone|tel|gst|gstin|cin|vat|www|http)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                !yearFirst.containsMatchIn(line) && !yearLast.containsMatchIn(line)
        }
        return ReceiptSuggestions(merchant, dates.distinct().take(40), totals.distinct().take(20))
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        if (year !in 1900..2100) null else runCatching { LocalDate.of(year, month, day) }.getOrNull()
}
