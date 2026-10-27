package com.returnables.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class CalendarDraftTest {
    private val purchase=Purchase("id","Headphones","Shop",LocalDate.of(2026,1,1),returnBy=LocalDate.of(2026,11,1))
    @Test fun allDayDraftHasUtcBoundariesAndOnlyNecessaryDetails() {
        val p=purchase.copy(notes="Private note",ocrText="Card number")
        val draft=CalendarDraft.from(p,DeadlineKind.RETURN)!!
        assertEquals(Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),draft.beginMillis)
        assertEquals(86_400_000,draft.endMillis-draft.beginMillis)
        assertTrue(draft.title.contains("Headphones"))
        assertFalse(draft.description.contains("Private note"))
        assertFalse(draft.description.contains("Card number"))
    }
    @Test fun extremeImportedDatesAreRejectedWithoutOverflow() {
        assertNull(CalendarDraft.from(purchase.copy(returnBy=LocalDate.MAX),DeadlineKind.RETURN))
        assertNull(CalendarDraft.from(purchase.copy(returnBy=LocalDate.MIN),DeadlineKind.RETURN))
    }
    @Test fun missingOrClosedDeadlinesDoNotProduceEvents() {
        assertNull(CalendarDraft.from(purchase,DeadlineKind.WARRANTY))
        assertNull(CalendarDraft.from(purchase.copy(status=PurchaseStatus.RETURNED),DeadlineKind.RETURN))
    }
}
