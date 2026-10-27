package com.returnables.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import org.junit.Before
import com.returnables.app.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReturnablesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestActivityAwake() {
        compose.activity.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private val today = LocalDate.now()
    private fun purchase(id:String="item",title:String="Headphones",status:PurchaseStatus=PurchaseStatus.ACTIVE) = Purchase(
        id=id,title=title,merchant="Example shop",purchasedOn=today.minusDays(3),
        returnBy=today.plusDays(4),status=status,receiptName="original.jpg",receiptMime="image/jpeg")
    private fun scroll(label:String) {
        if(compose.onAllNodesWithTag("purchase-ledger").fetchSemanticsNodes().isNotEmpty()) { compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText(label));compose.onNodeWithText(label).performScrollTo() }
        else compose.onNodeWithText(label).performScrollTo()
    }
    private fun tap(label:String) { scroll(label);compose.onNodeWithText(label).performClick() }
    private fun input(label:String,text:String) { scroll(label);compose.onNodeWithText(label).performTextReplacement(text) }

    @Test fun emptyLedgerAndBlankPurchaseAreActionable() {
        val actions=FakeActions()
        compose.setContent { ReturnablesApp(ReturnUiState(),actions) }
        compose.onNodeWithText("Your first receipt belongs here.").assertExists()
        compose.onNodeWithTag("add-purchase").performClick()
        tap("Save purchase")
        compose.onNodeWithText("Title must contain 1–160 characters.").performScrollTo().assertIsDisplayed()
        assertTrue(actions.saved.isEmpty())
    }

    @Test fun receiptDateNeedsExplicitConfirmationAndInvalidDatesDoNotSave() {
        val state=mutableStateOf(ReturnUiState())
        val actions=FakeActions()
        val extracted=today.minusYears(2)
        actions.attach={state.value=state.value.copy(receiptDraft=ReceiptDraft("scan.jpg","image/jpeg","Example shop\n$extracted",ReceiptSuggestions("Example shop",listOf(DateCandidate(extracted,"Date $extracted",false)),emptyList())))}
        compose.setContent { ReturnablesApp(state.value,actions) }
        compose.onNodeWithTag("add-purchase").performClick()
        tap("Import receipt image or PDF")
        compose.onNodeWithText("Purchase date").performScrollTo().assertTextContains(today.toString())
        tap("Use purchase date $extracted")
        compose.onNodeWithText("Purchase date").performScrollTo().assertTextContains(extracted.toString())
        input("Item title","Headphones");input("Merchant","Example shop")
        input("Return by (optional)","2026-02-30")
        tap("Save purchase")
        compose.onNodeWithText("Return date must be a valid date in YYYY-MM-DD format.").performScrollTo().assertIsDisplayed()
        assertTrue(actions.saved.isEmpty())
    }

    @Test fun changingStatusRequiresConfirmation() {
        val actions=FakeActions()
        compose.setContent { ReturnablesApp(ReturnUiState(purchases=listOf(purchase())),actions) }
        tap("Headphones");tap("Mark returned")
        compose.onNodeWithText("Update this purchase?").assertIsDisplayed()
        assertTrue(actions.saved.isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(actions.saved.isEmpty())
        tap("Mark returned")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(PurchaseStatus.RETURNED,actions.saved.single().status) }
    }

    @Test fun searchAndClosedFilterSelectRelevantPurchases() {
        val actions=FakeActions()
        compose.setContent { ReturnablesApp(ReturnUiState(purchases=listOf(purchase(),purchase("closed","Travel bag",PurchaseStatus.RETURNED))),actions) }
        tap("Closed")
        scroll("Travel bag");compose.onNodeWithText("Travel bag").assertIsDisplayed()
        compose.onNodeWithText("Headphones").assertDoesNotExist()
        input("Search purchases","missing")
        scroll("No purchases in this view.");compose.onNodeWithText("No purchases in this view.").assertIsDisplayed()
        input("Search purchases","")
        tap("All")
        scroll("Headphones");compose.onNodeWithText("Headphones").assertIsDisplayed()
    }

    @Test fun restoringEditedPurchaseRetainsIdentityReceiptAndUnsavedFields() {
        val actions=FakeActions()
        val original=purchase()
        val restoration=StateRestorationTester(compose)
        restoration.setContent { ReturnablesApp(ReturnUiState(purchases=listOf(original)),actions) }
        tap("Headphones");tap("Edit purchase")
        input("Item title","Headphones revised")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Item title").performScrollTo().assertTextContains("Headphones revised")
        tap("Save purchase")
        compose.runOnIdle {
            val saved=actions.saved.single()
            assertEquals(original.id,saved.id)
            assertEquals("Headphones revised",saved.title)
            assertEquals(original.receiptName,saved.receiptName)
            assertEquals(original.createdAt,saved.createdAt)
        }
    }

    @Test fun reusedReceiptSurvivesRecreationWithANewItemIdentity() {
        val actions=FakeActions()
        val original=purchase()
        val restoration=StateRestorationTester(compose)
        restoration.setContent { ReturnablesApp(ReturnUiState(purchases=listOf(original)),actions) }
        tap("Headphones");tap("Add another item from this receipt")
        input("Item title","Spare cable")
        restoration.emulateSavedInstanceStateRestore()
        tap("Save purchase")
        compose.runOnIdle {
            val saved=actions.saved.single()
            assertNotEquals(original.id,saved.id)
            assertEquals("Spare cable",saved.title)
            assertEquals(original.receiptName,saved.receiptName)
            assertEquals(original.merchant,saved.merchant)
            assertEquals(original.purchasedOn,saved.purchasedOn)
            assertNull(saved.returnBy)
        }
    }

    @Test fun headerBackOffersToKeepUnsavedEditing() {
        compose.setContent { ReturnablesApp(ReturnUiState(),FakeActions()) }
        compose.onNodeWithTag("add-purchase").performClick()
        input("Item title","Unsaved item")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard these edits?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Item title").assertTextContains("Unsaved item")
    }

    @Test fun overdueReceiptsAndSortControlsRefineTheLedger() {
        val overdue=purchase("overdue","Past return").copy(returnBy=today.minusDays(1),warrantyUntil=today.plusMonths(4))
        val noReceipt=purchase("without","No receipt").copy(receiptName=null,receiptMime=null)
        compose.setContent { ReturnablesApp(ReturnUiState(purchases=listOf(overdue,noReceipt)),FakeActions()) }
        tap("Overdue")
        scroll("Past return");compose.onNodeWithText("Expired").assertExists()
        tap("All");tap("Receipts only")
        scroll("1 purchase with receipts");compose.onNodeWithText("1 purchase with receipts").assertExists()
        scroll("Sort: Deadline");compose.onNodeWithTag("ledger-sort").performClick()
        compose.onNodeWithText("Title").performClick()
        scroll("Sort: Title");compose.onNodeWithText("Sort: Title").assertExists()
        tap("This week")
        scroll("No purchases in this view.");compose.onNodeWithText("No purchases in this view.").assertExists()
    }

    private class FakeActions:ReturnActions {
        val saved=mutableListOf<Purchase>()
        var attach:()->Unit={}
        override fun save(purchase:Purchase,onComplete:()->Unit){saved+=purchase;onComplete()}
        override fun attachReceipt()=attach()
        override fun captureReceipt(){}
        override fun clearReceiptDraft(){}
        override fun openReceipt(purchase:Purchase){}
        override fun shareEvidence(purchase:Purchase){}
        override fun exportBackup(){}
        override fun restoreBackup(){}
        override fun requestNotifications(){}
        override fun clearMessage(){}
    }
}
