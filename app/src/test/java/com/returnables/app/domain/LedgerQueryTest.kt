package com.returnables.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LedgerQueryTest {
    private val today=LocalDate.of(2026,10,27)
    private fun item(id:String,returnBy:LocalDate?=null,warranty:LocalDate?=null,status:PurchaseStatus=PurchaseStatus.ACTIVE,receipt:String?=null,created:Long=1)=
        Purchase(id,id,"Shop",today.minusMonths(2),returnBy=returnBy,warrantyUntil=warranty,status=status,receiptName=receipt,createdAt=created)

    @Test fun expiredReturnRemainsVisibleEvenWithLiveWarranty() {
        val mixed=item("mixed",today.minusDays(1),today.plusYears(1))
        assertEquals(listOf(mixed),LedgerQuery.select(listOf(mixed),today,filter=LedgerFilter.OVERDUE))
        assertEquals(listOf(mixed),LedgerQuery.select(listOf(mixed),today))
        assertEquals(1,LedgerQuery.overdueCount(listOf(mixed),today))
    }
    @Test fun closedItemsDoNotAppearInDeadlineViews() {
        val closed=item("closed",today.minusDays(1),today.plusDays(1),PurchaseStatus.RETURNED)
        assertTrue(LedgerQuery.select(listOf(closed),today).isEmpty())
        assertTrue(LedgerQuery.select(listOf(closed),today,filter=LedgerFilter.OVERDUE).isEmpty())
        assertEquals(listOf(closed),LedgerQuery.select(listOf(closed),today,filter=LedgerFilter.CLOSED))
    }
    @Test fun receiptAndSearchFiltersComposeIncludingExtractedText() {
        val attached=item("attached",receipt="receipt.jpg").copy(ocrText="Serial AB123")
        val missing=item("missing").copy(notes="Serial AB123")
        assertEquals(listOf(attached),LedgerQuery.select(listOf(attached,missing),today,search=" ab123 ",filter=LedgerFilter.ALL,receiptsOnly=true))
    }
    @Test fun sortOrdersAreStableAndDoNotCompareDifferentCurrencies() {
        val a=item("Alpha",today.plusDays(4),created=2)
        val b=item("beta",today.plusDays(1),created=3).copy(currency="JPY")
        val c=item("charlie",created=1)
        val list=listOf(c,b,a)
        assertEquals(listOf(a,b,c),LedgerQuery.select(list,today,filter=LedgerFilter.ALL,sort=LedgerSort.TITLE))
        assertEquals(listOf(b,a,c),LedgerQuery.select(list,today,filter=LedgerFilter.ALL,sort=LedgerSort.NEWEST))
        assertEquals(listOf(b,a,c),LedgerQuery.select(list,today,filter=LedgerFilter.ALL))
    }
    @Test fun urgentSummaryCountsItemsOnceAndIncludesTodayAndSeventhDay() {
        val two=item("two",today,today.plusDays(7))
        val later=item("later",today.plusDays(8))
        val closed=item("closed",today,status=PurchaseStatus.ARCHIVED)
        assertEquals(1,LedgerQuery.dueSoonCount(listOf(two,later,closed),today))
        assertEquals(listOf(two),LedgerQuery.select(listOf(two,later,closed),today,filter=LedgerFilter.THIS_WEEK))
        assertEquals(0,LedgerQuery.overdueCount(listOf(two,later,closed),today))
    }
}
