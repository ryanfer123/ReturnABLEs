package com.returnables.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    private val today = LocalDate.of(2024, 2, 29)
    private fun purchase() = Purchase("id-1", "Headphones", "Corner store", LocalDate.of(2024, 2, 28), returnBy = today, warrantyUntil = today.plusYears(1), createdAt = 100)

    @Test fun inclusiveLeapDayAndExpiredDeadlines() {
        val deadlines = DeadlineRules.forPurchase(purchase(), today)
        assertEquals(listOf(0L, 365L), deadlines.map { it.daysRemaining })
        assertEquals(DeadlineKind.RETURN, deadlines.first().kind)
        assertEquals(-1L, DeadlineRules.forPurchase(purchase(), today.plusDays(1)).first().daysRemaining)
        assertEquals(LocalDate.of(2025, 2, 28), deadlines.last().date)
    }

    @Test fun closedPurchasesHaveNoActionableDeadlines() {
        for (status in listOf(PurchaseStatus.RETURNED, PurchaseStatus.CLAIMED, PurchaseStatus.ARCHIVED)) {
            assertTrue(DeadlineRules.forPurchase(purchase().copy(status = status), today).isEmpty())
        }
        assertTrue(DeadlineRules.forPurchase(purchase().copy(returnBy = null, warrantyUntil = null), today).isEmpty())
    }

    @Test fun purchaseValidationUsesInjectedToday() {
        assertTrue(PurchaseRules.validate(purchase(), today).isEmpty())
        val invalid = purchase().copy(title = " ", merchant = "M".repeat(121), currency = "inr", amountMinor = -1, returnBy = today.minusDays(2), warrantyUntil = today.minusDays(2), purchasedOn = today.plusDays(1), updatedAt = 99)
        assertEquals(8, PurchaseRules.validate(invalid, today).size)
        assertTrue(PurchaseRules.validate(purchase().copy(returnBy = purchase().purchasedOn), today).isEmpty())
    }

    @Test fun currencyMinorUnitsAreExact() {
        assertEquals(1001L, Money.parseMinor("10.01", "INR"))
        assertEquals(0L, Money.parseMinor("0", "USD"))
        assertEquals(321L, Money.parseMinor("321", "JPY"))
        assertEquals(1001L, Money.parseMinor("1.001", "KWD"))
        assertEquals("10.01", Money.format(1001, "INR"))
        assertEquals("321", Money.format(321, "JPY"))
        assertEquals(Long.MAX_VALUE, Money.parseMinor("92233720368547758.07", "INR"))
    }

    @Test fun amountsNeverRoundOrAcceptAmbiguousInput() {
        for ((text, currency) in listOf("1.001" to "INR", "1.00" to "JPY", "-1" to "INR", "1,000" to "INR", "1e2" to "INR", "NaN" to "INR", "1." to "INR", "1" to "ZZZ", "92233720368547758.08" to "INR")) {
            expectInvalid { Money.parseMinor(text, currency) }
        }
    }

    @Test fun ambiguousDatesKeepBothPossibilitiesAndOriginalLine() {
        val line = "Purchase date 03/04/2024"
        val result = ReceiptParser.suggest("Corner shop\n$line\nTOTAL INR 10.25")
        assertEquals("Corner shop", result.merchantGuess)
        assertEquals(setOf(LocalDate.of(2024, 4, 3), LocalDate.of(2024, 3, 4)), result.dateCandidates.map { it.date }.toSet())
        assertTrue(result.dateCandidates.all { it.ambiguous && it.sourceLine == line })
        assertEquals(1025L, result.totalCandidates.single().minor)
        assertEquals("INR", result.totalCandidates.single().currency)
    }

    @Test fun invalidDatesAreRejectedWithoutYearOrFutureGuessing() {
        val result = ReceiptParser.suggest("DATE 29/02/2023\nDATE 2024-02-29\nDATE 31/04/2024\nDATE 03/04/24\nDATE 11/02\nDATE 29 Feb 2024\nDATE 2026-10-25")
        assertEquals(setOf(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 10, 25)), result.dateCandidates.map { it.date }.toSet())
        assertTrue(result.dateCandidates.none { it.ambiguous })
    }

    @Test fun totalsPreserveCurrencyUncertaintyAndIgnoreSubtotals() {
        val result = ReceiptParser.suggest("SUBTOTAL INR 500.00\nTAX INR 50.00\nTOTAL INR 1,250.50\nPAID JPY 1250\nTOTAL $ 9.25\nTOTAL INR -19.00")
        assertEquals(listOf(125050L, 1250L, 925L), result.totalCandidates.map { it.minor })
        assertEquals(listOf("INR", "JPY", null), result.totalCandidates.map { it.currency })
    }

    @Test fun indianRupeeGroupingIsRecognizedWithoutGuessingMalformedGrouping() {
        val result = ReceiptParser.suggest("TAX INVOICE\nGSTIN 123456\nCorner shop\nGrand total Rs. 1,23,456.78\nTOTAL ₹12,345.67\nTOTAL INR 12,34.56\nTOTAL INR −19.00")
        assertEquals("Corner shop", result.merchantGuess)
        assertEquals(listOf(12345678L, 1234567L), result.totalCandidates.map { it.minor })
        assertTrue(result.totalCandidates.all { it.currency == "INR" })
    }

    @Test fun noPolicyOrDateIsInventedFromEmptyOrIncompleteReceipt() {
        assertEquals(ReceiptSuggestions(null, emptyList(), emptyList()), ReceiptParser.suggest(""))
        val result = ReceiptParser.suggest("Store\nReturn within 30 days\n2024-13-35\nTOTAL INR 3.14159")
        assertTrue(result.dateCandidates.isEmpty())
        assertTrue(result.totalCandidates.isEmpty())
    }

    private fun expectInvalid(action: () -> Unit) {
        try { action(); fail("Expected invalid input to be rejected") } catch (_: IllegalArgumentException) { }
    }
}
