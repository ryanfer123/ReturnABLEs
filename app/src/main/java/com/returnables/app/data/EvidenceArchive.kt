package com.returnables.app.data

import com.returnables.app.domain.Money
import com.returnables.app.domain.Purchase
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Human-readable purchase evidence. This is distinct from the restorable backup format. */
object EvidenceArchive {
    fun write(output: OutputStream, purchase: Purchase, receiptDirectory: File) {
        val record = PurchaseJson.encode(purchase) // Validate before writing archive bytes.
        val receipt = purchase.receiptName?.let { name ->
            File(receiptDirectory, name).also { file ->
                require(file.isFile && file.canonicalFile.parentFile == receiptDirectory.canonicalFile) { "Missing or unsafe receipt." }
                file.inputStream().use { BackupArchive.copyReceipt(it, null, name, BackupArchive.MAX_RECEIPT_BYTES) }
            }
        }
        val metadata = JSONObject().put("schemaVersion", 1).put("format", "returnables-evidence").put("purchase", record)
        val readme = buildString {
            appendLine("returnABLEs — Purchase evidence")
            appendLine("=============================")
            appendLine("Purchase: ${purchase.title}")
            appendLine("Merchant: ${purchase.merchant}")
            appendLine("Purchased on: ${purchase.purchasedOn}")
            appendLine("Amount: ${purchase.amountMinor?.let { "${purchase.currency} ${Money.format(it, purchase.currency)}" } ?: "Not recorded"}")
            appendLine("Return deadline: ${purchase.returnBy ?: "Not recorded"}")
            appendLine("Warranty deadline: ${purchase.warrantyUntil ?: "Not recorded"}")
            appendLine("Status: ${purchase.status.name}")
            appendLine("Purchase ID: ${purchase.id}")
            appendLine()
            appendLine("Notes")
            appendLine(purchase.notes.ifBlank { "No notes recorded." })
            appendLine()
            appendLine("Files")
            appendLine("purchase.json: versioned structured purchase metadata.")
            if (receipt != null) {
                appendLine("receipt/${receipt.name}: original attached receipt bytes.")
                appendLine("Receipt SHA-256: ${receipt.name.substringBefore('.')}")
            } else appendLine("No receipt was attached to this purchase.")
            if (purchase.ocrText.isNotBlank()) appendLine("receipt-text.txt: machine-extracted receipt text; verify against the original.")
            appendLine()
            appendLine("Limits and context")
            appendLine("These are user-confirmed purchase details, not independently verified facts.")
            appendLine("Deadlines are entered by the user. They are not inferred merchant policies or legal advice.")
            appendLine("OCR text may contain errors. This folder does not prove a claim is eligible or accepted.")
            appendLine("This evidence export is for review and sharing; use Backup separately to restore app data.")
        }
        ZipOutputStream(output).use { zip ->
            fun text(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            text("README.txt", readme)
            text("purchase.json", metadata.toString(2))
            if (purchase.ocrText.isNotBlank()) text("receipt-text.txt", purchase.ocrText)
            if (receipt != null) {
                zip.putNextEntry(ZipEntry("receipt/${receipt.name}"))
                receipt.inputStream().use { BackupArchive.copyReceipt(it, zip, receipt.name, BackupArchive.MAX_RECEIPT_BYTES) }
                zip.closeEntry()
            }
        }
    }
}
