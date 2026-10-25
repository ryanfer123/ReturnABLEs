package com.returnables.app.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

class ReceiptStore(private val context: Context) {
    val directory = File(context.filesDir, "receipts").apply { mkdirs() }
    data class Stored(val name: String, val mime: String)
    fun import(uri: Uri): Stored {
        val temporary = File.createTempFile("receipt-", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                    require(total <= 20L * 1024 * 1024) { "Receipt exceeds 20 MiB. Choose a smaller image or PDF." }; output.write(buffer, 0, count) }
                require(total > 0) { "This receipt file is empty." }
            } } ?: error("Receipt could not be opened. Choose the file again.")
            val head = temporary.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val format = when {
                head.take(5).toByteArray().contentEquals("%PDF-".toByteArray()) -> "pdf" to "application/pdf"
                head[0] == 0xff.toByte() && head[1] == 0xd8.toByte() -> "jpg" to "image/jpeg"
                head.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "png" to "image/png"
                String(head,0,4,Charsets.US_ASCII) == "RIFF" && String(head,8,4,Charsets.US_ASCII) == "WEBP" -> "webp" to "image/webp"
                else -> error("Choose a JPEG, PNG, WebP image, or PDF receipt.")
            }
            if (format.first != "pdf") {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, options)
                require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong()*options.outHeight <= 80_000_000) { "Receipt image cannot be decoded or is too large." }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().use { input -> val buffer=ByteArray(8192); while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
            val name=digest.digest().joinToString("") { "%02x".format(it) }+"."+format.first
            val target=File(directory,name)
            if (!target.exists()) check(temporary.renameTo(target)) { "Receipt could not be stored. Try attaching it again." }
            return Stored(name,format.second)
        } finally { temporary.delete() }
    }
    fun file(name: String): File {
        require(Regex("[0-9a-f]{64}\\.(jpg|png|webp|pdf)").matches(name)) { "Invalid receipt reference." }
        return File(directory,name).also { require(it.isFile) { "Receipt is missing. Restore its backup or attach it again." } }
    }
    fun uri(name: String): Uri = FileProvider.getUriForFile(context, context.packageName+".files", file(name))
}
