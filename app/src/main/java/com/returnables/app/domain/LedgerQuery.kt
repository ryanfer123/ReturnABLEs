package com.returnables.app.domain

import java.time.LocalDate
import java.util.Locale

enum class LedgerFilter { UPCOMING, ALL, CLOSED, OVERDUE, THIS_WEEK }
enum class LedgerSort { DEADLINE, NEWEST, TITLE }

/** Views never discard records; expired return dates remain searchable beside live warranties. */
object LedgerQuery {
    fun select(
        purchases: List<Purchase>, today: LocalDate,
        search: String = "", filter: LedgerFilter = LedgerFilter.UPCOMING,
        sort: LedgerSort = LedgerSort.DEADLINE, receiptsOnly: Boolean = false,
    ): List<Purchase> {
        val query = search.trim()
        val selected = purchases.filter { p ->
            val deadlines = DeadlineRules.forPurchase(p, today)
            (!receiptsOnly || p.receiptName != null) &&
                (query.isBlank() || listOf(p.title, p.merchant, p.notes, p.ocrText).any { it.contains(query, ignoreCase = true) }) &&
                when (filter) {
                    LedgerFilter.UPCOMING -> deadlines.any { it.daysRemaining >= 0 }
                    LedgerFilter.THIS_WEEK -> deadlines.any { it.daysRemaining in 0..7 }
                    LedgerFilter.OVERDUE -> deadlines.any { it.daysRemaining < 0 }
                    LedgerFilter.CLOSED -> p.status != PurchaseStatus.ACTIVE
                    LedgerFilter.ALL -> true
                }
        }
        val byTitle = compareBy<Purchase> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
        return when (sort) {
            LedgerSort.TITLE -> selected.sortedWith(byTitle)
            LedgerSort.NEWEST -> selected.sortedWith(compareByDescending<Purchase> { it.createdAt }.then(byTitle))
            LedgerSort.DEADLINE -> selected.sortedWith(compareBy<Purchase> { p ->
                val dates = DeadlineRules.forPurchase(p, today)
                if (filter == LedgerFilter.OVERDUE) dates.firstOrNull { it.daysRemaining < 0 }?.date ?: LocalDate.MAX
                else dates.firstOrNull { it.daysRemaining >= 0 }?.date ?: dates.firstOrNull()?.date ?: LocalDate.MAX
            }.then(byTitle))
        }
    }

    /** Counts purchases once, even if both deadlines fall within the interval. */
    fun dueSoonCount(purchases: List<Purchase>, today: LocalDate, withinDays: Long = 7): Int =
        purchases.count { p -> DeadlineRules.forPurchase(p, today).any { it.daysRemaining in 0..withinDays } }

    fun overdueCount(purchases: List<Purchase>, today: LocalDate): Int =
        purchases.count { p -> DeadlineRules.forPurchase(p, today).any { it.daysRemaining < 0 } }
}
