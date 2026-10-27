package com.returnables.app.domain

import java.time.ZoneOffset

data class CalendarDraft(val title: String, val description: String, val beginMillis: Long, val endMillis: Long) {
    companion object {
        /** All-day calendar events use UTC date boundaries, independent of the device timezone. */
        fun from(purchase: Purchase, kind: DeadlineKind): CalendarDraft? {
            val date = when (kind) {
                DeadlineKind.RETURN -> purchase.returnBy
                DeadlineKind.WARRANTY -> purchase.warrantyUntil
            } ?: return null
            if (purchase.status != PurchaseStatus.ACTIVE || date.year !in 1..9999) return null
            val label = if (kind == DeadlineKind.RETURN) "Return deadline" else "Warranty deadline"
            return CalendarDraft(
                "$label: ${purchase.title}",
                "${purchase.merchant}\nDate recorded in returnABLEs. Verify the original terms before acting.\nSaving this event does not request a return or warranty claim.",
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        }
    }
}
