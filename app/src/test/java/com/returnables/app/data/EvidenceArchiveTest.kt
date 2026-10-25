package com.returnables.app.data

import com.returnables.app.domain.Purchase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream

class EvidenceArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private fun purchase() = Purchase("id", "../Headphones", "Corner shop", LocalDate.of(2024, 2, 29), amountMinor = 12345,
        returnBy = LocalDate.of(2024, 3, 30), notes = "Audio stopped working", ocrText = "TOTAL INR 123.45", createdAt = 1)
    private fun contents(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()); zip.closeEntry() } }
    }

    @Test fun evidenceIsReadableVersionedAndRetainsReceiptBytes() {
        val bytes = "%PDF-synthetic receipt".toByteArray()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val name = "$fingerprint.pdf"
        val directory = temp.newFolder()
        File(directory, name).writeBytes(bytes)
        val p = purchase().copy(receiptName = name, receiptMime = "application/pdf")
        val output = ByteArrayOutputStream()
        EvidenceArchive.write(output, p, directory)
        val files = contents(output.toByteArray())
        assertEquals(setOf("README.txt", "purchase.json", "receipt-text.txt", "receipt/$name"), files.keys)
        assertArrayEquals(bytes, files.getValue("receipt/$name"))
        assertEquals(p.ocrText, String(files.getValue("receipt-text.txt"), Charsets.UTF_8))
        val readme = String(files.getValue("README.txt"), Charsets.UTF_8)
        for (text in listOf("../Headphones", "Corner shop", "2024-02-29", "2024-03-30", "INR 123.45", "Audio stopped working", fingerprint, "user-confirmed", "not inferred", "Backup separately")) assertTrue(text, readme.contains(text))
        val metadata = JSONObject(String(files.getValue("purchase.json"), Charsets.UTF_8))
        assertEquals(1, metadata.getInt("schemaVersion"))
        assertEquals("returnables-evidence", metadata.getString("format"))
        assertEquals(p, PurchaseJson.decode(metadata.getJSONObject("purchase")))
    }

    @Test fun manualPurchaseWithoutReceiptExportsOnlyReadableAndStructuredDetails() {
        val output = ByteArrayOutputStream()
        EvidenceArchive.write(output, purchase().copy(ocrText = "", amountMinor = null), temp.newFolder())
        val files = contents(output.toByteArray())
        assertEquals(setOf("README.txt", "purchase.json"), files.keys)
        assertTrue(String(files.getValue("README.txt")).contains("Amount: Not recorded"))
    }

    @Test fun missingOrTamperedReceiptIsRejectedBeforeAnyOutput() {
        val directory = temp.newFolder()
        val name = "a".repeat(64) + ".pdf"
        val p = purchase().copy(receiptName = name, receiptMime = "application/pdf")
        repeat(2) { index ->
            if (index == 1) File(directory, name).writeText("wrong fingerprint")
            val output = ByteArrayOutputStream()
            try { EvidenceArchive.write(output, p, directory); fail("Expected receipt validation failure") }
            catch (_: IllegalArgumentException) { assertEquals(0, output.size()) }
        }
    }
}
