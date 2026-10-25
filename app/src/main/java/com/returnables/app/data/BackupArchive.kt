package com.returnables.app.data

import com.returnables.app.domain.Purchase
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupContents(val purchases: List<Purchase>, val receiptFiles: List<File>)

object BackupArchive {
    const val MAX_RECEIPT_BYTES = 20L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 5L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
    const val MAX_ENTRIES = 2001
    const val MAX_PURCHASES = 1000

    fun write(output: OutputStream, purchases: List<Purchase>, receiptDirectory: File) {
        require(purchases.size <= MAX_PURCHASES) { "Too many purchases." }
        require(purchases.map { it.id }.toSet().size == purchases.size) { "Duplicate purchase IDs." }
        val manifest = JSONObject().put("schemaVersion", 1).put("purchases", JSONArray().apply {
            purchases.forEach { put(PurchaseJson.encode(it)) }
        }).toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST_BYTES) { "Manifest is too large." }
        val receipts = purchases.mapNotNull { it.receiptName }.distinct().map { name ->
            File(receiptDirectory, name).also { file ->
                require(file.isFile && file.canonicalFile.parentFile == receiptDirectory.canonicalFile) { "Missing or unsafe receipt: $name" }
                require(file.length() <= MAX_RECEIPT_BYTES) { "Receipt is too large." }
            }
        }
        require(receipts.sumOf { it.length() } + manifest.size <= MAX_TOTAL_BYTES) { "Backup is too large." }
        // Validate all source files before writing any archive bytes.
        receipts.forEach { file -> file.inputStream().use { copyReceipt(it, null, file.name, MAX_RECEIPT_BYTES) } }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            var total = manifest.size.toLong()
            receipts.forEach { file ->
                zip.putNextEntry(ZipEntry("receipts/${file.name}"))
                total += file.inputStream().use { copyReceipt(it, zip, file.name, minOf(MAX_RECEIPT_BYTES, MAX_TOTAL_BYTES - total)) }
                zip.closeEntry()
            }
        }
    }

    /** Staging must be private and empty. Nothing is committed to the purchase store here. */
    fun inspect(input: InputStream, stagingDirectory: File): BackupContents {
        require((stagingDirectory.exists() || stagingDirectory.mkdirs()) && stagingDirectory.isDirectory) { "Staging directory is unavailable." }
        require(stagingDirectory.listFiles()?.isEmpty() == true) { "Staging directory must be empty." }
        val staged = mutableListOf<File>()
        try {
            var manifest: ByteArray? = null
            val names = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(names.add(entry.name)) { "Duplicate archive entry." }
                    require(names.size <= MAX_ENTRIES) { "Too many archive entries." }
                    require(!entry.isDirectory) { "Directory entries are not supported." }
                    if (entry.name == "manifest.json") {
                        val buffer = java.io.ByteArrayOutputStream()
                        total += copyBounded(zip, buffer, minOf(MAX_MANIFEST_BYTES, MAX_TOTAL_BYTES - total))
                        manifest = buffer.toByteArray()
                    } else {
                        require(entry.name.startsWith("receipts/")) { "Unexpected archive entry." }
                        val name = entry.name.removePrefix("receipts/")
                        require(PurchaseJson.receiptPattern.matches(name)) { "Unsafe receipt filename." }
                        val file = File(stagingDirectory, name)
                        require(file.createNewFile()) { "Receipt staging collision." }
                        staged += file
                        total += file.outputStream().use { copyReceipt(zip, it, name, minOf(MAX_RECEIPT_BYTES, MAX_TOTAL_BYTES - total)) }
                    }
                    zip.closeEntry()
                }
            }
            val bytes = requireNotNull(manifest) { "Missing manifest." }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val tokener = JSONTokener(text)
            val document = tokener.nextValue()
            require(document is JSONObject && tokener.nextClean() == '\u0000') { "Malformed manifest." }
            require(PurchaseJson.integer(document, "schemaVersion") == 1L) { "Unsupported backup version." }
            val records = document.get("purchases")
            require(records is JSONArray && records.length() <= MAX_PURCHASES) { "Invalid purchase collection." }
            val purchases = (0 until records.length()).map { index ->
                val value = records.get(index)
                require(value is JSONObject) { "Invalid purchase record." }
                PurchaseJson.decode(value)
            }
            require(purchases.map { it.id }.toSet().size == purchases.size) { "Duplicate purchase IDs." }
            val references = purchases.mapNotNull { it.receiptName }.toSet()
            require(references == staged.map { it.name }.toSet()) { "Receipt files do not match purchase references." }
            return BackupContents(purchases, staged.toList())
        } catch (e: Exception) {
            staged.forEach { it.delete() }
            throw IllegalArgumentException("Backup could not be validated: ${e.message}", e)
        }
    }

    internal fun copyReceipt(input: InputStream, output: OutputStream?, name: String, limit: Long): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val count = copyBounded(input, output, limit, digest)
        require(count > 0) { "Receipt file is empty." }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == name.substringBefore('.')) { "Receipt fingerprint does not match its filename." }
        return count
    }

    private fun copyBounded(input: InputStream, output: OutputStream?, limit: Long, digest: MessageDigest? = null): Long {
        require(limit >= 0) { "Backup is too large." }
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "Uncompressed archive entry exceeds its size limit." }
            digest?.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        return count
    }
}
