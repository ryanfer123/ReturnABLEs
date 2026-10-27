package com.returnables.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import org.junit.Before
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.returnables.app.domain.Purchase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Rendered UI evidence using synthetic fixtures only. No fixtures seed the real app. */
class ScreenCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestActivityAwake() {
        compose.activity.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private val actions = ScreenshotActions
    private val today = LocalDate.now()
    private val fixturePurchases = listOf(
        Purchase(id="screenshot-headphones",title="Headphones",merchant="Example shop (test fixture)",
            purchasedOn=today.minusDays(12),amountMinor=129900,returnBy=today.plusDays(2),
            warrantyUntil=today.plusMonths(11),notes="Synthetic screenshot fixture. Confirm the merchant’s original terms before a return.",
            receiptName="synthetic-receipt.jpg",receiptMime="image/jpeg"),
        Purchase(id="screenshot-bag",title="Everyday backpack",merchant="Example goods (test fixture)",
            purchasedOn=today.minusDays(4),amountMinor=249900,returnBy=today.plusDays(10)),
        Purchase(id="screenshot-kettle",title="Electric kettle",merchant="Example home (test fixture)",
            purchasedOn=today.minusMonths(3),warrantyUntil=today.plusMonths(9))
    )

    private fun render(state:ReturnUiState=ReturnUiState(),largeText:Boolean=false,darkTheme:Boolean=false) {
        compose.setContent {
            if(largeText) {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                    ReturnablesApp(state,actions,darkTheme=darkTheme)
                }
            } else ReturnablesApp(state,actions,darkTheme=darkTheme)
        }
    }

    private fun capture(name:String) {
        compose.mainClock.advanceTimeBy(350)
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val directory=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("qa-screenshots")
            ?: error("Screenshot output directory is unavailable.")
        check(directory.isDirectory || directory.mkdirs()) { "Screenshot directory could not be created." }
        val output=File(directory,"$name.png")
        output.outputStream().use { stream -> assertTrue("PNG encoding failed.",bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) }
        assertTrue("Rendered screenshot must contain pixels.",output.length()>0L)
    }

    @Test fun emptyHome() {
        render()
        compose.onNodeWithText("Your first receipt belongs here.").assertExists()
        compose.onNodeWithTag("add-purchase").assertIsDisplayed()
        capture("01-empty-home")
    }

    @Test fun filledDeadlineLedger() {
        render(ReturnUiState(purchases=fixturePurchases))
        compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText("Headphones"))
        compose.onNodeWithText("Headphones").assertIsDisplayed()
        compose.onNodeWithText("2 days\nleft").assertIsDisplayed()
        capture("02-deadline-ledger-synthetic")
    }

    @Test fun purchaseDetails() {
        render(ReturnUiState(purchases=fixturePurchases))
        compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText("Headphones"))
        compose.onNodeWithText("Headphones").performClick()
        compose.onNodeWithText("Dates you confirmed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Original receipt").assertExists()
        capture("03-purchase-details-synthetic")
    }

    @Test fun addPurchaseForm() {
        render()
        compose.onNodeWithTag("add-purchase").performClick()
        compose.onNodeWithText("Take receipt photo").assertIsDisplayed()
        compose.onNodeWithText("Import receipt image or PDF").assertIsDisplayed()
        capture("04-add-purchase")
    }

    @Test fun settingsAndPrivacy() {
        render()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Reminders").assertIsDisplayed()
        capture("05-settings")
        compose.onNodeWithText("Purchases and receipt files stay in this app’s local storage. Receipt text is processed on your device. Sharing or exporting creates a copy outside the app; you choose where it goes.").performScrollTo().assertIsDisplayed()
        capture("06-privacy")
    }

    @Test fun ledgerAtLargerTextSize() {
        render(ReturnUiState(purchases=fixturePurchases),largeText=true)
        compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText("Headphones"))
        compose.onNodeWithText("Headphones").assertIsDisplayed()
        compose.onNodeWithText("2 days\nleft").assertIsDisplayed()
        capture("07-ledger-font-scale-130-synthetic")
    }

    @Test fun darkLedger() {
        render(ReturnUiState(purchases=fixturePurchases),darkTheme=true)
        compose.onNodeWithTag("purchase-ledger").performScrollToNode(hasText("Headphones"))
        compose.onNodeWithText("Headphones").assertIsDisplayed()
        capture("08-dark-ledger-synthetic")
    }

    private object ScreenshotActions:ReturnActions {
        override fun save(purchase:Purchase,onComplete:()->Unit)=onComplete()
        override fun attachReceipt(){}
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
