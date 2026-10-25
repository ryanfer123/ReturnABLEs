package com.returnables.app.data

import com.returnables.app.domain.Purchase
import com.returnables.app.domain.PurchaseStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private fun purchase() = Purchase("p1", "旅行 headphones", "Shop", LocalDate.of(2024, 2, 29), amountMinor = 12345,
        returnBy = LocalDate.of(2024, 3, 30), warrantyUntil = LocalDate.of(2025, 2, 28), notes = "Line 1\nLine 2", ocrText = "TOTAL INR 123.45", status = PurchaseStatus.ACTIVE, createdAt = 1, updatedAt = 2)
    private fun manifest(purchases: List<Purchase>, version: Int = 1) = JSONObject().put("schemaVersion", version)
        .put("purchases", JSONArray().apply { purchases.forEach { put(PurchaseJson.encode(it)) } }).toString().toByteArray()
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun name(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) } + ".pdf"
    private fun reject(bytes: ByteArray) {
        val staging = temp.newFolder()
        try { BackupArchive.inspect(ByteArrayInputStream(bytes), staging); fail("Expected invalid backup") }
        catch (_: IllegalArgumentException) { assertEquals(emptyList<File>(), staging.listFiles()!!.toList()) }
    }

    @Test fun jsonRoundtripPreservesDatesMoneyNullsAndUnicode() {
        val p = purchase()
        assertEquals(p, PurchaseJson.decode(JSONObject(PurchaseJson.encode(p).toString())))
        assertEquals(p.copy(amountMinor = Long.MAX_VALUE), PurchaseJson.decode(PurchaseJson.encode(p.copy(amountMinor = Long.MAX_VALUE))))
    }

    @Test fun strictJsonRejectsLossyMoneyDatesStatusAndMissingFields() {
        val original = PurchaseJson.encode(purchase()).toString()
        val cases = listOf(
            JSONObject(original).put("amountMinor", 1.5), JSONObject(original).put("amountMinor", "123"),
            JSONObject(original).put("purchasedOn", "2023-02-29"), JSONObject(original).put("status", "UNKNOWN"),
            JSONObject(original).apply { remove("createdAt") }, JSONObject(original).put("returnBy", "2020-01-01"),
            JSONObject(original).put("receiptName", "../receipt.pdf").put("receiptMime", "application/pdf"),
        )
        cases.forEach { try { PurchaseJson.decode(it); fail("Expected invalid record") } catch (_: IllegalArgumentException) { } }
    }

    @Test fun archiveRoundtripIncludesSharedReceiptOnlyOnce() {
        val bytes = "%PDF-synthetic test receipt".toByteArray()
        val receiptName = name(bytes)
        val source = temp.newFolder()
        File(source, receiptName).writeBytes(bytes)
        val p = purchase().copy(receiptName = receiptName, receiptMime = "application/pdf")
        val records = listOf(p, p.copy(id = "p2", status = PurchaseStatus.RETURNED))
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, records, source)
        val staged = temp.newFolder()
        val result = BackupArchive.inspect(output.toByteArray().inputStream(), staged)
        assertEquals(records, result.purchases)
        assertEquals(1, result.receiptFiles.size)
        assertArrayEquals(bytes, result.receiptFiles.single().readBytes())
        assertEquals(staged.canonicalFile, result.receiptFiles.single().canonicalFile.parentFile)
    }

    @Test fun traversalAbsolutePathsUnexpectedAndDirectoriesAreRejected() {
        for (path in listOf("../escape", "/absolute", "receipts/../escape.pdf", "receipts/sub/file.pdf", "other.txt", "receipts/")) {
            reject(archive("manifest.json" to manifest(emptyList()), path to byteArrayOf(1)))
        }
    }

    @Test fun missingExtraAndTamperedReceiptsAreRejectedAndStagingCleaned() {
        val data = byteArrayOf(1, 2, 3)
        val filename = name(data)
        val p = purchase().copy(receiptName = filename, receiptMime = "application/pdf")
        reject(archive("manifest.json" to manifest(listOf(p))))
        reject(archive("receipts/$filename" to data, "manifest.json" to manifest(emptyList())))
        reject(archive("receipts/$filename" to byteArrayOf(4), "manifest.json" to manifest(listOf(p))))
        reject(archive("receipts/$filename" to data, "manifest.json" to "not json".toByteArray()))
    }

    @Test fun duplicateIdsVersionsMissingAndMalformedManifestAreRejected() {
        reject(archive("manifest.json" to manifest(listOf(purchase(), purchase()))))
        reject(archive("manifest.json" to manifest(emptyList(), 2)))
        reject(archive())
        reject(archive("manifest.json" to "[]".toByteArray()))
        reject(archive("manifest.json" to (String(manifest(emptyList())) + " trailing").toByteArray()))
    }

    @Test fun duplicateEntryNamesAreRejected() {
        val bytes = archive("manifest.json" to manifest(emptyList()), "manifest.jsox" to manifest(emptyList()))
        val from = "manifest.jsox".toByteArray()
        val to = "manifest.json".toByteArray()
        for (i in 0..bytes.size - from.size) if (from.indices.all { bytes[i + it] == from[it] }) to.copyInto(bytes, i)
        reject(bytes)
    }

    @Test fun compressedReceiptBombIsBoundedBeforeHashValidation() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("receipts/${"a".repeat(64)}.pdf"))
            val block = ByteArray(8192)
            repeat((BackupArchive.MAX_RECEIPT_BYTES / block.size).toInt() + 1) { zip.write(block) }
            zip.closeEntry()
        }
        assertTrue(output.size() < 100_000)
        reject(output.toByteArray())
    }

    @Test fun manifestAndRecordCountLimitsAreEnforced() {
        reject(archive("manifest.json" to ByteArray(BackupArchive.MAX_MANIFEST_BYTES.toInt() + 1) { 32 }))
        reject(archive("manifest.json" to manifest((0..1000).map { purchase().copy(id = "p$it") })))
    }

    @Test fun archiveEntryCountIsBounded() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(BackupArchive.MAX_ENTRIES + 1) { index ->
                val data = index.toString().toByteArray()
                zip.putNextEntry(ZipEntry("receipts/${name(data)}")); zip.write(data); zip.closeEntry()
            }
        }
        reject(output.toByteArray())
    }

    @Test fun aggregateInflatedSizeIsBoundedAcrossIndividuallyValidReceipts() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val block = ByteArray(8192)
            repeat(6) { index ->
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(index.toByte())
                repeat(17 * 1024 * 1024 / block.size) { digest.update(block) }
                val filename = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } + ".pdf"
                zip.putNextEntry(ZipEntry("receipts/$filename")); zip.write(index)
                repeat(17 * 1024 * 1024 / block.size) { zip.write(block) }
                zip.closeEntry()
            }
        }
        assertTrue(output.size() < 200_000)
        reject(output.toByteArray())
    }

    @Test fun existingStagingDataIsNeverChanged() {
        val staging = temp.newFolder()
        val existing = File(staging, "keep.txt").apply { writeText("keep") }
        try { BackupArchive.inspect(archive("manifest.json" to manifest(emptyList())).inputStream(), staging); fail("Expected nonempty rejection") }
        catch (_: IllegalArgumentException) { assertEquals("keep", existing.readText()); assertEquals(1, staging.listFiles()!!.size) }
    }

    @Test fun writerRejectsMissingTamperedAndDuplicateSourcesBeforeOutput() {
        val source = temp.newFolder()
        val data = byteArrayOf(1)
        val p = purchase().copy(receiptName = name(data), receiptMime = "application/pdf")
        for (records in listOf(listOf(p), listOf(purchase(), purchase()))) {
            val output = ByteArrayOutputStream()
            try { BackupArchive.write(output, records, source); fail("Expected invalid source") }
            catch (_: IllegalArgumentException) { assertEquals(0, output.size()) }
        }
        File(source, p.receiptName!!).writeBytes(byteArrayOf(2))
        val output = ByteArrayOutputStream()
        try { BackupArchive.write(output, listOf(p), source); fail("Expected hash mismatch") }
        catch (_: IllegalArgumentException) { assertEquals(0, output.size()) }
    }
}
