package com.returnables.app.data

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.returnables.app.domain.Purchase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Real Android SQLite, ContentResolver and FileProvider checks. Cleanup touches only this run's UUIDs. */
@RunWith(AndroidJUnit4::class)
class DataIntegrationTest {
    private lateinit var context: Context
    private lateinit var store: PurchaseStore
    private val ownedIds = mutableSetOf<String>()
    private val reminderKeys = mutableSetOf<String>()
    private val ownedFiles = mutableListOf<File>()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = PurchaseStore(context)
    }

    @After fun cleanUpOwnFixtures() {
        for (id in ownedIds) store.writableDatabase.delete("purchases", "id=?", arrayOf(id))
        for (key in reminderKeys) store.writableDatabase.delete("reminders", "reminder_key=?", arrayOf(key))
        store.close()
        ownedFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
    }

    private fun purchase(): Purchase {
        val id = "instrumentation-${UUID.randomUUID()}"
        ownedIds += id
        return Purchase(id, "Headphones — café receipt", "Test merchant", LocalDate.of(2024, 2, 29),
            amountMinor = 12345, currency = "INR", returnBy = LocalDate.of(2024, 3, 30),
            notes = "First line\nSecond line", createdAt = 1, updatedAt = 2)
    }

    @Test fun sqliteSurvivesReopenAndRestorePreservesExistingPurchase() {
        val original = purchase()
        store.save(original)
        store.close()
        store = PurchaseStore(context)
        assertEquals(original, store.all().single { it.id == original.id })
        assertEquals(0, store.restore(listOf(original.copy(title = "Stale backup title"))))
        assertEquals(original.title, store.all().single { it.id == original.id }.title)
        val missing = purchase()
        assertEquals(1, store.restore(listOf(missing)))
        assertEquals(missing, store.all().single { it.id == missing.id })
    }

    @Test fun reminderDeduplicationSurvivesDatabaseReopen() {
        val key = "instrumentation-${UUID.randomUUID()}:RETURN:2024-03-30"
        reminderKeys += key
        assertFalse(store.reminderWasSent(key))
        store.markReminderSent(key)
        store.markReminderSent(key)
        store.close()
        store = PurchaseStore(context)
        assertTrue(store.reminderWasSent(key))
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM reminders WHERE reminder_key=?", arrayOf(key)).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test fun receiptContentUriPrivateCopyAndBackupRestoreRoundtrip() {
        val source = File(File(context.cacheDir, "capture").apply { mkdirs() }, "qa-${UUID.randomUUID()}.pdf")
        ownedFiles += source
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawText("Synthetic receipt ${UUID.randomUUID()}", 12f, 30f, android.graphics.Paint())
            document.finishPage(page)
            source.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        val receipts = ReceiptStore(context)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", source)
        val imported = receipts.import(uri)
        val privateFile = receipts.file(imported.name)
        ownedFiles += privateFile
        assertEquals("application/pdf", imported.mime)
        assertNotEquals(source.canonicalPath, privateFile.canonicalPath)
        assertArrayEquals(source.readBytes(), privateFile.readBytes())
        context.contentResolver.openInputStream(receipts.uri(imported.name))!!.use {
            assertArrayEquals(source.readBytes(), it.readBytes())
        }
        val record = purchase().copy(receiptName = imported.name, receiptMime = imported.mime)
        store.save(record)
        val archive = ByteArrayOutputStream().also { BackupArchive.write(it, listOf(record), receipts.directory) }.toByteArray()
        val staging = File(context.cacheDir, "qa-stage-${UUID.randomUUID()}")
        ownedFiles += staging
        val inspected = BackupArchive.inspect(ByteArrayInputStream(archive), staging)
        assertEquals(listOf(record), inspected.purchases)
        assertArrayEquals(source.readBytes(), inspected.receiptFiles.single().readBytes())
        // Simulate an absent record without touching any other application data.
        assertEquals(1, store.writableDatabase.delete("purchases", "id=?", arrayOf(record.id)))
        assertEquals(1, store.restore(inspected.purchases))
        assertEquals(record, store.all().single { it.id == record.id })
    }
}
