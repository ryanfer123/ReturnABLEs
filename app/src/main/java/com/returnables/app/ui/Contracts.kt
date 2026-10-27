package com.returnables.app.ui

import com.returnables.app.domain.DeadlineKind
import com.returnables.app.domain.Purchase
import com.returnables.app.domain.ReceiptSuggestions

data class ReceiptDraft(val fileName: String, val mimeType: String, val text: String, val suggestions: ReceiptSuggestions)
data class ReturnUiState(
    val purchases: List<Purchase> = emptyList(),
    val receiptDraft: ReceiptDraft? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val notificationsEnabled: Boolean = false,
)
interface ReturnActions {
    fun save(purchase: Purchase, onComplete: () -> Unit)
    fun attachReceipt()
    fun captureReceipt()
    fun clearReceiptDraft()
    fun openReceipt(purchase: Purchase)
    fun shareEvidence(purchase: Purchase)
    fun addToCalendar(purchase: Purchase, kind: DeadlineKind) {}
    fun exportBackup()
    fun restoreBackup()
    fun requestNotifications()
    fun clearMessage()
}
