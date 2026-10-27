package com.returnables.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.returnables.app.MainActivity
import com.returnables.app.data.PurchaseStore
import com.returnables.app.domain.DeadlineRules
import com.returnables.app.domain.Purchase
import com.returnables.app.domain.PurchaseStatus
import org.junit.Before
import android.view.WindowManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Real MainActivity -> ViewModel -> SQLite. Cleanup targets this run's unique merchant only. */
class MainActivityFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun keepTestActivityAwake() { compose.activity.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private val token=UUID.randomUUID().toString()
    private val merchant="Synthetic QA $token"
    private val title="Test headphones $token"
    private val today=LocalDate.now()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    private fun scroll(label:String) {
        if(compose.onAllNodesWithTag("purchase-ledger").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performScrollTo()
    }
    private fun input(label:String,value:String) { scroll(label);compose.onNodeWithText(label).performTextReplacement(value) }
    private fun tap(label:String) { scroll(label);compose.onNodeWithText(label).performClick() }
    private fun awaitHome() { compose.waitUntil(timeoutMillis=15_000) { compose.onAllNodesWithText("Search purchases").fetchSemanticsNodes().isNotEmpty() } }
    private fun owned():List<Purchase> = PurchaseStore(context).use { it.all().filter { p->p.merchant==merchant } }
    private fun awaitRecord(condition:(Purchase)->Boolean):Purchase {
        compose.waitUntil(timeoutMillis=15_000) { owned().singleOrNull()?.let(condition)==true }
        return owned().single()
    }

    @After fun cleanUpOnlyThisTestRecordAndReminderKeys() {
        PurchaseStore(context).use { store ->
            for(purchase in store.all().filter { it.merchant==merchant }) {
                val keys=store.readableDatabase.rawQuery("SELECT reminder_key FROM reminders WHERE reminder_key LIKE ?",arrayOf(purchase.id+":%")).use { cursor ->
                    buildList { while(cursor.moveToNext()) add(cursor.getString(0)) }
                }
                keys.forEach { key ->
                    NotificationManagerCompat.from(context).cancel(key.hashCode())
                    store.writableDatabase.delete("reminders","reminder_key=?",arrayOf(key))
                }
                store.writableDatabase.delete("purchases","id=?",arrayOf(purchase.id))
            }
        }
    }

    @Test fun realActivitySavesEditsAndClosesOnlyTheCreatedPurchase() {
        compose.onNodeWithTag("add-purchase").performClick()
        input("Item title",title)
        input("Merchant",merchant)
        input("Purchase date",today.toString())
        input("Return by (optional)",today.plusDays(1).toString())
        tap("Save purchase")
        val original=awaitRecord { it.title==title && it.status==PurchaseStatus.ACTIVE }
        assertEquals(today.plusDays(1),original.returnBy)
        assertTrue(DeadlineRules.forPurchase(original,today).isNotEmpty())

        // Reopen the database independently: the UI callback alone is not persistence evidence.
        PurchaseStore(context).use { reopened -> assertEquals(original,reopened.all().single{it.id==original.id}) }
        awaitHome()
        input("Search purchases",token)
        tap(title)
        tap("Edit purchase")
        val revised="$title revised"
        input("Item title",revised)
        tap("Save purchase")
        val edited=awaitRecord { it.title==revised }
        assertEquals(original.id,edited.id)

        awaitHome()
        input("Search purchases",token)
        tap(revised)
        tap("Mark returned")
        compose.onNodeWithText("Update this purchase?").assertIsDisplayed()
        assertEquals(PurchaseStatus.ACTIVE,owned().single().status)
        compose.onNodeWithText("Confirm").performClick()
        val returned=awaitRecord { it.status==PurchaseStatus.RETURNED }
        assertEquals(original.id,returned.id)
        assertTrue("Closed records must produce no reminder deadlines.",DeadlineRules.forPurchase(returned,today).isEmpty())
    }
}
