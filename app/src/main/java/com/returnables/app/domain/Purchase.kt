package com.returnables.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PurchaseStatus { ACTIVE, RETURNED, CLAIMED, ARCHIVED }

data class Purchase(
    val id: String,
    val title: String,
    val merchant: String,
    val purchasedOn: LocalDate,
    val amountMinor: Long? = null,
    val currency: String = "INR",
    val returnBy: LocalDate? = null,
    val warrantyUntil: LocalDate? = null,
    val notes: String = "",
    val receiptName: String? = null,
    val receiptMime: String? = null,
    val ocrText: String = "",
    val status: PurchaseStatus = PurchaseStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

object PurchaseRules {
    fun validate(p: Purchase, today: LocalDate = LocalDate.now()): List<String> = buildList {
        if (p.id.isBlank() || p.id.length > 128) add("Purchase ID must contain 1–128 characters.")
        if (p.title.isBlank() || p.title.length > 160) add("Title must contain 1–160 characters.")
        if (p.merchant.isBlank() || p.merchant.length > 120) add("Merchant must contain 1–120 characters.")
        if (runCatching { Money.fractionDigits(p.currency) }.isFailure) add("Choose a valid three-letter currency code.")
        if (p.amountMinor != null && p.amountMinor < 0) add("Amount cannot be negative.")
        if (p.purchasedOn > today) add("Purchase date cannot be in the future.")
        if (p.returnBy != null && p.returnBy < p.purchasedOn) add("Return deadline cannot precede purchase date.")
        if (p.warrantyUntil != null && p.warrantyUntil < p.purchasedOn) add("Warranty deadline cannot precede purchase date.")
        if (p.notes.length > 10_000) add("Notes cannot exceed 10,000 characters.")
        if (p.ocrText.length > 100_000) add("Receipt text cannot exceed 100,000 characters.")
        if (p.receiptName != null && (p.receiptName.isBlank() || p.receiptName.length > 255)) add("Receipt name must contain 1–255 characters.")
        if (p.receiptMime != null && p.receiptMime.length > 100) add("Receipt MIME type cannot exceed 100 characters.")
        if (p.createdAt < 0 || p.updatedAt < p.createdAt) add("Purchase timestamps are invalid.")
    }
}

enum class DeadlineKind { RETURN, WARRANTY }
data class Deadline(val kind: DeadlineKind, val date: LocalDate, val daysRemaining: Long)

object DeadlineRules {
    /** Dates are inclusive: a deadline today has zero days remaining, not expired. */
    fun forPurchase(p: Purchase, today: LocalDate): List<Deadline> {
        if (p.status != PurchaseStatus.ACTIVE) return emptyList()
        return listOfNotNull(
            p.returnBy?.let { Deadline(DeadlineKind.RETURN, it, ChronoUnit.DAYS.between(today, it)) },
            p.warrantyUntil?.let { Deadline(DeadlineKind.WARRANTY, it, ChronoUnit.DAYS.between(today, it)) },
        ).sortedWith(compareBy<Deadline> { it.date }.thenBy { it.kind.ordinal })
    }
}
